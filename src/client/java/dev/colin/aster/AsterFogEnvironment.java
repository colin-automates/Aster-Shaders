package dev.colin.aster;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Incremental toroidal world-space skylight/blocklight/terrain cache. */
public final class AsterFogEnvironment implements AutoCloseable {
    private NativeImage air,ground;
    private GpuTexture airTexture,groundTexture;
    private GpuTextureView airView,groundView;
    private final int[] keysX=new int[1600],keysZ=new int[1600];
    private final List<int[]> order=new ArrayList<>();
    private Object level;
    private int centerX,centerZ,originY,cursor;
    private long samples,updates,cpuNanos;
    public AsterFogEnvironment() {
        for(int z=-20;z<20;z++) for(int x=-20;x<20;x++) order.add(new int[]{x,z});
        order.sort(Comparator.comparingInt(p -> p[0]*p[0]+p[1]*p[1]));
    }
    public void update(Vec3 camera) {
        long start=System.nanoTime();
        var world=Minecraft.getInstance().level;
        if(world == null) return;
        int cx=(int)Math.floor(camera.x/8),cz=(int)Math.floor(camera.z/8);
        int oy=(int)Math.floor(camera.y/16)*16-48;
        boolean reset=air == null || level != world || originY != oy;
        if(air == null) {
            air=new NativeImage(320,120,false);ground=new NativeImage(40,40,false);
            var device=RenderSystem.getDevice();int usage=GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING;
            airTexture=device.createTexture("Aster world air lighting",usage,GpuFormat.RGBA8_UNORM,320,120,1,1);
            groundTexture=device.createTexture("Aster world mist terrain",usage,GpuFormat.RGBA8_UNORM,40,40,1,1);
            airView=device.createTextureView(airTexture);groundView=device.createTextureView(groundTexture);
        }
        if(reset) {
            Arrays.fill(keysX,Integer.MIN_VALUE);Arrays.fill(keysZ,Integer.MIN_VALUE);cursor=0;
            for(int z=0;z<120;z++) for(int x=0;x<320;x++) air.setPixel(x,z,0);
            for(int z=0;z<40;z++) for(int x=0;x<40;x++) ground.setPixel(x,z,0);
        }
        if(reset || centerX != cx || centerZ != cz) {
            cursor=0;
            for(var offset:order) {
                int x=cx+offset[0],z=cz+offset[1],ix=Math.floorMod(x,40),iz=Math.floorMod(z,40),key=ix+iz*40;
                if(keysX[key] == x && keysZ[key] == z) continue;
                ground.setPixel(ix,iz,0);
                for(int y=0;y<24;y++) air.setPixel(ix+(y%8)*40,iz+(y/8)*40,0);
            }
        }
        level=world;centerX=cx;centerZ=cz;originY=oy;
        var position=new BlockPos.MutableBlockPos();
        // Near cells refresh every frame for local placement/removal; the rest
        // cycles in a bounded budget. Never force a missing chunk to load.
        for(int i=0;i<112;i++) {
            var offset=order.get(i < 16 ? i : (cursor++%order.size()));
            int x=cx+offset[0],z=cz+offset[1],wx=x*8+4,wz=z*8+4;
            int ix=Math.floorMod(x,40),iz=Math.floorMod(z,40),key=ix+iz*40;
            if(!world.hasChunk(wx>>4,wz>>4)) continue;
            int height=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,wx,wz);
            position.set(wx,height-1,wz);
            boolean water=world.getFluidState(position).is(FluidTags.WATER);
            int encodedHeight=Math.clamp(Math.round((height+64)*255f/512),0,255);
            ground.setPixel(ix,iz,ARGB.color(255,encodedHeight,water ? 255 : 0,0));
            for(int y=0;y<24;y++) {
                position.set(wx,oy+y*4+2,wz);
                int sky=world.getBrightness(LightLayer.SKY,position)*17;
                int block=world.getBrightness(LightLayer.BLOCK,position)*17;
                int solid=world.getBlockState(position).canOcclude() ? 255 : 0;
                air.setPixel(ix+(y%8)*40,iz+(y/8)*40,ARGB.color(255,sky,block,solid));samples++;
            }
            keysX[key]=x;keysZ[key]=z;
        }
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(airTexture,air,0,0,0,0);encoder.writeToTexture(groundTexture,ground,0,0,0,0);
        updates++;cpuNanos+=System.nanoTime()-start;
    }
    public float centerX() {return centerX*8+4;}
    public float centerZ() {return centerZ*8+4;}
    public float originY() {return originY;}
    public void bind(RenderPass pass) {
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        pass.setUniform("FogEnvironment",airView,nearest);pass.setUniform("FogTerrain",groundView,nearest);
    }
    public Object evidence() {
        return Map.of("airCells",38400,"terrainCells",1600,"queries",samples,"updates",updates,
            "cpuMeanMs",updates == 0 ? 0 : cpuNanos/(updates*1_000_000.0),"lightSource","native propagated sky/block light; warm block-light radiance approximation");
    }
    public void close() {
        if(airView != null) airView.close();if(groundView != null) groundView.close();
        if(airTexture != null) airTexture.close();if(groundTexture != null) groundTexture.close();
        if(air != null) air.close();if(ground != null) ground.close();
        air=null;ground=null;airView=null;groundView=null;airTexture=null;groundTexture=null;level=null;
    }
}
