package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.colin.aster.mixin.ChunkSectionsAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import java.util.*;
import java.nio.file.Files;

/** Water-only surface replay, bounded SSR, and validated HDR history on the native device. */
public final class AsterWater implements AutoCloseable {
    private final AsterPlanarWater planar=new AsterPlanarWater();
    private CameraRenderState camera;
    private TextureTarget lastOpaque;
    private final Map<String,Object> sourceProbes=new LinkedHashMap<>();
    private final TextureTarget[] surfaces=new TextureTarget[2],reflections=new TextureTarget[2];
    private TextureTarget raw;
    private final TextureTarget[] radiancePyramid=new TextureTarget[4];
    private int opaqueWidth,opaqueHeight;
    private final int configuredResolution=readResolution();
    private Integer verificationResolution;
    private boolean previousReflection;
    private final Matrix4f previousProjection=new Matrix4f(),currentProjection=new Matrix4f();
    private Vec3 previousCamera;
    private Object dimension;
    private GpuTexture atlasSource;
    private boolean underwater,reset=true;
    private int index,previousIndex,frames,historyResets;
    private GpuBufferSlice temporal,scene;
    private final Set<String> pendingResets=new LinkedHashSet<>();
    private List<String> lastResets=List.of();
    private final Map<String,Integer> resetEvents=new LinkedHashMap<>();
    private final Map<String,Object> probes=new LinkedHashMap<>();
    private Map<String,Object> opticsProbe=Map.of();
    private final GpuQueryPool[] queries=new GpuQueryPool[8];
    private final List<Double> timings=new ArrayList<>();
    private final String[] queryTags=new String[8];
    private final Map<String,List<Double>> caseTimings=new LinkedHashMap<>();

    private static int readResolution() {
        // Optional standard Fabric-instance config; no launcher/JVM changes.
        var path=FabricLoader.getInstance().getConfigDir().resolve("aster-water.properties");
        if(Files.isRegularFile(path)) {
            try(var reader=Files.newBufferedReader(path)) {
                var properties=new Properties();properties.load(reader);
                return switch(properties.getProperty("reflectionResolution","balanced").trim().toLowerCase(Locale.ROOT)) {
                    case "performance" -> 50;
                    case "balanced" -> 75;
                    case "quality" -> 100;
                    default -> { AsterClient.LOGGER.warn("Unknown Aster water reflectionResolution; using balanced.");yield 75; }
                };
            } catch(Exception ex) { AsterClient.LOGGER.warn("Cannot read Aster water quality; using balanced.",ex); }
        }
        return 75;
    }
    public void setVerificationResolution(int percent) {
        if(!AutomatedVerification.HIDDEN) return;
        if(percent != 50 && percent != 75 && percent != 100) throw new IllegalArgumentException("Reflection resolution must be 50, 75 or 100 percent");
        if(!Objects.equals(verificationResolution,percent)) { verificationResolution=percent;resetHistory("reflection-quality"); }
    }

