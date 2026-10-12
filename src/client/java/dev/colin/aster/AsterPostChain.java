package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.colin.aster.mixin.ChunkSectionsAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Linear floating-point lighting/atmosphere and SDR output after the HDR world. */
public final class AsterPostChain implements AutoCloseable {
    private TextureTarget hdr, materials, underwaterShafts, temporalMotion;
    private final List<TextureTarget> pyramid = new ArrayList<>();
    private final TextureTarget[] exposures = new TextureTarget[2];
    private int exposureIndex, materialPasses, historyResets, shaftPasses, shaftPassesSkippedAboveWater;
    private boolean shaftPassRendered;
    private boolean reset = true;
    private int radianceProbe = -1, emissionProbe = -1, exposureProbe = -1;

    public void ensureSize(int width, int height) {
        if (hdr != null && hdr.width == width && hdr.height == height) return;
        close();
        hdr = new TextureTarget("Aster linear HDR",width,height,GpuFormat.RGBA16_FLOAT,null);
        materials = new TextureTarget("Aster terrain materials",width,height,GpuFormat.RGBA8_UNORM,null);
        temporalMotion = new TextureTarget("Aster terrain previous displacement",width,height,GpuFormat.RGBA16_FLOAT,null);
        underwaterShafts = new TextureTarget("Aster underwater sunlight shafts",Math.max(1,(width+1)/2),Math.max(1,(height+1)/2),GpuFormat.RGBA16_FLOAT,null);
        int w = width, h = height;
        do {
            w = Math.max(1,w/2); h = Math.max(1,h/2);
            pyramid.add(new TextureTarget("Aster bloom/luminance "+pyramid.size(),w,h,GpuFormat.RGBA16_FLOAT,null));
        } while (w > 1 || h > 1);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int i=0;i<2;i++) {
            exposures[i] = new TextureTarget("Aster exposure "+i,1,1,GpuFormat.RG16_FLOAT,null);
            encoder.clearColorTexture(exposures[i].getColorTexture(),new Vector4f(1,0.14f,0,0));
        }
        resetHistory();
    }
    public void resetHistory() { reset = true; }
    public boolean needsReset() { return reset; }

    public void render(ChunkSectionsToRender chunks, TextureTarget sceneCopy, TextureTarget[] shadows, GpuQueryPool query, AsterFog fog) {
        var mc = Minecraft.getInstance();
        var main = mc.gameRenderer.mainRenderTarget();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        boolean taa=AsterRenderer.INSTANCE.temporalAa.enabled();
        var solid=AsterRenderer.INSTANCE.solidReflections;
        boolean reflections=solid.enabled();
        var materialPass=RenderPassDescriptor.builder(() -> "Aster terrain material and motion data")
            .withColorAttachment(materials.getColorTextureView(),Optional.of(new Vector4f(0)))
            .withDepthAttachment(main.getDepthTextureView());
        if(taa || reflections) materialPass.withColorAttachment(temporalMotion.getColorTextureView(),Optional.of(new Vector4f(0,0,0,1)));
        if(reflections)materialPass.withColorAttachment(solid.geometry().getColorTextureView(),Optional.of(new Vector4f(0)))
            .withColorAttachment(solid.response().getColorTextureView(),Optional.of(new Vector4f(0)));
        try (var pass = encoder.createRenderPass(materialPass.build())) {
            RenderSystem.bindDefaultUniforms(pass);
            if(reflections)AsterRenderer.INSTANCE.bindScene(pass);
            var atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
            ((ChunkSectionsAccessor)chunks).aster$renderLayers(ChunkSectionLayerGroup.OPAQUE.layers(),nearest,pass,atlas,mc.gameRenderer.lightmap(),
                reflections?AsterPipelines.MATERIAL_REFLECTION:taa?AsterPipelines.MATERIAL_TEMPORAL:AsterPipelines.MATERIAL,
                reflections?AsterPipelines.MATERIAL_REFLECTION_INDIRECT:taa?AsterPipelines.MATERIAL_TEMPORAL_INDIRECT:AsterPipelines.MATERIAL_INDIRECT);
        }
        materialPasses++;
        encoder.writeTimestamp(query,3);
        fog.render(main,materials,shadows);
        var reflectedScene=solid.render(sceneCopy,main,temporalMotion);
        // The composite reads this target only for an underwater camera. Each
        // underwater entry writes every pixel before use, so above-water frames
        // need neither an empty fullscreen draw nor a clear of this texture.
        shaftPassRendered=AsterRenderer.INSTANCE.underwaterCamera();
        if(shaftPassRendered) {
          try(var pass=encoder.createRenderPass(() -> "Aster half-resolution underwater sunlight",underwaterShafts.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.UNDERWATER_SHAFT_TRACE));
            AsterRenderer.INSTANCE.bindUnderwaterFocusedScatter(pass);
            AsterRenderer.INSTANCE.bindScene(pass);
            pass.setUniform("SceneDepth",main.getDepthTextureView(),nearest);
            for(int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),nearest);
            pass.draw(3,1,0,0);
          }
          shaftPasses++;
        } else shaftPassesSkippedAboveWater++;
        try (var pass = encoder.createRenderPass(() -> "Aster linear lighting and atmosphere",hdr.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.COMPOSITE));
            AsterRenderer.INSTANCE.bindUnderwaterFocusedScatter(pass);
            AsterRenderer.INSTANCE.bindScene(pass);
            pass.setUniform("SceneColor",reflectedScene.getColorTextureView(),linear);
            pass.setUniform("SceneDepth",main.getDepthTextureView(),nearest);
            pass.setUniform("MaterialData",materials.getColorTextureView(),nearest);
            pass.setUniform("UnderwaterShafts",underwaterShafts.getColorTextureView(),nearest);
            for (int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        var resolved=AsterRenderer.INSTANCE.temporalAa.resolve(hdr,main,temporalMotion,
            AsterRenderer.INSTANCE.uploadedWindTime(),AsterRenderer.INSTANCE.rainAmount());
        var previous = resolved;
        for (int i=0;i<pyramid.size();i++) {
            var target = pyramid.get(i);
            try (var pass = encoder.createRenderPass(() -> "Aster bloom/luminance reduction",target.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(i == 0 ? (taa?AsterPipelines.DOWNSAMPLE_TAA:AsterPipelines.DOWNSAMPLE_FIRST) : AsterPipelines.DOWNSAMPLE));
                AsterRenderer.INSTANCE.bindScene(pass);
                pass.setUniform("HdrInput",previous.getColorTextureView(),linear);
                pass.draw(3,1,0,0);
            }
            previous = target;
        }
        int next = 1-exposureIndex;
        try (var pass = encoder.createRenderPass(() -> "Aster adaptive exposure",exposures[next].getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.EXPOSURE));
            AsterRenderer.INSTANCE.bindScene(pass);
            pass.setUniform("Luminance",pyramid.getLast().getColorTextureView(),nearest);
            pass.setUniform("PreviousExposure",exposures[exposureIndex].getColorTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        exposureIndex = next;
        if (reset) historyResets++;
        reset = false;
        encoder.writeTimestamp(query,4);
        try (var pass = encoder.createRenderPass(() -> "Aster filmic SDR output",WorldScene.outputColor(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(taa?AsterPipelines.TONEMAP_TAA:AsterPipelines.TONEMAP));
            AsterRenderer.INSTANCE.bindScene(pass);
            pass.setUniform("HdrInput",resolved.getColorTextureView(),linear);
            pass.setUniform("Exposure",exposures[exposureIndex].getColorTextureView(),nearest);
            for (int i=0;i<4;i++) pass.setUniform("Bloom"+i,pyramid.get(Math.min(i+1,pyramid.size()-1)).getColorTextureView(),linear);
            pass.draw(3,1,0,0);
        }
    }

    public void captureProbe() {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var probe = new TextureTarget("Aster HDR acceptance probe",1,1,GpuFormat.RGBA8_UNORM,null);
        try (var pass = encoder.createRenderPass(() -> "Aster HDR acceptance probe",probe.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.HDR_PROBE));
            AsterRenderer.INSTANCE.bindScene(pass);
            // Probe the actual world target, before atmosphere/bloom/tone mapping.
            pass.setUniform("HdrInput",WorldScene.target().getColorTextureView(),nearest);
            pass.setUniform("Exposure",exposures[exposureIndex].getColorTextureView(),nearest);
            pass.setUniform("MaterialData",materials.getColorTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(probe,image -> {
            try {
                int pixel = image.getPixel(0,0);
                radianceProbe = ARGB.red(pixel); emissionProbe = ARGB.green(pixel); exposureProbe = ARGB.blue(pixel);
                AsterClient.LOGGER.info("HDR probe: radiance={}, emission={}, exposureByte={}",radianceProbe,emissionProbe,exposureProbe);
            } finally { image.close(); probe.destroyBuffers(); }
        });
        captureMaterials();
    }
    public void captureUnderwaterReceiverProbe(String label) {
        if(!AutomatedVerification.HIDDEN) return;
        // Verification runs after WorldScene.end(), when mainRenderTarget()
        // returns the display target. Its depth is not the rendered world.
        var main=WorldScene.target();
        if(main==null) throw new IllegalStateException("Receiver diagnostic requires rendered world depth");
        var target=new TextureTarget("Aster native wet receiver/replay-mask diagnostic",main.width,main.height,GpuFormat.RGBA8_UNORM,null);
        var metadata=AsterRenderer.INSTANCE.sceneEvidence();
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Aster native wet receiver/replay-mask diagnostic",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_RECEIVER_PROBE));
            AsterRenderer.INSTANCE.bindUnderwaterReceiverProbe(pass);
            var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("SceneDepth",main.getDepthTextureView(),nearest);
            pass.setUniform("MaterialData",materials.getColorTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image->{
            try {
                int eligible=0,masked=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int pixel=image.getPixel(x,y);
                    if(ARGB.red(pixel)>127) {eligible++;if(ARGB.green(pixel)>127) masked++;}
                }
                var result=new LinkedHashMap<String,Object>();
                result.put("eligibleWetPixels",eligible);result.put("replayMaskedWetPixels",masked);
                result.put("wetPixelsExcludedByReplayMask",eligible-masked);
                result.put("replayCoverageFraction",eligible>0 ? (double)masked/eligible:null);
                result.put("sceneDepthSource","Aster scene-linear world depth after the completed world pass");
                result.put("nativeScene",metadata);
                result.put("scope","Actual native scene-depth and connected-water reconstruction versus terrain replay alpha; excludes foreground water surfaces. A diagnostic, not a quality score or proof of final radiance.");
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                image.writeToFile(folder.resolve(label+"-receiver-mask.png"));
                java.nio.file.Files.writeString(folder.resolve(label+"-receiver-mask.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            } catch(Exception ex) {throw new RuntimeException(ex);} finally {image.close();target.destroyBuffers();}
        });
        captureUnderwaterReplayProbe(label);
        captureUnderwaterReceiverProvenance(label);
        if(label.equals("lighting-wet-look-up")) Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(),image->{
            try {image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence").resolve(label+"-view.png"));}
            catch(Exception ex) {throw new RuntimeException(ex);} finally {image.close();}
        });
    }
    private void captureUnderwaterReceiverProvenance(String label) {
        var world=WorldScene.target();
        var target=new TextureTarget("Aster wet endpoint depth provenance",world.width,world.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Aster wet endpoint depth provenance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_RECEIVER_PROVENANCE_PROBE));
            AsterRenderer.INSTANCE.bindUnderwaterReceiverProbe(pass);
            var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("SceneDepth",world.getDepthTextureView(),nearest);
            pass.setUniform("MaterialData",materials.getColorTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image->{
            try {
                int candidates=0,opaque=0,translucentWater=0,translucentOther=0,waterPixels=0,translucentWaterPixels=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int pixel=image.getPixel(x,y);
                    if(ARGB.blue(pixel)>127) {
                        waterPixels++;
                        if(ARGB.green(pixel)<=127) translucentWaterPixels++;
                    }
                    if(ARGB.red(pixel)>127) {
                        candidates++;
                        if(ARGB.green(pixel)>127) opaque++;
                        else if(ARGB.blue(pixel)>127) translucentWater++;
                        else translucentOther++;
                    }
                }
                var result=new LinkedHashMap<String,Object>();
                result.put("wetCandidates",candidates);result.put("visibleOpaqueWetPixels",opaque);
                result.put("translucentDepthWetPixels",candidates-opaque);
                result.put("translucentDepthWetPixelsWithWaterSurface",translucentWater);
                result.put("translucentDepthWetPixelsWithoutWaterSurface",translucentOther);
                result.put("waterSurfacePixels",waterPixels);
                result.put("translucentDepthWaterSurfacePixels",translucentWaterPixels);
                result.put("scope","Actual completed world D32 versus the snapshot before native translucency. RGB encodes wet receiver candidate, identical opaque endpoint, and water-surface presence. Green/blue retain all visible pixels, including rejected receivers, to qualify upward foreground coverage. Diagnostic only, not visual quality.");
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                image.writeToFile(folder.resolve(label+"-depth-provenance.png"));
                java.nio.file.Files.writeString(folder.resolve(label+"-depth-provenance.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            } catch(Exception ex) {throw new RuntimeException(ex);} finally {image.close();target.destroyBuffers();}
        });
    }
    private void captureUnderwaterReplayProbe(String label) {
        var world=WorldScene.target();
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var empty=new TextureTarget("Aster cleared replay control",1,1,GpuFormat.RGBA8_UNORM,null);
        var ablated=new TextureTarget("Aster replay independence HDR",world.width,world.height,GpuFormat.RGBA16_FLOAT,null);
        var difference=new TextureTarget("Aster replay independence error",world.width,world.height,GpuFormat.RGBA8_UNORM,null);
        encoder.clearColorTexture(empty.getColorTexture(),new Vector4f(0));
        // Execute the production composite again with identical scene/depth,
        // lighting and optics, replacing only terrain replay with zero coverage.
        try(var pass=encoder.createRenderPass(()->"Aster cleared replay production composite",ablated.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.COMPOSITE));
            AsterRenderer.INSTANCE.bindUnderwaterFocusedScatter(pass);
            AsterRenderer.INSTANCE.bindUnderwaterReplayProbe(pass);
            pass.setUniform("SceneColor",world.getColorTextureView(),linear);
            pass.setUniform("SceneDepth",world.getDepthTextureView(),nearest);
            pass.setUniform("MaterialData",empty.getColorTextureView(),nearest);
            pass.setUniform("UnderwaterShafts",underwaterShafts.getColorTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        try(var pass=encoder.createRenderPass(()->"Aster replay independence HDR difference",difference.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_REPLAY_PROBE));
            pass.setUniform("ActualHdr",hdr.getColorTextureView(),nearest);
            pass.setUniform("ClearedReplayHdr",ablated.getColorTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(difference,image->{
            try {
                int changed=0,maxDifference=0,aboveOne=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int pixel=image.getPixel(x,y);
                    if(ARGB.red(pixel)>127) changed++;
                    maxDifference=Math.max(maxDifference,ARGB.green(pixel));
                    if(ARGB.blue(pixel)>127) aboveOne++;
                }
                var result=new LinkedHashMap<String,Object>();
                result.put("passed",changed==0);result.put("changedPixels",changed);
                result.put("absoluteHdrErrorThreshold",.0001);
                result.put("maxEncodedDifference",maxDifference);
                result.put("actualHdrPixelsAboveOne",aboveOne);
                result.put("comparedPixels",image.getWidth()*image.getHeight());
                result.put("scope","Same-frame production RGBA16F composite versus identical inputs with terrain replay coverage cleared. Error computed on GPU before SDR screenshot conversion; this is a dependency regression, not a quality score.");
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                image.writeToFile(folder.resolve(label+"-replay-error.png"));
                java.nio.file.Files.writeString(folder.resolve(label+"-replay-error.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            } catch(Exception ex) {throw new RuntimeException(ex);} finally {
                image.close();difference.destroyBuffers();ablated.destroyBuffers();empty.destroyBuffers();
            }
        });
    }
    public void captureMaterials() {
        Screenshot.takeScreenshot(materials,image -> {
            try { image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/material-data.png")); }
            catch (Exception ex) { throw new RuntimeException(ex); }
            finally { image.close(); }
        });
    }
    public Object evidence() {
        var report = new LinkedHashMap<String,Object>();
        report.put("format","RGBA16_FLOAT"); report.put("nativeWorldInput","RGBA16_FLOAT scene-linear world");
        report.put("temporalAa",AsterRenderer.INSTANCE.temporalAa.evidence());
        report.put("solidReflections",AsterRenderer.INSTANCE.solidReflections.evidence());
        report.put("underwaterShaftPasses",shaftPasses);
        report.put("underwaterShaftPassRendered",shaftPassRendered);
        report.put("underwaterShaftPassesSkippedAboveWater",shaftPassesSkippedAboveWater);
        if(underwaterShafts!=null) report.put("underwaterShafts",java.util.Map.of("width",underwaterShafts.width,"height",underwaterShafts.height,
            "textureBytes",(long)underwaterShafts.width*underwaterShafts.height*8,"raySegments",16,"traceRangeBlocks",48,
            "reconstruction","depth-aware bilinear; conservative endpoint; exact-ray fallback at rejected silhouettes","temporalHistory",false));
        report.put("bloomLevels",pyramid.size()); report.put("materialPasses",materialPasses); report.put("historyResets",historyResets);
        report.put("probeCaptured",radianceProbe >= 0);
        report.put("radianceProbeSource","scene-linear world before post-processing");
        report.put("radianceAboveOne",radianceProbe < 0 ? null : radianceProbe > 192);
        report.put("emissiveMaterialDetected",emissionProbe < 0 ? null : emissionProbe > 192);
        report.put("probeExposure",exposureProbe < 0 ? null : exposureProbe*4.0/255.0);
        report.put("probePassed",radianceProbe < 0 ? null : radianceProbe > 192 && emissionProbe > 192 && exposureProbe >= 38 && exposureProbe <= 128);
        return report;
    }
    @Override public void close() {
        if (hdr != null) { hdr.destroyBuffers(); hdr = null; }
        if (materials != null) { materials.destroyBuffers(); materials = null; }
        if (temporalMotion != null) { temporalMotion.destroyBuffers(); temporalMotion = null; }
        if (underwaterShafts != null) { underwaterShafts.destroyBuffers(); underwaterShafts = null; }
        for (var target : pyramid) target.destroyBuffers();
        pyramid.clear();
        for (int i=0;i<2;i++) if (exposures[i] != null) { exposures[i].destroyBuffers(); exposures[i] = null; }
        exposureIndex = 0;
        reset = true;
    }
}
