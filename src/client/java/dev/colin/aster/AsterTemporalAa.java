package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import java.nio.file.Files;
import java.util.*;

/** World-only temporal reconstruction, before exposure, bloom, hand and HUD. */
public final class AsterTemporalAa implements AutoCloseable {
    private final TextureTarget[] history = new TextureTarget[2];
    private final Matrix4f current = new Matrix4f(), previous = new Matrix4f();
    private final Map<String,Integer> resetEvents = new LinkedHashMap<>();
    private final Set<String> pendingReasons = new LinkedHashSet<>();
    private List<String> lastReasons = List.of();
    private final Map<String,Object> probes = new LinkedHashMap<>();
    private GpuBufferSlice temporal;
    private TextureTarget lastInput, lastMotion;
    private RenderTarget lastDepth;
    private Vec3 previousCamera;
    private Object dimension, atlas, cameraType;
    private float previousFovX, previousFovY, previousWind = 12, previousRain;
    private float jitterX, jitterY, previousJitterX, previousJitterY;
    private int index, frame, resets;
    private boolean reset = true, underwater;
    private boolean enabled = !System.getProperty("aster.temporalAa", "true").equalsIgnoreCase("false");

    public boolean enabled() { return enabled && AsterRenderer.INSTANCE.effectsEnabled(); }
    public void setVerificationEnabled(boolean value) {
        if (!AutomatedVerification.HIDDEN) return;
        if (enabled != value) { enabled = value; resetHistory("taa-toggle"); }
    }
    public float previousWindTime() { return previousWind; }
    public float previousRain() { return previousRain; }
    public void resetHistory(String reason) { reset = true; pendingReasons.add(reason); }

