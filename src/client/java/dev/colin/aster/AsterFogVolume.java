package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;
import java.util.*;

/** Raster-only 64-slice volume, coefficient history, and associative prefix scan. */
public final class AsterFogVolume implements AutoCloseable {
    private TextureTarget raw;
    private final TextureTarget[] history=new TextureTarget[2],scan=new TextureTarget[2];
    private int index,width,height;
    private TextureTarget integrated;
    private final Map<String,Object> probes=new LinkedHashMap<>(),captures=new LinkedHashMap<>();
    public boolean ensure(int screenWidth,int screenHeight) {
        int w=Math.max(1,(screenWidth+11)/12),h=Math.max(1,(screenHeight+11)/12);
        if(raw != null && width == w && height == h) return false;
        close();width=w;height=h;
        raw=new TextureTarget("Aster fog volume coefficients",w*8,h*8,GpuFormat.RGBA16_FLOAT,null);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        for(int i=0;i<2;i++) {
            history[i]=new TextureTarget("Aster fog air history "+i,w*8,h*8,GpuFormat.RGBA16_FLOAT,null);
            scan[i]=new TextureTarget("Aster fog depth integration "+i,w*8,h*8,GpuFormat.RGBA16_FLOAT,null);
            encoder.clearColorTexture(history[i].getColorTexture(),new Vector4f());
        }
        integrated=scan[0];return true;
    }
    public int width() {return width;}
    public int height() {return height;}
    public void render(GpuBufferSlice scene,GpuBufferSlice temporal,GpuBufferSlice[] scanUniforms,TextureTarget[] shadows,AsterFogEnvironment environment) {
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        int old=index;index=1-index;
        try(var pass=encoder.createRenderPass(() -> "Aster world fog lighting",raw.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_VOLUME_LIGHT));
            pass.setUniform("AsterScene",scene);pass.setUniform("FogTemporal",temporal);environment.bind(pass);
            AsterRenderer.INSTANCE.bindLocalLighting(pass);AsterRenderer.INSTANCE.bindSky(pass);
            for(int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),nearest);
            pass.draw(3,1,0,0);
        }
        try(var pass=encoder.createRenderPass(() -> "Aster air-space fog history",history[index].getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_VOLUME_HISTORY));
            pass.setUniform("AsterScene",scene);pass.setUniform("FogTemporal",temporal);
            pass.setUniform("FogRawVolume",raw.getColorTextureView(),nearest);
            pass.setUniform("FogPreviousVolume",history[old].getColorTextureView(),linear);pass.draw(3,1,0,0);
        }
        TextureTarget input=history[index];
        for(int stage=0;stage<7;stage++) {
            TextureTarget output=scan[stage%2];
            try(var pass=encoder.createRenderPass(() -> "Aster fog associative depth integration",output.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_VOLUME_INTEGRATE));
                pass.setUniform("AsterScene",scene);pass.setUniform("FogTemporal",scanUniforms[stage]);
                pass.setUniform("FogScanInput",input.getColorTextureView(),nearest);pass.draw(3,1,0,0);
            }
            input=output;
        }
        integrated=input;
    }
    public void bind(RenderPass pass) {
        if(integrated == null) return;
        pass.setUniform("FogVolume",integrated.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
    public void capture(String label,GpuBufferSlice scene,GpuBufferSlice temporal,boolean colors) {
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var target=new TextureTarget("Aster volume history diagnostic",raw.width,raw.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster fog volume history diagnostic",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_VOLUME_HISTORY_DEBUG));
            pass.setUniform("AsterScene",scene);pass.setUniform("FogTemporal",temporal);
            pass.setUniform("FogRawVolume",raw.getColorTextureView(),nearest);
            pass.setUniform("FogPreviousVolume",history[1-index].getColorTextureView(),linear);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                long reused=0;double weight=0;
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int value=ARGB.green(image.getPixel(x,y));weight+=value/255.0;if(value > 25) reused++;
                }
                captures.put(label,Map.of("cells",(long)image.getWidth()*image.getHeight(),"reusedCells",reused,"meanReuseWeight",weight/(image.getWidth()*image.getHeight())));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-volume-history.png"));
            } catch(Exception e) {throw new RuntimeException(e);}
            finally {image.close();target.destroyBuffers();}
        });
        if(colors) {captureColor(label+"-volume-raw",raw);captureColor(label+"-volume-filtered",history[index]);}
    }
    private void captureColor(String label,TextureTarget input) {
        var preview=new TextureTarget(label,input.width,input.height,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label,preview.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_VOLUME_DEBUG));
            pass.setUniform("FogInput",input.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(preview,image -> {
            try {image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+".png"));}
            catch(Exception e) {throw new RuntimeException(e);}
            finally {image.close();preview.destroyBuffers();}
        });
    }
    public void capturePhysics(String label,GpuBufferSlice scene) {
        var target=new TextureTarget("Aster volume transport acceptance",1,1,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster volume transport acceptance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.FOG_VOLUME_PROBE));
            pass.setUniform("AsterScene",scene);bind(pass);
            pass.setUniform("FogCoefficients",history[index].getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image -> {
            try {
                int p=image.getPixel(0,0);
                probes.put(label,Map.of("energy",ARGB.red(p)>192,"boundedMonotone",ARGB.green(p)>192,"identity",ARGB.blue(p)>192,
                    "passed",ARGB.red(p)>192 && ARGB.green(p)>192 && ARGB.blue(p)>192));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-volume-probe.png"));
            } catch(Exception e) {throw new RuntimeException(e);}
            finally {image.close();target.destroyBuffers();}
        });
    }
    public Object evidence() {
        var result=new LinkedHashMap<String,Object>(Map.of("representation","64-slice camera-aligned raster atlas; world-space coefficients/history",
            "width",width,"height",height,"slices",64,"textureBytes",(long)width*height*64*8*5,
            "depthDistribution","quadratic","integration","associative front-to-back prefix scan",
            "edgeFallback",false,"localLighting","occlusion-aware native block-light field"));
        result.put("alphaStorage","optical depth; transmittance evaluated in float at lookup");
        result.put("probes",new LinkedHashMap<>(probes));result.put("captures",new LinkedHashMap<>(captures));return result;
    }
    public void close() {
        if(raw != null) raw.destroyBuffers();raw=null;integrated=null;
        for(int i=0;i<2;i++) {
            if(history[i] != null) history[i].destroyBuffers();history[i]=null;
            if(scan[i] != null) scan[i].destroyBuffers();scan[i]=null;
        }
    }
}
