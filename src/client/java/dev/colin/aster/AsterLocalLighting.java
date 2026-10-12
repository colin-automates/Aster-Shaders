package dev.colin.aster;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Bounded RGB light diffusion in native loaded cells. This is not traced GI. */
public final class AsterLocalLighting implements AutoCloseable {
    private static final int X=48,Y=40,Z=48,N=X*Y*Z,BUDGET=N/4;
    private final byte[] occupancy=new byte[N];
    private final int[] rgb=new int[N*3],queue=new int[N];
    private final boolean[] queued=new boolean[N];
    private final IdentityHashMap<BlockState,int[]> emitters=new IdentityHashMap<>();
    private record Light(float x,float y,float z,float radius,float r,float g,float b,boolean solid,double distance) {}
    private final PriorityQueue<Light> nearest=new PriorityQueue<>(Comparator.comparingDouble(Light::distance).reversed());
    private List<Light> publishedLights=List.of();
    private NativeImage image;
    private GpuTexture texture;
    private GpuTextureView view;
    private GpuBufferSlice uniforms;
    private Object level;
    private int scanX,scanY,scanZ,publishedX,publishedY,publishedZ,cursor,head,tail,count;
    private boolean published;
    private long queries,updates,publications,cpuNanos,propagationNanos;
    private int sources,publishedSources,litCells;
    private static int index(int x,int y,int z){return x+X*(z+Z*y);}

