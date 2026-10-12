package dev.colin.aster;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.ByteBuffer;
import java.util.Map;

/** Nearby native fluid occupancy; 64 x 16 x 64 cells in a nearest-sampled atlas. */
public final class AsterWaterVolume implements AutoCloseable {
    private static final int[][] SURFACE_COLUMNS={{0,0},{-8,0},{8,0},{0,-8},{0,8},{-8,-8},{8,-8},{-8,8},{8,8}};
    private GpuTexture texture;
    private GpuTextureView view;
    private Object level;
    private int ox,oy,oz,rebuilds,wetCells;
    private long updated;
    private double lastMs,maxMs;
    private int farWetCells,farColumnsChecked;
    private boolean verificationDisabled;
    private int verticalAnchor;
    private String heightSource="cameraFallback";
    public void setVerificationDisabled(boolean value) { if(AutomatedVerification.HIDDEN) verificationDisabled=value; }
    private int lightingHeight(BlockPos camera) {
        var world=Minecraft.getInstance().level;
        var cursor=new BlockPos.MutableBlockPos().set(camera);
        // Underwater receivers belong to the connected water above the eye,
        // including covered pools whose heightmap points to a roof.
        if(world.hasChunkAt(cursor) && world.getFluidState(cursor).is(FluidTags.WATER)) {
            for(int step=0;step<64;step++) {
                cursor.move(0,1,0);
                if(!world.getFluidState(cursor).is(FluidTags.WATER)) {
                    heightSource="connectedSurface";return cursor.getY()-1;
                }
            }
        }
        // Keep the native wet cache around the lake rather than around an
        // elevated eye. The former camera-centred slab was entirely above
        // visible water when flying, removing its bed lighting at every range.
        for(int[] offset:SURFACE_COLUMNS) {
            cursor.set(camera.getX()+offset[0],camera.getY(),camera.getZ()+offset[1]);
            if(!world.hasChunkAt(cursor)) continue;
            int top=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,cursor.getX(),cursor.getZ())-1;
            if(top>camera.getY()) continue; // do not replace a cave with a lake above it
            cursor.setY(top);
            if(world.getFluidState(cursor).is(FluidTags.WATER)) {
                heightSource="openSurface";return top;
            }
        }
        heightSource="cameraFallback";return camera.getY();
    }
    public void update(BlockPos camera) {
        var world=Minecraft.getInstance().level;
        verticalAnchor=lightingHeight(camera);
        int x=Math.floorDiv(camera.getX(),4)*4-32,y=Math.floorDiv(verticalAnchor,4)*4-8,z=Math.floorDiv(camera.getZ(),4)*4-32;
        long now=System.nanoTime();
        if(view != null && level == world && ox == x && oy == y && oz == z && now-updated < 500_000_000L) return;
        ox=x;oy=y;oz=z;level=world;updated=now;wetCells=0;
        var device=RenderSystem.getDevice();
        if(texture == null) {
            texture=device.createTexture("Aster native water occupancy",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,1024,64,1,1);
            view=device.createTextureView(texture);
        }
        try(var image=new NativeImage(1024,64,false)) {
            image.fillRect(0,0,1024,64,0);
            var pos=new BlockPos.MutableBlockPos();
            for(int dz=0;dz<64;dz++) for(int dx=0;dx<64;dx++) {
                pos.set(ox+dx,oy,oz+dz);
                if(!world.hasChunkAt(pos)) continue;
                for(int dy=0;dy<16;dy++) {
                    pos.set(ox+dx,oy+dy,oz+dz);
                    var fluid=world.getFluidState(pos);
                    if(!fluid.is(FluidTags.WATER)) continue;
                    int height=Math.clamp(Math.round(fluid.getHeight(world,pos)*254)+1,1,255);
                    image.setPixel(dx+dy*64,dz,ARGB.color(height,0,0,0));wetCells++;
                }
            }
            // Unused RGB in layer 15 carries verified 4x4 open-water cells
            // across 256 blocks. Alpha retains its fine voxel meaning. All
            // 16 native columns must share a source surface and contain water
            // down to the floor; mixed shores and unknown geometry stay dry.
            farWetCells=0;farColumnsChecked=0;
            for(int dz=0;dz<64;dz++) for(int dx=0;dx<64;dx++) {
                int bx=ox-96+dx*4,bz=oz-96+dz*4,maxFloor=Integer.MIN_VALUE,topBlock=0;
                double surface=Double.NaN;boolean valid=true;
                columns: for(int fz=0;fz<4;fz++) for(int fx=0;fx<4;fx++) {
                    pos.set(bx+fx,verticalAnchor,bz+fz);
                    if(!world.hasChunkAt(pos)) {valid=false;break columns;}
                    int top=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())-1;
                    pos.setY(top);var fluid=world.getFluidState(pos);farColumnsChecked++;
                    if(!fluid.is(FluidTags.WATER) || !fluid.isSource()) {valid=false;break columns;}
                    double nativeSurface=top+fluid.getHeight(world,pos);
                    if(Double.isFinite(surface) && (top!=topBlock || Math.abs(nativeSurface-surface)>.004)) {valid=false;break columns;}
                    surface=nativeSurface;topBlock=top;
                    // OCEAN_FLOOR is not a client-transmitted heightmap. Walk
                    // the bounded native column instead of trusting its default.
                    int floor=top+1;boolean connectedFloor=false;
                    for(int step=0;step<8;step++) {
                        pos.setY(top-step);
                        if(!world.getFluidState(pos).is(FluidTags.WATER)) {connectedFloor=true;break;}
                        floor=top-step;
                    }
                    if(!connectedFloor) {valid=false;break columns;}
                    maxFloor=Math.max(maxFloor,floor);
                }
                if(valid) {
                    int encoded=(int)Math.round((surface+64)*128);
                    if(encoded>=0 && encoded<=65535) {
                        int prior=image.getPixel(dx+15*64,dz);
                        image.setPixel(dx+15*64,dz,ARGB.color(ARGB.alpha(prior),topBlock-maxFloor+1,encoded>>>8,encoded&255));
                        farWetCells++;
                    }
                }
            }
            device.createCommandEncoder().writeToTexture(texture,image,0,0,0,0);
        }
        rebuilds++;lastMs=(System.nanoTime()-now)/1_000_000.0;maxMs=Math.max(maxMs,lastMs);
    }
    private int receiverDiagnostic;
    public void setReceiverDiagnostic(int mode) {
        if((AutomatedVerification.HIDDEN || Boolean.getBoolean("watercompare.hidden")) && mode>=0 && mode<=3) receiverDiagnostic=mode;
    }
    public void writeUniform(ByteBuffer data) { data.putFloat(ox).putFloat(oy).putFloat(oz).putFloat(verificationDisabled ? 0 : 1+receiverDiagnostic); }
    public void bind(RenderPass pass) { if(view != null) pass.setUniform("WaterVolume",view,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)); }
    public Object evidence() {
        var result=new java.util.LinkedHashMap<String,Object>();
        result.putAll(Map.of("dimensions",java.util.List.of(64,16,64),"textureBytes",262144,"rebuilds",rebuilds,"wetCells",wetCells,"lastRebuildMs",lastMs,"maxRebuildMs",maxMs,"lightingEnabled",!verificationDisabled,"nativeFluidOccupancy",true));
        result.put("origin",java.util.List.of(ox,oy,oz));result.put("verticalAnchor",verticalAnchor);result.put("heightSource",heightSource);
        result.put("receiverDiagnostic",receiverDiagnostic);
        result.put("farOpenWaterExtentBlocks",256);result.put("farOpenWaterCellBlocks",4);
        result.put("farVerifiedWetCells",farWetCells);result.put("farNativeColumnsChecked",farColumnsChecked);
        result.put("farMapStorage","Unused RGB in occupancy layer 15; no extra texture allocation");
        return result;
    }
    @Override public void close() { if(view != null) { view.close();view=null; } if(texture != null) {texture.close();texture=null;} level=null;updated=0; }
}
