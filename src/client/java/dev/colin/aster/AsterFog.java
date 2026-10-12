package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import java.util.*;

/** Depth-resolved atmosphere; the old endpoint path is verification-only. */
public final class AsterFog implements AutoCloseable {
    private final AsterFogVolume volume=new AsterFogVolume();
    private final AsterFogEnvironment environment=new AsterFogEnvironment();
    private TextureTarget foggedOpaque;
    private boolean verificationNoHistory;
    private GpuBufferSlice volumeTemporal;
    private final GpuBufferSlice[] scanUniforms=new GpuBufferSlice[7];
    private TextureTarget raw;
    private TextureTarget referenceRaw;
    private final TextureTarget[] referenceHistory=new TextureTarget[2];
    private static final boolean CAPTURE_REFERENCE=AutomatedVerification.HIDDEN && System.getProperty("aster.verificationProfile","").equals("fog");
    private static final boolean LEGACY_DIAGNOSTICS=AutomatedVerification.HIDDEN && Set.of("fog","lifecycle","foliage","leafgrain").contains(System.getProperty("aster.verificationProfile",""));
    private final TextureTarget[] geometry=new TextureTarget[2],history=new TextureTarget[2];
    private final Matrix4f previousProjection=new Matrix4f(),currentProjection=new Matrix4f();
    private final Vector3f previousSun=new Vector3f(),previousColor=new Vector3f();
    private Vec3 previousCamera;
    private Object dimension;
    private GpuTexture atlasSource;
    private float previousStrength,previousWeather;
    private boolean underwater,reset=true;
    private int index,previousIndex,frames,historyResets;
    private GpuBufferSlice temporal,scene;
    private final Set<String> pendingResets=new LinkedHashSet<>();
    private List<String> lastResets=List.of();
    private final Map<String,Integer> resetEvents=new LinkedHashMap<>();
    private final Map<String,Object> probes=new LinkedHashMap<>();
    private final GpuQueryPool[] queries=new GpuQueryPool[8];
    private final String[] queryTags=new String[8];
    private final Map<String,List<Double>> taggedTimings=new LinkedHashMap<>();
    private boolean verificationReferenceMode;
    private final List<Double> timings=new ArrayList<>();
    private final Map<String,Boolean> physicsProbes=new LinkedHashMap<>();
    private final Map<String,Object> referenceProbes=new LinkedHashMap<>();
    private int shadowedVolumePixels,sunlitVolumePixels;
    private TextureTarget[] shadowInputs;