    public void update(Vec3 camera,boolean enabled) {
        long start=System.nanoTime();var mc=Minecraft.getInstance();var world=mc.level;
        if(world==null)return;
        if(image==null){
            image=new NativeImage(X*8,Z*(Y/8),false);
            for(int z=0;z<image.getHeight();z++)for(int x=0;x<image.getWidth();x++)image.setPixel(x,z,0);
            texture=RenderSystem.getDevice().createTexture("Aster local RGB diffusion",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,X*8,Z*(Y/8),1,1);
            view=RenderSystem.getDevice().createTextureView(texture);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture,image,0,0,0,0);
        }
        if(!enabled) {
            // Terrain retains its bind layout while F8 disables the effect.
            // Transient uniforms expire every frame, so bind a fresh neutral
            // slice instead of carrying yesterday's GPU allocation forward.
            uploadUniforms(camera,false);updates++;cpuNanos+=System.nanoTime()-start;return;
        }
        // Margin exceeds the maximum 15-cell light reach on all sides, even
        // between snapped origins. A nearby source cannot fall off the cache.
        int ox=Math.floorDiv((int)Math.floor(camera.x),4)*4-24;
        int oy=Math.floorDiv((int)Math.floor(camera.y),4)*4-20;
        int oz=Math.floorDiv((int)Math.floor(camera.z),4)*4-24;
        if(level!=world){level=world;published=false;cursor=0;emitters.clear();}
        if(cursor==0 || scanX!=ox || scanY!=oy || scanZ!=oz){
            scanX=ox;scanY=oy;scanZ=oz;cursor=0;sources=0;
            Arrays.fill(rgb,0);Arrays.fill(queued,false);head=tail=count=0;nearest.clear();
        }
        var pos=new BlockPos.MutableBlockPos();
        int end=Math.min(N,cursor+BUDGET);
        for(;cursor<end;cursor++){
            int x=cursor%X,z=(cursor/X)%Z,y=cursor/(X*Z);
            pos.set(scanX+x,scanY+y,scanZ+z);
            if(!world.hasChunk(pos.getX()>>4,pos.getZ()>>4)){occupancy[cursor]=0;continue;}
            var state=world.getBlockState(pos);queries++;
            occupancy[cursor]=(byte)(state.canOcclude()?1:2);
            int[] color=emitters.computeIfAbsent(state,AsterLocalLighting::emission);
            if(color[3]>0){
                int offset=cursor*3;
                for(int c=0;c<3;c++)rgb[offset+c]=color[c];
                float lx=pos.getX()+.5f,ly=pos.getY()+.5f,lz=pos.getZ()+.5f;
                double distance=camera.distanceToSqr(lx,ly,lz);
                if(nearest.size()<32 || distance<nearest.peek().distance()) {
                    if(nearest.size()==32)nearest.remove();
                    nearest.add(new Light(lx,ly,lz,color[3],color[0]/255f,color[1]/255f,color[2]/255f,state.canOcclude(),distance));
                }
                sources++;enqueue(cursor);
            }
        }
        if(cursor==N){
            long propagationStart=System.nanoTime();diffuse();propagationNanos+=System.nanoTime()-propagationStart;
            litCells=0;
            for(int y=0;y<Y;y++)for(int z=0;z<Z;z++)for(int x=0;x<X;x++){
                int cell=index(x,y,z),offset=cell*3;
                if(Math.max(rgb[offset],Math.max(rgb[offset+1],rgb[offset+2]))>0)litCells++;
                // Alpha: 0 unknown, 127 solid, 255 open. Preserve solid cells
                // for held-light visibility while rejecting them from filtering.
                int alpha=occupancy[cell]==0?0:occupancy[cell]==1?127:255;
                image.setPixel(x+(y%8)*X,z+(y/8)*Z,ARGB.color(alpha,rgb[offset],rgb[offset+1],rgb[offset+2]));
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture,image,0,0,0,0);
            publishedX=scanX;publishedY=scanY;publishedZ=scanZ;publishedSources=sources;
            publishedLights=nearest.stream().sorted(Comparator.comparingDouble(Light::distance)).toList();
            published=true;publications++;cursor=0;
        }
        uploadUniforms(camera,published);
        updates++;cpuNanos+=System.nanoTime()-start;
    }
    private void uploadUniforms(Vec3 camera,boolean ready) {
        var mc=Minecraft.getInstance();
        var bytes=ByteBuffer.allocateDirect(1088).order(ByteOrder.nativeOrder());
        put(bytes,publishedX,publishedY,publishedZ,ready?1:0);
        Vec3 eye=mc.player==null?camera:mc.player.getEyePosition();
        put(bytes,(float)eye.x,(float)eye.y,(float)eye.z,ready?publishedLights.size():0);
        int[] heldA=!ready||mc.player==null?new int[4]:held(mc.player.getMainHandItem());
        int[] heldB=!ready||mc.player==null?new int[4]:held(mc.player.getOffhandItem());
        put(bytes,heldA[0]/255f,heldA[1]/255f,heldA[2]/255f,heldA[3]);
        put(bytes,heldB[0]/255f,heldB[1]/255f,heldB[2]/255f,heldB[3]);
        for(int i=0;i<32;i++) {
            if(!ready || i>=publishedLights.size())put(bytes,0,0,0,0);
            else {var light=publishedLights.get(i);put(bytes,light.x(),light.y(),light.z(),light.radius());}
        }
        for(int i=0;i<32;i++) {
            if(!ready || i>=publishedLights.size())put(bytes,0,0,0,0);
            else {var light=publishedLights.get(i);put(bytes,light.r(),light.g(),light.b(),light.solid()?1:0);}
        }
        bytes.flip();
        var device=RenderSystem.getDevice();
        uniforms=device.createCommandEncoder().transientMemory().uploadGpu(bytes,device.getDeviceInfo().limits().minUniformOffsetAlignment(),GpuBuffer.USAGE_UNIFORM);
    }
    private void enqueue(int cell){if(!queued[cell]){queued[cell]=true;queue[tail]=cell;tail=(tail+1)%N;count++;}}
    private void diffuse(){
        while(count>0){
            int cell=queue[head];head=(head+1)%N;count--;queued[cell]=false;
            int x=cell%X,z=(cell/X)%Z,y=cell/(X*Z);
            if(x>0)spread(cell,cell-1);if(x<X-1)spread(cell,cell+1);
            if(z>0)spread(cell,cell-X);if(z<Z-1)spread(cell,cell+X);
            if(y>0)spread(cell,cell-X*Z);if(y<Y-1)spread(cell,cell+X*Z);
        }
    }
    private void spread(int from,int to){
        if(occupancy[to]!=2)return;
        int a=from*3,b=to*3,power=Math.max(rgb[a],Math.max(rgb[a+1],rgb[a+2]));
        if(power<=17)return;
        boolean changed=false;
        for(int c=0;c<3;c++){
            int value=rgb[a+c]*(power-17)/power;
            if(value>rgb[b+c]){rgb[b+c]=value;changed=true;}
        }
        if(changed)enqueue(to);
    }
    private static void put(ByteBuffer b,float x,float y,float z,float w){b.putFloat(x).putFloat(y).putFloat(z).putFloat(w);}
    private static int[] held(ItemStack stack){
        if(stack.getItem() instanceof BlockItem block)return emission(block.getBlock().defaultBlockState());
        return new int[4];
    }
    private static int[] emission(BlockState state){
        int strength=state.getLightEmission();if(strength==0)return new int[4];
        String name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        float r=1,g=.52f,b=.20f;
        if(name.contains("soul")){r=.18f;g=.72f;b=1;}
        else if(name.equals("sea_lantern")||name.equals("end_rod")){r=.61f;g=.88f;b=1;}
        else if(name.contains("amethyst")||name.equals("crying_obsidian")){r=.63f;g=.24f;b=1;}
        else if(name.contains("redstone")){r=1;g=.12f;b=.05f;}
        else if(name.equals("pearlescent_froglight")){r=1;g=.64f;b=.92f;}
        else if(name.equals("verdant_froglight")){r=.65f;g=1;b=.60f;}
        else if(name.equals("ochre_froglight")){r=1;g=.87f;b=.57f;}
        else if(name.equals("beacon")){r=.75f;g=.92f;b=1;}
        else if(name.equals("glowstone")){r=1;g=.72f;b=.36f;}
        else if(name.equals("shroomlight")){r=1;g=.49f;b=.17f;}
        int power=strength*17;
        return new int[]{Math.round(power*r),Math.round(power*g),Math.round(power*b),strength};
    }
    public void bind(RenderPass pass){
        if(uniforms==null||view==null)return;
        pass.setUniform("LocalLighting",uniforms);
        pass.setUniform("LocalLightVolume",view,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
    public Object evidence(){var out=new LinkedHashMap<String,Object>();
        out.put("cells",N);out.put("queries",queries);out.put("updates",updates);out.put("publications",publications);
        out.put("sourceCells",publishedSources);out.put("litCells",litCells);out.put("visibleSourceLimit",32);out.put("publishedLights",publishedLights.size());
        out.put("cpuMeanMs",updates==0?0:cpuNanos/(updates*1_000_000.0));
        out.put("propagationMeanMs",publications==0?0:propagationNanos/(publications*1_000_000.0));
        out.put("method","Up to 32 native emitters with bounded voxel visibility rays, RGB diffusion fill and native scalar fallback; no traced GI");return out;}
    @Override public void close(){
        if(view!=null)view.close();if(texture!=null)texture.close();if(image!=null)image.close();
        view=null;texture=null;image=null;uniforms=null;level=null;published=false;cursor=0;emitters.clear();nearest.clear();publishedLights=List.of();
    }
}