    /** Left multiplication translates clip coordinates after the native bob/portal projection. */
    public Matrix4f jitter(Matrix4f projection, int width, int height) {
        jitterX = jitterY = 0;
        if (!enabled() || width < 1 || height < 1) return projection;
        // The preserved wind geometry checker compares raw direct/indirect
        // coverage. Hold its raster sample constant so jitter is not mistaken
        // for changed vegetation. The dedicated TAA suite uses all eight phases.
        int sample = AutomatedVerification.HIDDEN && System.getProperty("aster.verificationProfile","").equals("wind")
            ? 1 : frame % 8 + 1;
        jitterX = (halton(sample, 2) - .5f) / width;
        jitterY = (halton(sample, 3) - .5f) / height;
        return new Matrix4f().translation(2*jitterX, 2*jitterY, 0).mul(projection);
    }
    private static float halton(int i, int base) {
        float fraction = 1, result = 0;
        while (i > 0) { fraction /= base; result += fraction * (i % base); i /= base; }
        return result;
    }
    public void begin(int width, int height, CameraRenderState camera, Object level, Object resourceAtlas) {
        if (!enabled()) { resetHistory("taa-inactive"); return; }
        if (history[0] == null || history[0].width != width || history[0].height != height) {
            releaseTargets();
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int i=0;i<2;i++) {
                history[i] = new TextureTarget("Aster scene TAA history "+i,width,height,GpuFormat.RGBA16_FLOAT,null);
                encoder.clearColorTexture(history[i].getColorTexture(),new Vector4f(0));
            }
            resetHistory("resize");
        }
        if (dimension != level) resetHistory("dimension");
        if (atlas != resourceAtlas) resetHistory("resource-reload");
        if (underwater != camera.fogType.name().equals("WATER")) resetHistory("underwater-transition");
        var type = Minecraft.getInstance().options.getCameraType();
        if (cameraType != type) resetHistory("camera-mode");
        float fovX = camera.projectionMatrix.m00(), fovY = camera.projectionMatrix.m11();
        if (Math.abs(fovX-previousFovX) > Math.abs(fovX)*.02f || Math.abs(fovY-previousFovY) > Math.abs(fovY)*.02f)
            resetHistory("projection-change");
        current.set(WorldCameraTransforms.projection(camera)).mul(camera.viewRotationMatrix);
        var delta = previousCamera == null ? Vec3.ZERO : camera.pos.subtract(previousCamera);
        if (previousCamera == null || delta.lengthSqr() > 16) resetHistory("camera-cut");
        // A sudden turn exposes a largely different image. Ordinary mouse turns reproject normally.
        var forward = new Matrix4f(current).invert().transformProject(new org.joml.Vector3f(0,0,.01f)).normalize();
        var oldForward = new Matrix4f(previous).invert().transformProject(new org.joml.Vector3f(0,0,.01f)).normalize();
        if (forward.dot(oldForward) < .7f) resetHistory("camera-turn");
        lastReasons = List.copyOf(pendingReasons);
        if (reset) { resets++; for (String reason : pendingReasons) resetEvents.merge(reason,1,Integer::sum); }
        pendingReasons.clear();
        try (var stack = MemoryStack.stackPush()) {
            var bytes = stack.malloc(176);
            new Matrix4f(current).invert().get(0,bytes);
            previous.get(64,bytes);
            bytes.position(128);
            bytes.putFloat((float)delta.x()).putFloat((float)delta.y()).putFloat((float)delta.z()).putFloat(reset ? 1 : 0);
            bytes.putFloat(width).putFloat(height).putFloat(frame).putFloat(0);
            bytes.putFloat(jitterX).putFloat(jitterY).putFloat(previousJitterX).putFloat(previousJitterY);
            bytes.flip();
            var device = RenderSystem.getDevice();
            temporal = device.createCommandEncoder().transientMemory().uploadGpu(bytes,device.getDeviceInfo().limits().minUniformOffsetAlignment(),GpuBuffer.USAGE_UNIFORM);
        }
        previousCamera = camera.pos; dimension = level; atlas = resourceAtlas; cameraType = type;
        underwater = camera.fogType.name().equals("WATER"); previousFovX = fovX; previousFovY = fovY;
    }
    private void bind(RenderPass pass, TextureTarget input, RenderTarget depth, TextureTarget motion) {
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        pass.setUniform("SceneTemporal",temporal);
        pass.setUniform("TaaInput",input.getColorTextureView(),linear);
        pass.setUniform("TaaDepth",depth.getDepthTextureView(),nearest);
        pass.setUniform("TaaMotion",motion.getColorTextureView(),nearest);
        pass.setUniform("TaaHistory",history[index].getColorTextureView(),linear);
        AsterRenderer.INSTANCE.bindTemporalInputs(pass);
    }
    public TextureTarget resolve(TextureTarget input, RenderTarget depth, TextureTarget motion, float windTime, float rain) {
        if (!enabled()) return input;
        lastInput=input; lastDepth=depth; lastMotion=motion;
        int next=1-index;
        try (var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster scene temporal reconstruction",history[next].getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.TAA));
            bind(pass,input,depth,motion);
            pass.draw(3,1,0,0);
        }
        // Keep the source index for diagnostic replays of this exact frame.
        previous.set(current); previousJitterX=jitterX; previousJitterY=jitterY;
        previousWind=windTime; previousRain=rain;
        reset=false; frame++;
        index=next;
        return history[index];
    }
    public Object evidence() {
        var result = new LinkedHashMap<String,Object>();
        result.put("enabled",enabled()); result.put("resolvedFrames",frame); result.put("historyResets",resets);
        result.put("resetEvents",resetEvents); result.put("lastResetReasons",lastReasons);
        result.put("jitterPixels",List.of(jitterX*(history[0]==null?0:history[0].width),jitterY*(history[0]==null?0:history[0].height)));
        result.put("format","RGBA16_FLOAT, RGB scene radiance plus previous surface distance in alpha");
        result.put("placement","scene HDR before bloom/exposure; hand and HUD after resolve");
        result.put("foliageMotion","previous/current native wind evaluated per vertex in the existing material replay MRT");
        result.put("untrackedMotion","water, entities and transparency use reduced history plus color/depth rejection");
        result.put("probes",probes);
        if(history[0]!=null) result.put("historyBytes",(long)history[0].width*history[0].height*16);
        return result;
    }
    public void captureProbe(String label) {
        if (!AutomatedVerification.HIDDEN || !enabled() || lastInput==null) return;
        var target=new TextureTarget("Aster TAA rejection diagnostic",lastInput.width,lastInput.height,GpuFormat.RGBA8_UNORM,null);
        // Resolve's old history is the other ping-pong target. Re-run only diagnostics.
        index=1-index;
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster TAA history diagnostic",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.TAA_DEBUG));
            bind(pass,lastInput,lastDepth,lastMotion);
            pass.draw(3,1,0,0);
        } finally { index=1-index; }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int accepted=0,rejected=0,reactive=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int p=image.getPixel(x,y);
                    if(ARGB.green(p)>127) accepted++;
                    if(ARGB.red(p)>127) rejected++;
                    if(ARGB.blue(p)>127) reactive++;
                }
                probes.put(label,Map.of("acceptedPixels",accepted,"rejectedPixels",rejected,"reactivePixels",reactive,"pixels",image.getWidth()*image.getHeight()));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-taa-history.png"));
            } catch(Exception ex) { throw new RuntimeException(ex); }
            finally { image.close();target.destroyBuffers(); }
        });
    }
    public void captureMotionProbe(String label) {
        if(!AutomatedVerification.HIDDEN || !enabled() || lastMotion==null) return;
        var target=new TextureTarget("Aster native wind velocity diagnostic",lastMotion.width,lastMotion.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster native wind velocity diagnostic",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.TAA_MOTION_DEBUG));
            bind(pass,lastInput,lastDepth,lastMotion);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int foliage=0,moving=0,maxMotion=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int pixel=image.getPixel(x,y);
                    if(ARGB.green(pixel)>127) {foliage++;maxMotion=Math.max(maxMotion,ARGB.red(pixel));if(ARGB.red(pixel)>1)moving++;}
                }
                var result=Map.of("foliagePixels",foliage,"movingFoliagePixels",moving,"maxEncodedMotion",maxMotion,
                    "encoding","red = min(world-space motion length * 1024, 1); green = replayed foliage");
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                Files.writeString(folder.resolve(label+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
                image.writeToFile(folder.resolve(label+".png"));
            } catch(Exception ex) {throw new RuntimeException(ex);}
            finally {image.close();target.destroyBuffers();}
        });
    }
    private void releaseTargets() { for(int i=0;i<2;i++) if(history[i]!=null) { history[i].destroyBuffers();history[i]=null; } }
    @Override public void close() { releaseTargets();previousCamera=null;atlas=null;dimension=null;resetHistory("renderer-close"); }
}
