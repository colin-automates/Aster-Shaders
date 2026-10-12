package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import java.nio.file.Files;
import java.util.*;

/** Bounded native SSR for opaque terrain, independently of the water replay. */
public final class AsterSolidReflections implements AutoCloseable {
    private TextureTarget geometry,response,raw,output;
    private final TextureTarget[] history=new TextureTarget[2],historyGeometry=new TextureTarget[2],mips=new TextureTarget[4];
    private final Matrix4f current=new Matrix4f(),previous=new Matrix4f();
    private final Map<String,Integer> resetEvents=new LinkedHashMap<>();
    private final Map<String,Object> probes=new LinkedHashMap<>();
    private final Set<String> pending=new LinkedHashSet<>();
    private List<String> lastReasons=List.of();
    private Vec3 camera;
    private Object dimension,atlas,cameraType;
    private float fovX,fovY;
    private int index,frames,skippedFrames;
    private boolean reset=true,submerged;
    private boolean enabled=!System.getProperty("aster.solidReflections","true").equalsIgnoreCase("false");
    private GpuBufferSlice temporal;
    private TextureTarget lastScene,lastMotion;
    private RenderTarget lastDepth;

    public boolean enabled() {return enabled && AsterRenderer.INSTANCE.effectsEnabled() && !submerged;}
    public void setVerificationEnabled(boolean value) {
        if(AutomatedVerification.HIDDEN && enabled!=value){enabled=value;resetHistory("reflection-toggle");AsterRenderer.INSTANCE.temporalAa.resetHistory("reflection-toggle");}
    }
    public void resetHistory(String reason) {reset=true;pending.add(reason);}
    public TextureTarget geometry() {return geometry;}
    public TextureTarget response() {return response;}
    private void ensureSize(int width,int height) {
        if(geometry!=null && geometry.width==width && geometry.height==height)return;
        releaseTargets();
        geometry=new TextureTarget("Aster solid normal/roughness",width,height,GpuFormat.RGBA16_FLOAT,null);
        response=new TextureTarget("Aster solid reflection response",width,height,GpuFormat.RGBA16_FLOAT,null);
        output=new TextureTarget("Aster scene with solid reflections",width,height,GpuFormat.RGBA16_FLOAT,null);
        int w=Math.max(1,(width+1)/2),h=Math.max(1,(height+1)/2);
        raw=new TextureTarget("Aster half-resolution solid reflection trace",w,h,GpuFormat.RGBA16_FLOAT,null);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        for(int i=0;i<2;i++) {
            history[i]=new TextureTarget("Aster solid reflection history "+i,w,h,GpuFormat.RGBA16_FLOAT,null);
            historyGeometry[i]=new TextureTarget("Aster solid reflection geometry history "+i,w,h,GpuFormat.RGBA16_FLOAT,null);
            encoder.clearColorTexture(history[i].getColorTexture(),new Vector4f(0));
            encoder.clearColorTexture(historyGeometry[i].getColorTexture(),new Vector4f(0));
        }
        for(int i=0;i<mips.length;i++) {
            mips[i]=new TextureTarget("Aster solid radiance reduction "+i,w,h,GpuFormat.RGBA16_FLOAT,null);
            w=Math.max(1,w/2);h=Math.max(1,h/2);
        }
        resetHistory("resize");
    }
    public void begin(int width,int height,CameraRenderState state,Object level,Object resourceAtlas) {
        boolean water=state.fogType.name().equals("WATER");
        if(water!=submerged)resetHistory("underwater-transition");
        submerged=water;
        if(!enabled()){resetHistory("inactive");return;}
        ensureSize(width,height);
        if(level!=dimension)resetHistory("dimension");
        if(resourceAtlas!=atlas)resetHistory("resource-reload");
        var type=Minecraft.getInstance().options.getCameraType();
        if(type!=cameraType)resetHistory("camera-mode");
        if(Math.abs(state.projectionMatrix.m00()-fovX)>Math.abs(state.projectionMatrix.m00())*.02f
            || Math.abs(state.projectionMatrix.m11()-fovY)>Math.abs(state.projectionMatrix.m11())*.02f)resetHistory("projection-change");
        current.set(WorldCameraTransforms.projection(state)).mul(state.viewRotationMatrix);
        var delta=camera==null?Vec3.ZERO:state.pos.subtract(camera);
        if(camera==null || delta.lengthSqr()>16)resetHistory("camera-cut");
        var forward=new Matrix4f(current).invert().transformProject(new Vector3f(0,0,.01f)).normalize();
        var oldForward=new Matrix4f(previous).invert().transformProject(new Vector3f(0,0,.01f)).normalize();
        if(forward.dot(oldForward)<.7f)resetHistory("camera-turn");
        lastReasons=List.copyOf(pending);
        if(reset)for(String reason:pending)resetEvents.merge(reason,1,Integer::sum);
        pending.clear();
        try(var stack=MemoryStack.stackPush()) {
            var bytes=stack.malloc(160);
            current.get(0,bytes);previous.get(64,bytes);bytes.position(128);
            bytes.putFloat((float)delta.x()).putFloat((float)delta.y()).putFloat((float)delta.z()).putFloat(reset?1:0);
            bytes.putFloat(raw.width).putFloat(raw.height).putFloat(frames).putFloat(0);bytes.flip();
            var device=RenderSystem.getDevice();
            temporal=device.createCommandEncoder().transientMemory().uploadGpu(bytes,device.getDeviceInfo().limits().minUniformOffsetAlignment(),GpuBuffer.USAGE_UNIFORM);
        }
        camera=state.pos;dimension=level;atlas=resourceAtlas;cameraType=type;
        fovX=state.projectionMatrix.m00();fovY=state.projectionMatrix.m11();
    }
    private void bind(RenderPass pass) {
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        AsterRenderer.INSTANCE.bindScene(pass);
        AsterRenderer.INSTANCE.bindVerificationScene(pass);AsterRenderer.INSTANCE.bindSky(pass);
        AsterRenderer.INSTANCE.bindWater(pass);
        pass.setUniform("SolidTemporal",temporal);
        pass.setUniform("SolidGeometry",geometry.getColorTextureView(),nearest);
        pass.setUniform("SolidResponse",response.getColorTextureView(),nearest);
        pass.setUniform("SolidDepth",lastDepth.getDepthTextureView(),nearest);
        pass.setUniform("SolidScene",lastScene.getColorTextureView(),linear);
        pass.setUniform("SolidMotion",lastMotion.getColorTextureView(),nearest);
        for(int i=0;i<4;i++)pass.setUniform("SolidMip"+i,mips[i].getColorTextureView(),linear);
        pass.setUniform("SolidRaw",raw.getColorTextureView(),nearest);
        pass.setUniform("SolidHistory",history[index].getColorTextureView(),linear);
        pass.setUniform("SolidPreviousGeometry",historyGeometry[index].getColorTextureView(),nearest);
        pass.setUniform("SolidResolved",history[1-index].getColorTextureView(),nearest);
        pass.setUniform("SolidResolvedGeometry",historyGeometry[1-index].getColorTextureView(),nearest);
    }
    public TextureTarget render(TextureTarget scene,RenderTarget depth,TextureTarget motion) {
        if(!enabled()){skippedFrames++;return scene;}
        lastScene=scene;lastDepth=depth;lastMotion=motion;
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        var source=scene;
        for(var mip:mips) {
            try(var pass=encoder.createRenderPass(()->"Aster solid reflection radiance reduction",mip.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.DOWNSAMPLE));
                pass.setUniform("HdrInput",source.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));pass.draw(3,1,0,0);
            }
            source=mip;
        }
        try(var pass=encoder.createRenderPass(()->"Aster solid scene reflection trace",raw.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.SOLID_TRACE));bind(pass);pass.draw(3,1,0,0);
        }
        int next=1-index;
        try(var pass=encoder.createRenderPass(RenderPassDescriptor.builder(()->"Aster solid reflection history")
            .withColorAttachment(history[next].getColorTextureView()).withColorAttachment(historyGeometry[next].getColorTextureView()).build())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.SOLID_RESOLVE));bind(pass);pass.draw(3,1,0,0);
        }
        try(var pass=encoder.createRenderPass(()->"Aster solid reflection replacement",output.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.SOLID_APPLY));bind(pass);pass.draw(3,1,0,0);
        }
        previous.set(current);reset=false;index=next;frames++;
        return output;
    }
    public Object evidence() {
        var result=new LinkedHashMap<String,Object>();
        result.put("enabled",enabled());result.put("renderedFrames",frames);result.put("skippedFrames",skippedFrames);
        result.put("resetEvents",resetEvents);result.put("lastResetReasons",lastReasons);result.put("probes",probes);
        result.put("scope","Opaque terrain SSR, 48 coarse steps/6 refinements, roughness radiance reductions, bounded 0.82 history, sky fallback; water unchanged; no off-screen geometry tracing");
        if(raw!=null)result.put("traceSize",List.of(raw.width,raw.height));
        return result;
    }
    public void captureProbe(String label) {
        if(!AutomatedVerification.HIDDEN || !enabled() || lastScene==null)return;
        for(boolean historyProbe:List.of(false,true)) {
            var target=new TextureTarget("Aster solid reflection diagnostic",raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
            try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Aster solid reflection diagnostic",target.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(historyProbe?AsterPipelines.SOLID_HISTORY_DEBUG:AsterPipelines.SOLID_DEBUG));
                index=1-index;
                try{bind(pass);pass.draw(3,1,0,0);}finally{index=1-index;}
            }
            String suffix=historyProbe?"history":"trace";
            Screenshot.takeScreenshot(target,image->{
                try {
                    int eligible=0,accepted=0,sharp=0,rough=0;
                    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                        int pixel=image.getPixel(x,y);
                        if(ARGB.red(pixel)>127){eligible++;if(ARGB.green(pixel)>127)accepted++;if(ARGB.blue(pixel)<77)sharp++;else rough++;}
                    }
                    var result=Map.of("eligiblePixels",eligible,"acceptedPixels",accepted,"sharpPixels",sharp,"roughPixels",rough,"pixels",image.getWidth()*image.getHeight());
                    probes.put(label+"-"+suffix,result);
                    var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                    image.writeToFile(folder.resolve(label+"-"+suffix+".png"));
                    Files.writeString(folder.resolve(label+"-"+suffix+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
                }catch(Exception ex){throw new RuntimeException(ex);}finally{image.close();target.destroyBuffers();}
            });
        }
    }
    private void releaseTargets() {
        for(var target:Arrays.asList(geometry,response,raw,output))if(target!=null)target.destroyBuffers();
        geometry=response=raw=output=null;
        for(var group:List.of(history,historyGeometry,mips))for(int i=0;i<group.length;i++)if(group[i]!=null){group[i].destroyBuffers();group[i]=null;}
    }
    @Override public void close(){releaseTargets();camera=null;dimension=atlas=null;resetHistory("renderer-close");}
}