    public void resetHistory(String reason) {
        reset=true;
        if(pendingResets.add(reason)) resetEvents.merge(reason,1,Integer::sum);
    }
    public void setVerificationReference(boolean value) {
        if(AutomatedVerification.HIDDEN && verificationReferenceMode != value) {
            verificationReferenceMode=value;resetHistory("quality-change");
        }
    }
    public void begin(int width,int height,CameraRenderState camera,Object currentDimension,TextureAtlas atlas,GpuBufferSlice scene,Vector3f sun,float strength,Vector3f color,float weather) {
        environment.update(camera.pos);
        if(volume.ensure(width,height)) resetHistory("volume-resize");
        int w=LEGACY_DIAGNOSTICS ? Math.max(1,(width+1)/2) : 1,h=LEGACY_DIAGNOSTICS ? Math.max(1,(height+1)/2) : 1;
        if(raw == null || raw.width != w || raw.height != h) {
            releaseTargets();
            raw=new TextureTarget("Aster fog single scattering",w,h,GpuFormat.RGBA16_FLOAT,null);
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            if(CAPTURE_REFERENCE) referenceRaw=new TextureTarget("Aster verification nine tap fog",w,h,GpuFormat.RGBA16_FLOAT,null);
            for(int i=0;i<2;i++) {
                geometry[i]=new TextureTarget("Aster fog endpoint "+i,w,h,GpuFormat.RG32_FLOAT,null);
                history[i]=new TextureTarget("Aster fog history "+i,w,h,GpuFormat.RGBA16_FLOAT,null);
                encoder.clearColorTexture(geometry[i].getColorTexture(),new Vector4f(0));
                encoder.clearColorTexture(history[i].getColorTexture(),new Vector4f(0,0,0,1));
                if(CAPTURE_REFERENCE) {
                    referenceHistory[i]=new TextureTarget("Aster verification fog history "+i,w,h,GpuFormat.RGBA16_FLOAT,null);
                    encoder.clearColorTexture(referenceHistory[i].getColorTexture(),new Vector4f(0,0,0,1));
                }
            }
            resetHistory("resize");
        }
        boolean submerged=camera.fogType.name().equals("WATER");
        if(dimension != currentDimension) resetHistory("dimension");
        if(submerged != underwater) resetHistory("underwater-transition");
        if(atlasSource != atlas.getTexture()) resetHistory("resource-reload");
        if(previousCamera == null || previousCamera.distanceToSqr(camera.pos) > 8*8) resetHistory("camera-cut");
        if(frames > 0 && (previousSun.dot(sun) < .995f || Math.abs(previousStrength-strength) > .08f)) resetHistory("lighting-change");
        if(frames > 0 && Math.abs(previousWeather-weather) > .08f) resetHistory("weather-change");
        if(frames > 0 && previousColor.distance(color) > .12f) resetHistory("atmosphere-change");
        currentProjection.set(WorldCameraTransforms.projection(camera)).mul(camera.viewRotationMatrix);
        this.scene=scene;
        var delta=previousCamera == null ? Vec3.ZERO : camera.pos.subtract(previousCamera);
        float environmentChange=Math.abs(previousWeather-weather)+Math.abs(previousStrength-strength)+previousColor.distance(color);
        float reaction=(float)Math.exp(-delta.length()*.6-environmentChange*12);
        try(var stack=MemoryStack.stackPush()) {
            var bytes=stack.malloc(256);
            currentProjection.get(0,bytes);previousProjection.get(64,bytes);
            new Matrix4f(previousProjection).invert().get(128,bytes);
            bytes.position(192);
            bytes.putFloat((float)delta.x()).putFloat((float)delta.y()).putFloat((float)delta.z()).putFloat(frames);
            bytes.putFloat(w).putFloat(h).putFloat(reset ? 1 : 0).putFloat(160);
            bytes.putFloat(reaction).putFloat(0).putFloat(0).putFloat(0);
            bytes.putFloat(environment.centerX()).putFloat(environment.originY()).putFloat(environment.centerZ()).putFloat(0);bytes.flip();
            temporal=RenderSystem.getDevice().createCommandEncoder().transientMemory().uploadGpu(bytes,RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(),GpuBuffer.USAGE_UNIFORM);
            bytes.putFloat(208,volume.width());bytes.putFloat(212,volume.height());bytes.putFloat(216,reset || verificationNoHistory ? 1 : 0);
            var upload=RenderSystem.getDevice().createCommandEncoder().transientMemory();
            int alignment=RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment();
            volumeTemporal=upload.uploadGpu(bytes,alignment,GpuBuffer.USAGE_UNIFORM);
            for(int stage=0;stage<7;stage++) {
                bytes.putFloat(228,stage == 0 ? 0 : 1<<(stage-1));
                scanUniforms[stage]=upload.uploadGpu(bytes,alignment,GpuBuffer.USAGE_UNIFORM);
            }
        }
        dimension=currentDimension;underwater=submerged;atlasSource=atlas.getTexture();previousCamera=camera.pos;
        previousSun.set(sun);previousStrength=strength;previousWeather=weather;previousColor.set(color);
    }
    public void renderVolume(TextureTarget[] shadows) {
        shadowInputs=shadows;
        var device=RenderSystem.getDevice();var encoder=device.createCommandEncoder();
        int ring=frames%queries.length;
        if(queries[ring] == null) queries[ring]=device.createTimestampQueryPool(2);
        else if(frames >= queries.length) {
            var values=queries[ring].getValues(0,2);
            if(values[0].isPresent() && values[1].isPresent()) {
                double ms=(values[1].getAsLong()-values[0].getAsLong())*device.getDeviceInfo().timestampPeriod()/1_000_000.0;
                timings.add(ms);
                if(queryTags[ring] != null) taggedTimings.computeIfAbsent(queryTags[ring],key -> new ArrayList<>()).add(ms);
                if(timings.size() > 1200) timings.removeFirst();
            }
        }
        queryTags[ring]=FrameProfiler.currentTag();
        encoder.writeTimestamp(queries[ring],0);
        volume.render(scene,volumeTemporal,scanUniforms,shadows,environment);
        encoder.writeTimestamp(queries[ring],1);
    }
    public void render(RenderTarget depth,TextureTarget materials,TextureTarget[] shadows) {
        var encoder=RenderSystem.getDevice().createCommandEncoder();previousIndex=index;index=1-index;
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        if(LEGACY_DIAGNOSTICS) {
        try(var pass=encoder.createRenderPass(() -> "Aster fog endpoints",geometry[index].getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_GEOMETRY));bindUniforms(pass);
            pass.setUniform("SceneDepth",depth.getDepthTextureView(),nearest);
            pass.setUniform("MaterialData",materials.getColorTextureView(),nearest);
            AsterRenderer.INSTANCE.bindWater(pass);pass.draw(3,1,0,0);
        }
        try(var pass=encoder.createRenderPass(() -> "Aster sixteen step volumetric fog",raw.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(verificationReferenceMode ? AsterPipelines.FOG_TRACE_REFERENCE : AsterPipelines.FOG_TRACE));bindCurrent(pass);
            for(int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        try(var pass=encoder.createRenderPass(() -> "Aster fog reprojection",history[index].getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_RESOLVE));bindResolve(pass);pass.draw(3,1,0,0);
        }
        if(CAPTURE_REFERENCE) {
            try(var pass=encoder.createRenderPass(() -> "Aster verification nine tap fog",referenceRaw.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_TRACE_REFERENCE));bindCurrent(pass);
                for(int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),nearest);
                pass.draw(3,1,0,0);
            }
            try(var pass=encoder.createRenderPass(() -> "Aster verification fog history",referenceHistory[index].getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_RESOLVE));bindResolve(pass);
                pass.setUniform("RawFog",referenceRaw.getColorTextureView(),nearest);
                pass.setUniform("PreviousFog",referenceHistory[previousIndex].getColorTextureView(),nearest);pass.draw(3,1,0,0);
            }
        }
        }
        if(reset) historyResets++;
        lastResets=List.copyOf(pendingResets);pendingResets.clear();reset=false;
        previousProjection.set(currentProjection);frames++;
    }
    private void bindUniforms(RenderPass pass) { pass.setUniform("AsterScene",scene);pass.setUniform("FogTemporal",temporal); }
    public void bindVolume(RenderPass pass) { volume.bind(pass); }
    public void captureVolume(String label,boolean colors) {volume.capture(label,scene,volumeTemporal,colors);volume.capturePhysics(label,scene);}
    public void setVerificationNoHistory(boolean value) {if(AutomatedVerification.HIDDEN) {verificationNoHistory=value;resetHistory("volume-history-ablation");}}
    public void recoverOpaque(TextureTarget opaque) {
        if(foggedOpaque == null || foggedOpaque.width != opaque.width || foggedOpaque.height != opaque.height) {
            if(foggedOpaque != null) foggedOpaque.destroyBuffers();
            foggedOpaque=new TextureTarget("Aster opaque fog recovery source",opaque.width,opaque.height,GpuFormat.RGBA16_FLOAT,null);
        }
        foggedOpaque.copyColorFrom(opaque);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster recover surface radiance for water optics",opaque.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_OPAQUE_RECOVER));pass.setUniform("AsterScene",scene);volume.bind(pass);
            pass.setUniform("FoggedOpaque",foggedOpaque.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("OpaqueDepth",opaque.getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);
        }
    }
    private void bindCurrent(RenderPass pass) {
        bindUniforms(pass);pass.setUniform("FogGeometry",geometry[index].getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
    private void bindResolve(RenderPass pass) {
        bindCurrent(pass);var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        pass.setUniform("RawFog",raw.getColorTextureView(),nearest);
        pass.setUniform("PreviousFogGeometry",geometry[previousIndex].getColorTextureView(),nearest);
        pass.setUniform("PreviousFog",history[previousIndex].getColorTextureView(),nearest);
    }
    public void bindOutput(RenderPass pass) {
        volume.bind(pass);
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        pass.setUniform("FogGeometry",geometry[index].getColorTextureView(),nearest);
        pass.setUniform("FogScattering",history[index].getColorTextureView(),nearest);
    }
    public void capture(String label,boolean colors) {
        volume.capture(label,scene,volumeTemporal,colors);
        var debug=new TextureTarget("Aster fog history acceptance",raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster fog history acceptance",debug.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_HISTORY_DEBUG));bindResolve(pass);pass.draw(3,1,0,0);
        }
        var resetReasons=lastResets;
        Screenshot.takeScreenshot(debug,image -> {
            try {
                int surfaces=0,accepted=0,sky=0,skyAccepted=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int p=image.getPixel(x,y);boolean reused=ARGB.green(p) > 25;
                    if(ARGB.red(p) > 192) { surfaces++;if(reused) accepted++; }
                    else { sky++;if(reused) skyAccepted++; }
                }
                var data=new LinkedHashMap<String,Object>();data.put("surfacePixels",surfaces);data.put("historyAcceptedPixels",accepted);
                data.put("skyPixels",sky);data.put("skyHistoryAcceptedPixels",skyAccepted);
                data.put("historyAcceptedFraction",surfaces == 0 ? 0 : (double)accepted/surfaces);
                data.put("resetReasons",resetReasons);probes.put(label,data);
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-fog-history.png"));
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { image.close();debug.destroyBuffers(); }
        });
        if(colors) {
            captureColor(label+"-fog-raw",raw);captureColor(label+"-fog-filtered",history[index]);
            if(CAPTURE_REFERENCE) {
                captureColor(label+"-fog-reference",referenceHistory[index]);
                if(label.startsWith("stable-")) captureReferenceProbe(label);
            }
        }
    }
    private void captureReferenceProbe(String label) {
        var target=new TextureTarget("Aster fog float error probe",1,1,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster fog float error probe",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_COMPARE));
            var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("FilteredFog",history[index].getColorTextureView(),nearest);
            pass.setUniform("ReferenceFog",referenceHistory[index].getColorTextureView(),nearest);
            pass.setUniform("RawFog",raw.getColorTextureView(),nearest);
            pass.setUniform("ReferenceRawFog",referenceRaw.getColorTextureView(),nearest);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int p=image.getPixel(0,0);
                referenceProbes.put(label,Map.of("meanAbsoluteLinearRGBError",ARGB.red(p)/(255.0*512),"maximumSampledLinearChannelError",ARGB.green(p)/(255.0*32),"rawTransmittanceMatches",ARGB.blue(p) > 192,"samples",4096));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-fog-reference-probe.png"));
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { image.close();target.destroyBuffers(); }
        });
    }
    public void capturePhysicsProbe(String label) {
        volume.capturePhysics(label,scene);
        var target=new TextureTarget("Aster fog integral acceptance",1,1,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster fog integral acceptance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_PROBE));bindUniforms(pass);
            pass.setUniform("RawFog",raw.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try { int p=image.getPixel(0,0);physicsProbes.put(label,ARGB.red(p) > 192 && ARGB.green(p) > 192 && ARGB.blue(p) > 192);
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-fog-integral-probe.png")); }
            catch(Exception e) { throw new RuntimeException(e); }
            finally { image.close();target.destroyBuffers(); }
        });
    }
    public void captureShadowProbe() {
        var target=new TextureTarget("Aster fog shadow acceptance",raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster fog shadow acceptance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_SHADOW_DEBUG));bindCurrent(pass);
            var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            for(int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadowInputs[i].getDepthTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                shadowedVolumePixels=0;sunlitVolumePixels=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int p=image.getPixel(x,y);
                    if(ARGB.blue(p) < 5) continue;
                    if(ARGB.red(p) > 64) shadowedVolumePixels++;
                    if(ARGB.green(p) > 192) sunlitVolumePixels++;
                }
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/fog-shadow-probe.png"));
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { image.close();target.destroyBuffers(); }
        });
    }
    private void captureColor(String label,TextureTarget input) {
        var preview=new TextureTarget(label,raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label,preview.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_DEBUG));
            pass.setUniform("FogInput",input.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(preview,image -> {
            try { image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+".png")); }
            catch(Exception e) { throw new RuntimeException(e); }
            finally { image.close();preview.destroyBuffers(); }
        });
    }
    public Object evidence() {
        var volumeData=volume.evidence();
        var data=new LinkedHashMap<String,Object>();data.put("format","RGBA16_FLOAT scattering/transmittance; RG32_FLOAT signed endpoint/sky access");
        data.put("volume",volumeData);data.put("environment",environment.evidence());
        data.put("legacyDiagnostics","16-step endpoint history is retained for historical regression comparisons; scene fog uses the volume");
        data.put("integrationSteps",16);data.put("maxDistance",160);
        data.put("width",raw == null ? 0 : raw.width);data.put("height",raw == null ? 0 : raw.height);
        data.put("textureBytes",raw == null ? 0 : (long)raw.width*raw.height*40);
        data.put("shadowTapsPerStep",verificationReferenceMode ? 9 : 3);
        data.put("verificationReferenceTextureBytes",referenceRaw == null ? 0 : (long)referenceRaw.width*referenceRaw.height*24);
        data.put("frames",frames);data.put("historyResets",historyResets);data.put("resetEvents",new LinkedHashMap<>(resetEvents));
        data.put("lastResetReasons",lastResets);data.put("probes",new LinkedHashMap<>(probes));data.put("physicsProbes",new LinkedHashMap<>(physicsProbes));
        data.put("referenceProbes",new LinkedHashMap<>(referenceProbes));
        data.put("physicsProbePassed",!physicsProbes.isEmpty() && physicsProbes.values().stream().allMatch(Boolean::booleanValue));
        data.put("shadowedVolumePixels",shadowedVolumePixels);data.put("sunlitVolumePixels",sunlitVolumePixels);
        data.put("gpuSamples",timings.size());
        if(!timings.isEmpty()) { var sorted=timings.stream().mapToDouble(Double::doubleValue).sorted().toArray();data.put("gpuMedianMs",sorted[sorted.length/2]);data.put("gpuP95Ms",sorted[Math.min(sorted.length-1,(int)(sorted.length*.95))]); }
        var groups=new LinkedHashMap<String,Object>();
        for(var entry:taggedTimings.entrySet()) {
            var sorted=entry.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();
            if(sorted.length > 0) groups.put(entry.getKey(),Map.of("samples",sorted.length,"medianMs",sorted[sorted.length/2],"p95Ms",sorted[Math.min(sorted.length-1,(int)(sorted.length*.95))]));
        }
        data.put("taggedGpuTimes",groups);
        return data;
    }
    private void releaseTargets() {
        if(raw != null) { raw.destroyBuffers();raw=null; }
        if(referenceRaw != null) { referenceRaw.destroyBuffers();referenceRaw=null; }
        for(int i=0;i<2;i++) { if(geometry[i] != null) { geometry[i].destroyBuffers();geometry[i]=null; } if(history[i] != null) { history[i].destroyBuffers();history[i]=null; } }
        for(int i=0;i<2;i++) if(referenceHistory[i] != null) { referenceHistory[i].destroyBuffers();referenceHistory[i]=null; }
    }
    @Override public void close() {
        if(foggedOpaque != null) {foggedOpaque.destroyBuffers();foggedOpaque=null;}
        volume.close();environment.close();volumeTemporal=null;Arrays.fill(scanUniforms,null);
        releaseTargets();previousCamera=null;dimension=null;atlasSource=null;temporal=null;scene=null;shadowInputs=null;
        for(int i=0;i<queries.length;i++) if(queries[i] != null) { queries[i].close();queries[i]=null; }
        resetHistory("renderer-close");
    }
}
