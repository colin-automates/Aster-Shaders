package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.util.ARGB;
import java.util.Optional;
import org.joml.Vector4f;

/** One complete cloud environment per frame, shared by the sky and every reflection. */
public final class AsterSky implements AutoCloseable {
    private TextureTarget clouds;
    private TextureTarget cloudShadow;
    private GpuTexture noise;
    private GpuTextureView noiseView;
    private int width;
    private int height;
    private int renderedFrames;
    public void ensure() {
        if(noise == null) createNoise();
        if(cloudShadow == null) {
            cloudShadow=new TextureTarget("Aster cloud shadow field",256,256,GpuFormat.RGBA16_FLOAT,null);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(cloudShadow.getColorTexture(),new Vector4f(1));
        }
        boolean previous=(AsterRenderer.INSTANCE.verificationLightingFlags()&32768) != 0;
        int nextWidth=previous ? 1536 : 2048,nextHeight=previous ? 768 : 1024;
        if(clouds != null && width != nextWidth) {clouds.destroyBuffers();clouds=null;}
        if(clouds == null) {
            width=nextWidth;height=nextHeight;
            clouds=new TextureTarget("Aster volumetric cloud environment",width,height,GpuFormat.RGBA16_FLOAT,null);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(clouds.getColorTexture(),new Vector4f(0,0,0,1));
        }
    }
    public void render(GpuBufferSlice scene) {
        ensure();
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Aster curved volumetric cloud layers",clouds.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.SKY_CLOUDS));
            pass.setUniform("AsterScene",scene);
            pass.setUniform("CloudNoise",noiseView,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3,1,0,0);
        }
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Aster cloud shadow projection",cloudShadow.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.CLOUD_SHADOW));
            pass.setUniform("AsterScene",scene);
            pass.setUniform("CloudNoise",noiseView,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3,1,0,0);
        }
        renderedFrames++;
    }
    public void bind(RenderPass pass) {
        if(cloudShadow != null) pass.setUniform("CloudShadow",cloudShadow.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if(clouds != null) pass.setUniform("SkyClouds",clouds.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
    public Object evidence() {
        return java.util.Map.ofEntries(java.util.Map.entry("cloudEnvironmentWidth",width),
            java.util.Map.entry("cloudEnvironmentHeight",height),
            java.util.Map.entry("textureBytes",(long)width*height*8),
            java.util.Map.entry("renderedFrames",renderedFrames),
            java.util.Map.entry("cloudRaySteps",width == 1536 ? 18 : 128),
            java.util.Map.entry("sharedWithWater",true),
            java.util.Map.entry("noiseVolumeResolution",128),
            java.util.Map.entry("noiseTextureBytes",2080L*1040*4),
            java.util.Map.entry("cloudShadowResolution",256),
            java.util.Map.entry("cloudShadowRange",768),
            java.util.Map.entry("cloudShadowRaySteps",32));
    }
    private void createNoise() {
        // Padded slices emulate a repeatable 128-cubed value-noise texture.
        // The field is generated once, not rehashed at every marching/light tap.
        try(var image=new NativeImage(2080,1040,false)) {
            for(int z=0;z<128;z++) for(int y=-1;y<=128;y++) for(int x=-1;x<=128;x++) {
                int ix=Math.floorMod(x,128),iy=Math.floorMod(y,128);
                float a=fract(ix*.1031f),b=fract(iy*.1031f),c=fract(z*.1031f);
                float dot=a*(b+33.33f)+b*(c+33.33f)+c*(a+33.33f);
                a+=dot;b+=dot;c+=dot;
                int value=Math.round(fract((a+b)*c)*255);
                image.setPixel((z%16)*130+x+1,(z/16)*130+y+1,ARGB.color(255,value,value,value));
            }
            var device=RenderSystem.getDevice();
            noise=device.createTexture("Aster cached 3D cloud noise",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,2080,1040,1,1);
            device.createCommandEncoder().writeToTexture(noise,image,0,0,0,0);
            noiseView=device.createTextureView(noise);
        }
    }
    private static float fract(float v) {return v-(float)Math.floor(v);}
    public void close() {
        if(clouds != null) {clouds.destroyBuffers();clouds=null;}
        if(cloudShadow != null) {cloudShadow.destroyBuffers();cloudShadow=null;}
        if(noiseView != null) {noiseView.close();noiseView=null;}
        if(noise != null) {noise.close();noise=null;}
    }
}