    public void resetHistory(String reason) {
        reset=true;
        if(pendingResets.add(reason)) resetEvents.merge(reason,1,Integer::sum);
    }
    public void begin(int width,int height,CameraRenderState camera,Object currentDimension,TextureAtlas atlas,GpuBufferSlice scene) {
        this.camera=camera;
        int percent=verificationResolution == null ? configuredResolution : verificationResolution;
        int w=Math.max(1,(width*percent+99)/100),h=Math.max(1,(height*percent+99)/100);
        if(raw == null || raw.width != w || raw.height != h) {
            releaseTargets();
            raw=new TextureTarget("Aster raw water reflection",w,h,GpuFormat.RGBA16_FLOAT,null);
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            for(int i=0;i<2;i++) {
                surfaces[i]=new TextureTarget("Aster water surface "+i,w,h,GpuFormat.RGBA16_FLOAT,GpuFormat.D32_FLOAT);
                reflections[i]=new TextureTarget("Aster water reflection history "+i,w,h,GpuFormat.RGBA16_FLOAT,null);
                encoder.clearColorAndDepthTextures(surfaces[i].getColorTexture(),new Vector4f(0),surfaces[i].getDepthTexture(),0);
                encoder.clearColorTexture(reflections[i].getColorTexture(),new Vector4f(0));
            }
            resetHistory("resize");
        }
        if(opaqueWidth != width || opaqueHeight != height || radiancePyramid[0] == null) {
            releaseRadiance();opaqueWidth=width;opaqueHeight=height;
            int mw=width,mh=height;
            for(int i=0;i<radiancePyramid.length;i++) {
                mw=Math.max(1,(mw+1)/2);mh=Math.max(1,(mh+1)/2);
                radiancePyramid[i]=new TextureTarget("Aster reflection scene color "+i,mw,mh,GpuFormat.RGBA16_FLOAT,null);
            }
        }
        boolean submerged=camera.fogType.name().equals("WATER");
        if(dimension != currentDimension) resetHistory("dimension");
        if(submerged != underwater) resetHistory("underwater-transition");
        if(atlasSource != atlas.getTexture()) resetHistory("resource-reload");
        if(previousCamera == null || previousCamera.distanceToSqr(camera.pos) > 8*8) resetHistory("camera-cut");
        currentProjection.set(WorldCameraTransforms.projection(camera)).mul(camera.viewRotationMatrix);
        this.scene=scene;
        var delta=previousCamera == null ? Vec3.ZERO : camera.pos.subtract(previousCamera);
        try(var stack=MemoryStack.stackPush()) {
            var bytes=stack.malloc(224);
            currentProjection.get(0,bytes);
            previousProjection.get(64,bytes);
            new Matrix4f(previousProjection).invert().get(128,bytes);
            bytes.position(192);
            bytes.putFloat((float)delta.x()).putFloat((float)delta.y()).putFloat((float)delta.z()).putFloat(frames);
            bytes.putFloat(w).putFloat(h).putFloat(reset ? 1 : 0).putFloat(1);
            bytes.flip();
            temporal=RenderSystem.getDevice().createCommandEncoder().transientMemory().uploadGpu(bytes,RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(),GpuBuffer.USAGE_UNIFORM);
        }
        dimension=currentDimension;underwater=submerged;atlasSource=atlas.getTexture();
        previousCamera=camera.pos;
    }
    public void render(ChunkSectionsToRender chunks,TextureTarget opaque,TextureTarget[] shadows,boolean indirect) {
        lastOpaque=opaque;
        var device=RenderSystem.getDevice();var encoder=device.createCommandEncoder();
        int ring=frames%queries.length;
        if(queries[ring] == null) queries[ring]=device.createTimestampQueryPool(2);
        else if(frames >= queries.length) {
            var values=queries[ring].getValues(0,2);
            if(values[0].isPresent() && values[1].isPresent()) {
                timings.add((values[1].getAsLong()-values[0].getAsLong())*device.getDeviceInfo().timestampPeriod()/1_000_000.0);
                if(queryTags[ring] != null) caseTimings.computeIfAbsent(queryTags[ring],key -> new ArrayList<>()).add(timings.getLast());
                if(timings.size() > 1200) timings.removeFirst();
            }
        }
        encoder.writeTimestamp(queries[ring],0);
        planar.render(Minecraft.getInstance().levelRenderer,camera,dimension,scene,shadows,indirect);
        queryTags[ring]=FrameProfiler.currentTag();
        previousIndex=index;index=1-index;
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var mc=Minecraft.getInstance();
        previousReflection=AutomatedVerification.HIDDEN && (AsterRenderer.INSTANCE.verificationLightingFlags()&(512|4096)) != 0;
        if(!previousReflection) {
            var input=opaque;
            for(var target:radiancePyramid) {
                try(var pass=encoder.createRenderPass(() -> "Aster reflection color prefilter",target.getColorTextureView(),Optional.empty())) {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.DOWNSAMPLE));
                    pass.setUniform("HdrInput",input.getColorTextureView(),linear);pass.draw(3,1,0,0);
                }
                input=target;
            }
        }
        try(var pass=encoder.createRenderPass(() -> "Aster visible water surface",surfaces[index].getColorTextureView(),Optional.of(new Vector4f(0)),surfaces[index].getDepthTextureView(),OptionalDouble.of(0))) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("AsterScene",scene);pass.setUniform("WaterTemporal",temporal);
            pass.setUniform("OpaqueDepth",opaque.getDepthTextureView(),nearest);
            var atlas=mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
            ((ChunkSectionsAccessor)chunks).aster$renderLayers(ChunkSectionLayerGroup.TRANSLUCENT.layers(),nearest,pass,atlas,mc.gameRenderer.lightmap(),AsterPipelines.WATER_SURFACE,AsterPipelines.WATER_SURFACE_INDIRECT);
        }
        try(var pass=encoder.createRenderPass(() -> "Aster water SSR",raw.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_TRACE));
            bindCurrent(pass);
            planar.bind(pass);
            pass.setUniform("OpaqueColor",opaque.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("OpaqueDepth",opaque.getDepthTextureView(),nearest);
            for(int i=0;i<radiancePyramid.length;i++) pass.setUniform("OpaqueMip"+i,radiancePyramid[i].getColorTextureView(),linear);
            pass.draw(3,1,0,0);
        }
        try(var pass=encoder.createRenderPass(() -> "Aster water reflection reprojection",reflections[index].getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_RESOLVE));
            bindResolve(pass);pass.draw(3,1,0,0);
        }
        encoder.writeTimestamp(queries[ring],1);
        if(reset) historyResets++;
        lastResets=List.copyOf(pendingResets);pendingResets.clear();reset=false;
        previousProjection.set(currentProjection);frames++;
    }
    private void bindCurrent(RenderPass pass) {
        AsterRenderer.INSTANCE.bindSky(pass);
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        pass.setUniform("AsterScene",scene);pass.setUniform("WaterTemporal",temporal);
        pass.setUniform("WaterSurface",surfaces[index].getColorTextureView(),nearest);
        pass.setUniform("WaterDepth",surfaces[index].getDepthTextureView(),nearest);
    }
    private void bindResolve(RenderPass pass) {
        bindCurrent(pass);var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        pass.setUniform("RawReflection",raw.getColorTextureView(),nearest);
        pass.setUniform("PreviousSurface",surfaces[previousIndex].getColorTextureView(),nearest);
        pass.setUniform("PreviousDepth",surfaces[previousIndex].getDepthTextureView(),nearest);
        pass.setUniform("PreviousReflection",reflections[previousIndex].getColorTextureView(),nearest);
    }
    public void bindOutput(RenderPass pass,TextureTarget fallback) {
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        pass.setUniform("WaterReflection",raw == null ? fallback.getColorTextureView() : reflections[index].getColorTextureView(),nearest);
        pass.setUniform("WaterSurface",raw == null ? fallback.getColorTextureView() : surfaces[index].getColorTextureView(),nearest);
        pass.setUniform("WaterDepth",raw == null ? fallback.getDepthTextureView() : surfaces[index].getDepthTextureView(),nearest);
    }
    public void capture(String label,boolean colors) {
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        var debug=new TextureTarget("Aster water history acceptance",raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=encoder.createRenderPass(() -> "Aster water history acceptance",debug.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_DEBUG));bindResolve(pass);pass.draw(3,1,0,0);
        }
        var resetReasons=lastResets;
        Screenshot.takeScreenshot(debug,image -> {
            try {
                int water=0,accepted=0,hits=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int p=image.getPixel(x,y);
                    if(ARGB.red(p) > 192) { water++;if(ARGB.green(p) > 25) accepted++;if(ARGB.blue(p) > 25) hits++; }
                }
                var data=new LinkedHashMap<String,Object>();
                data.put("waterPixels",water);data.put("historyAcceptedPixels",accepted);data.put("ssrHitPixels",hits);
                data.put("historyAcceptedFraction",water == 0 ? 0 : (double)accepted/water);
                data.put("resetReasons",resetReasons);probes.put(label,data);
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-water-history.png"));
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { image.close();debug.destroyBuffers(); }
        });
        if(colors) { captureColor(label+"-water-raw",raw);captureColor(label+"-water-filtered",reflections[index]); }
    }
    public void captureSourceProbe(String label) {
        if(!AutomatedVerification.HIDDEN && !Boolean.getBoolean("watercompare.hidden")) return;
        try { Files.createDirectories(FabricLoader.getInstance().getGameDir().resolve("aster-evidence")); }
        catch(Exception ex) { throw new RuntimeException(ex); }
        var target=new TextureTarget("Aster reflection source probe",raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster reflection source probe",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_TRACE_DEBUG));bindCurrent(pass);planar.bind(pass);
            pass.setUniform("OpaqueColor",lastOpaque.getColorTextureView(),linear);pass.setUniform("OpaqueDepth",lastOpaque.getDepthTextureView(),nearest);
            for(int i=0;i<radiancePyramid.length;i++) pass.setUniform("OpaqueMip"+i,radiancePyramid[i].getColorTextureView(),linear);
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int covered=0,ssr=0,independent=0,missFilled=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int pixel=image.getPixel(x,y);
                    if(ARGB.blue(pixel)<192) continue;
                    covered++;if(ARGB.red(pixel)>25) ssr++;
                    if(ARGB.green(pixel)>192) {independent++;if(ARGB.red(pixel)<=25) missFilled++;}
                }
                sourceProbes.put(label,Map.of("waterPixels",covered,"ssrHitPixels",ssr,"independentTerrainPixels",independent,"ssrMissFilledPixels",missFilled));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-sources.png"));
            } catch(Exception ex) {throw new RuntimeException(ex);}
            finally {image.close();target.destroyBuffers();}
        });
        captureColor(label+"-planar",planar.target());
        captureColor(label+"-water-raw",raw);
        captureColor(label+"-water-filtered",reflections[index]);
    }
    private final Map<String,Object> edgeProbes=new LinkedHashMap<>();
    public void captureEdgeProbe(String label) {
        if(!AutomatedVerification.HIDDEN) return;
        var target=new TextureTarget("Aster viewport refraction invariants",512,32,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster viewport refraction invariants",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_EDGE_PROBE));
            pass.setUniform("AsterScene",scene);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int parallel=0,bounded=0,monotonic=0,samples=image.getWidth()*image.getHeight();
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int pixel=image.getPixel(x,y);
                    if(ARGB.red(pixel)>192) parallel++;
                    if(ARGB.green(pixel)>192) bounded++;
                    if(ARGB.blue(pixel)>192) monotonic++;
                }
                edgeProbes.put(label,Map.of("samples",samples,"parallelStablePixels",parallel,"boundedPixels",bounded,"monotonicPixels",monotonic));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-edge-probe.png"));
            } catch(Exception ex) {throw new RuntimeException(ex);}
            finally {image.close();target.destroyBuffers();}
        });
    }
    private Object waveProbe;
    private final Map<String,Object> contactProbes=new LinkedHashMap<>();
    public void captureContactProbe(String label) {
        if(!AutomatedVerification.HIDDEN) return;
        var target=new TextureTarget("Aster bank contact depth probe",raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster bank contact depth probe",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CONTACT_PROBE));bindCurrent(pass);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int anchors=0,stable=0,water=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int pixel=image.getPixel(x,y);
                    if(ARGB.blue(pixel) > 192) water++;
                    if(ARGB.red(pixel) > 192) { anchors++;if(ARGB.green(pixel) > 192) stable++; }
                }
                contactProbes.put(label,Map.of("waterPixels",water,"anchorPixels",anchors,"stableAnchorPixels",stable,"stableFraction",anchors == 0 ? 0.0 : (double)stable/anchors));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-contact.png"));
            } catch(Exception ex) {throw new RuntimeException(ex);}
            finally {image.close();target.destroyBuffers();}
        });
    }
    public void captureWaveProbe() {
        if(!AutomatedVerification.HIDDEN) return;
        var target=new TextureTarget("Aster wave field acceptance",512,4,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster wave field acceptance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_WAVE_PROBE));
            pass.setUniform("AsterScene",scene);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int macro=0,detail=0,bounded=0,loop=0,rain=0,minified=0,roughness=0,rippleDerivativeError=0,rainError=0;
                int maxCombinedSlope=0,maxMacroSlope=0,maxRippleSlope=0;
                for(int x=0;x<512;x++) {
                    // Screenshot rows run from top to bottom; the shader rows run upward.
                    int pixel=image.getPixel(x,3);
                    if(ARGB.red(pixel) == 255) macro++;
                    if(ARGB.green(pixel) == 255) detail++;
                    if(ARGB.blue(pixel) == 255) bounded++;
                    pixel=image.getPixel(x,2);
                    if(ARGB.red(pixel) == 255) loop++;
                    if(ARGB.green(pixel) == 255) rain++;
                    if(ARGB.blue(pixel) == 255) minified++;
                    pixel=image.getPixel(x,1);
                    if(ARGB.green(pixel) == 255) roughness++;
                    rippleDerivativeError=Math.max(rippleDerivativeError,ARGB.red(pixel));
                    rainError=Math.max(rainError,ARGB.blue(pixel));
                    pixel=image.getPixel(x,0);
                    maxCombinedSlope=Math.max(maxCombinedSlope,ARGB.red(pixel));
                    maxMacroSlope=Math.max(maxMacroSlope,ARGB.green(pixel));
                    maxRippleSlope=Math.max(maxRippleSlope,ARGB.blue(pixel));
                }
                var result=new LinkedHashMap<String,Object>();
                result.put("slopeTolerance",.001);
                result.put("largeCoordinateDifferenceSteps",List.of(.015625,.03125,.046875));
                result.put("spatialPhaseAnchorBoundariesIncluded",true);
                result.put("largeCoordinateDerivativeEstimator","sixth-order central differences for the expanded short-wave spectrum");
                result.put("readbackRowOrder","top-down image: numeric slopes, error values, temporal checks, spatial checks");
                result.put("numericCombinedSlopeMax",maxCombinedSlope*2.0/255.0);
                result.put("numericMacroSlopeMax",maxMacroSlope*2.0/255.0);
                result.put("numericRippleSlopeMax",maxRippleSlope*2.0/255.0);
                result.put("numericSlopeReadbackStep",2.0/255.0);
                result.put("numericSlopeScope","Linear UNORM RGB diagnostic for the same 512 sampled coordinates; quantized magnitudes are not an all-world bound. Original exact GPU pass bits remain authoritative.");
                result.put("samples",512);result.put("macroDerivativePassed",macro);result.put("rippleDerivativePassed",detail);
                result.put("boundedHeightSlopePassed",bounded);result.put("clockLoopPassed",loop);
                result.put("rainErrorQuantization",1.0/2550000.0);
                result.put("rippleErrorQuantization",1.0/25500.0);
                result.put("maxRippleDerivativeError",rippleDerivativeError/25500.0);
                result.put("maxRainError",rainError/2550000.0);
                result.put("rainResponsePassed",rain);result.put("minificationPassed",minified);
                result.put("unresolvedRoughnessPassed",roughness);
                result.put("passed",macro == 512 && detail == 512 && bounded == 512 && loop == 512 && rain == 512 && minified == 512 && roughness == 512);waveProbe=result;
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/water-wave-probe.png"));
            } catch(Exception ex) { throw new RuntimeException(ex); }
            finally { image.close();target.destroyBuffers(); }
        });
    }
    public void captureOpticsProbe() {
        if(!AutomatedVerification.HIDDEN) return;
        var target=new TextureTarget("Aster water optics acceptance",8,1,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster water optics acceptance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_PROBE));
            pass.setUniform("AsterScene",scene);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int normal=image.getPixel(0,0),angles=image.getPixel(1,0),depth=image.getPixel(2,0),identity=image.getPixel(3,0),gradient=image.getPixel(4,0);
                var result=new LinkedHashMap<String,Object>();
                result.put("normalIncidenceAbove",ARGB.red(normal)/255.0/8.0);
                result.put("normalIncidenceBelow",ARGB.green(normal)/255.0/8.0);
                result.put("grazingAbove",ARGB.blue(normal)/255.0);
                result.put("totalInternalReflection",ARGB.red(angles)/255.0);
                result.put("belowCriticalReflection",ARGB.green(angles)/255.0);
                result.put("fiveBlockTransmission",List.of(ARGB.red(depth)/255.0,ARGB.green(depth)/255.0,ARGB.blue(depth)/255.0));
                result.put("zeroDistanceIdentity",ARGB.red(identity) == 255 && ARGB.green(identity) == 255 && ARGB.blue(identity) == 255);
                result.put("waveGradientError",ARGB.red(gradient)/255000.0);
                int underwater=image.getPixel(5,0),underwaterIdentity=image.getPixel(6,0),underwaterChecks=image.getPixel(7,0);
                result.put("underwaterEightBlockTransmission",List.of(ARGB.red(underwater)/255.0,ARGB.green(underwater)/255.0,ARGB.blue(underwater)/255.0));
                boolean underwaterZero=ARGB.red(underwaterIdentity)==255 && ARGB.green(underwaterIdentity)==255 && ARGB.blue(underwaterIdentity)==255;
                boolean underwaterBounds=ARGB.red(underwaterChecks)==255,underwaterMonotonic=ARGB.green(underwaterChecks)==255,underwaterComposition=ARGB.blue(underwaterChecks)==255;
                result.put("underwaterZeroDistanceIdentity",underwaterZero);
                result.put("underwaterBoundedOrdered",underwaterBounds);
                result.put("underwaterMonotonic",underwaterMonotonic);
                result.put("underwaterPathComposition",underwaterComposition);
                result.put("underwaterProbeScope","512 GPU-evaluated distances from zero to 96 blocks, plus negative/zero identity and split-path composition at 0.000001 tolerance; original five acceptance texels and thresholds unchanged.");
                boolean passed=ARGB.red(normal) >= 38 && ARGB.red(normal) <= 45 && ARGB.green(normal) >= 38 && ARGB.green(normal) <= 45
                    && ARGB.blue(normal) > 70 && ARGB.red(angles) == 255 && ARGB.green(angles) < 30
                    && ARGB.red(depth) < ARGB.green(depth) && ARGB.green(depth) < ARGB.blue(depth) && ARGB.blue(depth) < 250
                    && (boolean)result.get("zeroDistanceIdentity") && ARGB.green(gradient) == 255
                    && underwaterZero && underwaterBounds && underwaterMonotonic && underwaterComposition;
                result.put("passed",passed);opticsProbe=result;
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/water-optics-probe.png"));
            } catch(Exception ex) { throw new RuntimeException(ex); }
            finally { image.close();target.destroyBuffers(); }
        });
    }
    private void captureColor(String label,TextureTarget input) {
        var preview=new TextureTarget(label,raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label,preview.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.HDR_DEBUG));
            pass.setUniform("HdrInput",input.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(preview,image -> {
            try { image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+".png")); }
            catch(Exception e) { throw new RuntimeException(e); }
            finally { image.close();preview.destroyBuffers(); }
        });
    }
    public Object evidence() {
        var data=new LinkedHashMap<String,Object>();data.put("reflectionFormat","RGBA16_FLOAT scene-linear");
        data.put("width",raw == null ? 0 : raw.width);data.put("height",raw == null ? 0 : raw.height);
        long reflectionBytes=raw == null ? 0 : (long)raw.width*raw.height*48;
        long pyramidBytes=Arrays.stream(radiancePyramid).filter(Objects::nonNull).mapToLong(t -> (long)t.width*t.height*8).sum();
        long planarBytes=planar.target() == null ? 0 : (long)planar.target().width*planar.target().height*12;
        data.put("textureBytes",reflectionBytes+pyramidBytes+planarBytes);data.put("reflectionTextureBytes",reflectionBytes);data.put("radiancePyramidBytes",pyramidBytes);data.put("planarTextureBytes",planarBytes);
        data.put("reflectionResolutionPercent",verificationResolution == null ? configuredResolution : verificationResolution);
        data.put("radiancePyramidLevels",radiancePyramid.length);data.put("previousReflectionAblation",previousReflection);
        data.put("frames",frames);data.put("historyResets",historyResets);data.put("resetEvents",new LinkedHashMap<>(resetEvents));
        data.put("lastResetReasons",lastResets);data.put("probes",new LinkedHashMap<>(probes));
        data.put("opticsProbe",opticsProbe);
        data.put("waveProbe",waveProbe);
        data.put("edgeProbes",new LinkedHashMap<>(edgeProbes));
        data.put("contactProbes",new LinkedHashMap<>(contactProbes));
        data.put("nativeMesh",WaterMeshMetadata.evidence());
        data.put("planar",planar.evidence());
        data.put("sourceProbes",new LinkedHashMap<>(sourceProbes));
        data.put("gpuSamples",timings.size());
        if(!timings.isEmpty()) { var sorted=timings.stream().mapToDouble(Double::doubleValue).sorted().toArray();data.put("gpuMedianMs",sorted[sorted.length/2]);data.put("gpuP95Ms",sorted[Math.min(sorted.length-1,(int)(sorted.length*.95))]); }
        var cases=new LinkedHashMap<String,Object>();
        caseTimings.forEach((name,values) -> { var sorted=values.stream().mapToDouble(Double::doubleValue).sorted().toArray();cases.put(name,Map.of("samples",sorted.length,"medianMs",sorted[sorted.length/2],"p95Ms",sorted[Math.min(sorted.length-1,(int)(sorted.length*.95))])); });
        data.put("gpuCases",cases);
        return data;
    }
    private void releaseRadiance() {
        for(int i=0;i<radiancePyramid.length;i++) if(radiancePyramid[i] != null) { radiancePyramid[i].destroyBuffers();radiancePyramid[i]=null; }
        opaqueWidth=opaqueHeight=0;
    }
    private void releaseTargets() {
        if(raw != null) { raw.destroyBuffers();raw=null; }
        for(int i=0;i<2;i++) { if(surfaces[i] != null) { surfaces[i].destroyBuffers();surfaces[i]=null; } if(reflections[i] != null) { reflections[i].destroyBuffers();reflections[i]=null; } }
    }
    @Override public void close() {
        releaseTargets();releaseRadiance();planar.close();camera=null;previousCamera=null;dimension=null;atlasSource=null;temporal=null;scene=null;
        for(int i=0;i<queries.length;i++) if(queries[i] != null) { queries[i].close();queries[i]=null; }
        resetHistory("renderer-close");
    }
}
