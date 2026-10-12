package dev.colin.aster;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.colin.aster.mixin.ChunkSectionsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.OptionalDouble;

/** An independently culled native terrain view supplies off-screen lake reflections. */
public final class AsterPlanarWater implements AutoCloseable {
    private TextureTarget target;
    private GpuBufferSlice uniform;
    private final Matrix4f projection=new Matrix4f();
    private final FrustumIntersection frustum=new FrustumIntersection();
    private double height=Double.NaN;
    private int frames,draws,sections,directDraws,indirectDraws;
    private boolean fixtureFeatureOutsideMainView;
    private Object dimension;
    private boolean valid;
    private long lastSurfaceSearch;
    private net.minecraft.world.phys.Vec3 lastSearchCamera;
    private int rangeChunks,visitedSections;
    private float fadeStart,fadeEnd,farthestSection;
    private boolean legacyRange;
    private BlockPos surfaceAnchor;
    private boolean anchorCovered,retainedSurface;
    private int surfaceSearches,planeChanges;
    private boolean legacySurfaceSearch;
    private boolean submerged;

    private double sourceHeight(BlockPos pos) {
        var level=Minecraft.getInstance().level;
        if(!level.hasChunkAt(pos)) return Double.NaN;
        var fluid=level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource() && !level.getFluidState(pos.above()).is(FluidTags.WATER)
            ? pos.getY()+fluid.getHeight(level,pos)-.001 : Double.NaN;
    }
    private double rememberSurface(BlockPos pos,boolean covered) {
        surfaceAnchor=pos;anchorCovered=covered;return sourceHeight(pos);
    }
    private boolean anchorValid(CameraRenderState camera) {
        if(surfaceAnchor == null || !Double.isFinite(height)) return false;
        double x=surfaceAnchor.getX()+.5-camera.pos.x(),z=surfaceAnchor.getZ()+.5-camera.pos.z();
        double current=sourceHeight(surfaceAnchor);
        if(x*x+z*z>fadeEnd*fadeEnd || !Double.isFinite(current) || Math.abs(current-height)>.01) return false;
        return anchorCovered || Minecraft.getInstance().level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,surfaceAnchor.getX(),surfaceAnchor.getZ())-1 == surfaceAnchor.getY();
    }
    private double findSurface(CameraRenderState camera) {
        retainedSurface=false;surfaceSearches++;
        if(legacyRange || legacySurfaceSearch) return findSurfaceLegacy(camera);
        var level=Minecraft.getInstance().level;
        // When submerged, find the connected surface above the actual eye.
        // Searching only downward misses the very plane that produces TIR.
        if(submerged) {
            var cursor=camera.blockPos;
            for(int step=0;step<64;step++,cursor=cursor.above()) {
                if(!level.hasChunkAt(cursor) || !level.getFluidState(cursor).is(FluidTags.WATER)) break;
                if(Double.isFinite(sourceHeight(cursor))) return rememberSurface(cursor,true);
            }
            surfaceAnchor=null;return Double.NaN;
        }
        var forward=new Matrix4f(camera.viewRotationMatrix).invert().transformDirection(new Vector3f(0,0,-1));
        float horizontal=(float)Math.sqrt(forward.x*forward.x+forward.z*forward.z);
        if(horizontal>.001f) forward.mul(1/horizontal);
        var columns=new ArrayList<BlockPos>();
        boolean cached=anchorValid(camera);
        BlockPos firstOpen=null;
        // Search all exposed surfaces before the covered-water fallback. A
        // solid bank must never let a cave beneath it replace the visible lake.
        for(int distance:new int[]{0,8,24,48,80,128,192,256,384,480}) {
            if(distance>fadeEnd) continue;
            for(int side:new int[]{0,-4,4,-12,12}) {
                int x=(int)Math.floor(camera.pos.x()+forward.x*distance-forward.z*side);
                int z=(int)Math.floor(camera.pos.z()+forward.z*distance+forward.x*side);
                var column=new BlockPos(x,camera.blockPos.getY(),z);
                if(!level.hasChunkAt(column)) continue;
                columns.add(column);
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
                if(y>camera.blockPos.getY()) continue;
                var pos=new BlockPos(x,y,z);double candidate=sourceHeight(pos);
                if(!Double.isFinite(candidate)) continue;
                // Crossing directly above another water level is a real plane
                // change. Otherwise prefer the already observed surface level.
                if(x==camera.blockPos.getX() && z==camera.blockPos.getZ()) return rememberSurface(pos,false);
                if(cached && Math.abs(candidate-height)<.01) return rememberSurface(pos,false);
                if(firstOpen==null) firstOpen=pos;
            }
        }
        if(cached) {
            var clip=new Matrix4f(WorldCameraTransforms.projection(camera)).mul(camera.viewRotationMatrix).transform(new Vector4f(
                (float)(surfaceAnchor.getX()+.5-camera.pos.x()),(float)(height-camera.pos.y()),(float)(surfaceAnchor.getZ()+.5-camera.pos.z()),1));
            boolean visible=clip.w>.001f && Math.abs(clip.x)<clip.w*1.25f && Math.abs(clip.y)<clip.w*1.25f;
            // Retain validated world geometry when sparse sample columns move
            // across a bank. The reflection image is still rerendered each frame.
            if(firstOpen==null || visible) {retainedSurface=true;return height;}
        }
        if(firstOpen!=null) return rememberSurface(firstOpen,false);
        for(var column:columns) {
            for(int y=camera.blockPos.getY();y>=Math.max(level.getMinY(),camera.blockPos.getY()-24);y--) {
                var pos=new BlockPos(column.getX(),y,column.getZ());
                if(Double.isFinite(sourceHeight(pos))) return rememberSurface(pos,true);
                // The first solid surface blocks this vertical sample. Continue
                // to other columns rather than scanning through it into caves.
                if(level.getBlockState(pos).canOcclude()) break;
            }
        }
        surfaceAnchor=null;return Double.NaN;
    }

    private double findSurfaceLegacy(CameraRenderState camera) {
        var level=Minecraft.getInstance().level;
        var forward=new Matrix4f(camera.viewRotationMatrix).invert().transformDirection(new Vector3f(0,0,-1));
        float horizontal=(float)Math.sqrt(forward.x*forward.x+forward.z*forward.z);
        if(horizontal > .001f) forward.mul(1/horizontal);
        int[] distances=legacyRange ? new int[]{0,4,8,16,24,40} : new int[]{0,8,24,48,80,128,192,256,384,480};
        for(int distance:distances) for(int side:new int[]{0,-4,4,-12,12}) {
            int x=(int)Math.floor(camera.pos.x()+forward.x*distance-forward.z*side);
            int z=(int)Math.floor(camera.pos.z()+forward.z*distance+forward.x*side);
            var column=new BlockPos(x,camera.blockPos.getY(),z);
            if(!level.hasChunkAt(column)) continue;
            // Native heightmaps identify open water without scanning caves
            // below a bank or imposing a camera-height cutoff. Leaves do not
            // hide the lake surface from this lookup.
            if(!legacyRange) {
                int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
                var pos=new BlockPos(x,surface,z);
                var fluid=level.getFluidState(pos);
                if(surface<=camera.blockPos.getY() && fluid.is(FluidTags.WATER) && fluid.isSource()
                    && !level.getFluidState(pos.above()).is(FluidTags.WATER)) return surface+fluid.getHeight(level,pos)-.001;
            }
            // Retain nearby covered-water detection for rooms and overhangs.
            // Only the open-surface path above extends below the old 24-block
            // search, so distant caves cannot masquerade as the visible lake.
            int top=camera.blockPos.getY()+2;
            int bottom=Math.max(level.getMinY(),camera.blockPos.getY()-24);
            for(int y=top;y>=bottom;y--) {
                var pos=new BlockPos(x,y,z);
                if(!level.hasChunkAt(pos)) break;
                var fluid=level.getFluidState(pos);
                if(fluid.is(FluidTags.WATER) && fluid.isSource() && !level.getFluidState(pos.above()).is(FluidTags.WATER))
                    return y+fluid.getHeight(level,pos)-.001;
            }
        }
        return Double.NaN;
    }
    public void render(LevelRenderer renderer,CameraRenderState camera,Object currentDimension,GpuBufferSlice scene,TextureTarget[] shadows,boolean indirect) {
        var mc=Minecraft.getInstance();var encoder=RenderSystem.getDevice().createCommandEncoder();
        legacySurfaceSearch=AutomatedVerification.HIDDEN && Boolean.getBoolean("aster.legacySurfaceSearch");
        legacyRange=AutomatedVerification.HIDDEN && (AsterRenderer.INSTANCE.verificationLightingFlags()&8388608)!=0;
        var area=renderer.viewArea();
        rangeChunks=area == null ? 0 : legacyRange ? Math.min(area.getViewDistance(),5) : area.getViewDistance();
        // Keep the whole fade inside loaded native chunks, including the
        // camera's changing position within its 16-block section.
        fadeEnd=Math.max(16,rangeChunks*16-16);
        fadeStart=Math.max(0,fadeEnd-Math.max(32,fadeEnd*.20f));
        if(AutomatedVerification.HIDDEN && System.getProperty("aster.verificationProfile","").equals("waterhybrid")) {
            var mainProjection=new Matrix4f(WorldCameraTransforms.projection(camera)).mul(camera.viewRotationMatrix);
            boolean above=true,below=true;
            for(int x:new int[]{42,47}) for(int y:new int[]{65,77}) for(int z:new int[]{-9,-6}) {
                var clip=mainProjection.transform(new Vector4f((float)(x-camera.pos.x()),(float)(y-camera.pos.y()),(float)(z-camera.pos.z()),1));
                above &= clip.y>clip.w;below &= clip.y<-clip.w;
            }
            fixtureFeatureOutsideMainView=above || below;
        }
        var main=mc.gameRenderer.mainRenderTarget();
        // Preserve reflected silhouettes at the same 75% scale as balanced SSR.
        int width=Math.min(1440,Math.max(1,(main.width*3+3)/4));
        int targetHeight=Math.max(1,Math.round(width*(float)main.height/main.width));
        if(target == null || target.width != width || target.height != targetHeight) {
            if(target != null) target.destroyBuffers();
            target=new TextureTarget("Aster off-screen water terrain",width,targetHeight,GpuFormat.RGBA16_FLOAT,GpuFormat.D32_FLOAT);
            encoder.clearColorAndDepthTextures(target.getColorTexture(),new Vector4f(),target.getDepthTexture(),0);
        }
        long now=System.nanoTime();
        boolean nextSubmerged=camera.fogType.name().equals("WATER");
        if(nextSubmerged != submerged) {lastSearchCamera=null;submerged=nextSubmerged;}
        if(dimension != currentDimension || lastSearchCamera == null || lastSearchCamera.distanceToSqr(camera.pos)>16 || now-lastSurfaceSearch>250_000_000L) {
            if(dimension != currentDimension) {height=Double.NaN;surfaceAnchor=null;}
            double next=findSurface(camera);
            if(Double.isFinite(height) && Double.isFinite(next) && Math.abs(next-height)>.01) planeChanges++;
            height=next;lastSurfaceSearch=now;lastSearchCamera=camera.pos;
        }
        dimension=currentDimension;
        valid=Double.isFinite(height) && (submerged ? camera.pos.y() < height-.03 : camera.pos.y() > height+.03)
            && (AsterRenderer.INSTANCE.verificationLightingFlags()&(512|4096|65536)) == 0;
        double relativeHeight=Double.isFinite(height) ? height-camera.pos.y() : 0;
        // Extra angular coverage handles wave-normal perturbations near view edges.
        var wide=new Matrix4f().scaling(1/1.25f,1/1.25f,1).mul(WorldCameraTransforms.projection(camera));
        projection.set(wide).mul(camera.viewRotationMatrix).translate(0,(float)(2*relativeHeight),0).scale(1,-1,1);
        try(var stack=MemoryStack.stackPush()) {
            var bytes=stack.malloc(160);projection.get(0,bytes);new Matrix4f(projection).invert().get(64,bytes);
            bytes.position(128);bytes.putFloat((float)relativeHeight).putFloat(valid ? 1 : 0).putFloat(width).putFloat(targetHeight);
            bytes.putFloat(fadeStart).putFloat(fadeEnd).putFloat(fadeEnd*2.5f).putFloat(legacyRange ? 1 : 0).flip();
            uniform=encoder.transientMemory().uploadGpu(bytes,RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(),GpuBuffer.USAGE_UNIFORM);
        }
        frames++;
        if(!valid) { sections=0;return; }
        if(area == null) {valid=false;return;}
        var visible=renderer.visibleSections();var saved=new ArrayList<>(visible);
        var atlas=mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR,true);
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try {
            visible.clear();
            int cx=camera.blockPos.getX()>>4,cz=camera.blockPos.getZ()>>4,range=rangeChunks;
            frustum.set(projection);visitedSections=0;farthestSection=0;
            int minY=submerged ? area.minSectionY() : Math.max(area.minSectionY(),(int)Math.floor(height/16));
            int maxY=submerged ? Math.min(area.maxSectionY(),(int)Math.floor(height/16))
                : Math.min(area.maxSectionY(),legacyRange ? Math.floorDiv(camera.blockPos.getY(),16)+5 : area.maxSectionY());
            var lookup=new BlockPos.MutableBlockPos();
            for(int x=cx-range;x<=cx+range;x++) for(int z=cz-range;z<=cz+range;z++) {
                float columnX=(float)(x*16-camera.pos.x()),columnZ=(float)(z*16-camera.pos.z());
                float nearX=Math.max(Math.max(columnX,0),-columnX-16),nearZ=Math.max(Math.max(columnZ,0),-columnZ-16);
                if(!legacyRange && (nearX*nearX+nearZ*nearZ>=fadeEnd*fadeEnd
                    || !frustum.testAab(columnX,(float)(minY*16-camera.pos.y()),columnZ,columnX+16,(float)((maxY+1)*16-camera.pos.y()),columnZ+16))) continue;
                for(int y=minY;y<=maxY;y++) {
                    visitedSections++;
                    var section=area.getRenderSectionAt(lookup.set(x*16,y*16,z*16));
                    if(section == null || section.getSectionMesh() == null || !section.getSectionMesh().hasRenderableLayers()) continue;
                    var origin=section.getRenderOrigin();
                    float rx=(float)(origin.getX()-camera.pos.x()),ry=(float)(origin.getY()-camera.pos.y()),rz=(float)(origin.getZ()-camera.pos.z());
                    boolean inside;
                    if(legacyRange) {
                        var center=projection.transform(new Vector4f(rx+8,ry+8,rz+8,1));
                        inside=center.w > -28 && Math.abs(center.x)<center.w+28 && Math.abs(center.y)<center.w+28;
                    } else inside=frustum.testAab(rx,ry,rz,rx+16,ry+16,rz+16);
                    if(inside) {
                        visible.add(section);
                        farthestSection=Math.max(farthestSection,(float)Math.sqrt((rx+8)*(rx+8)+(rz+8)*(rz+8)));
                    }
                }
            }
            sections=visible.size();
            var chunks=indirect
                ? renderer.prepareChunkRendersIndirect(new Matrix4f(),false) : renderer.prepareChunkRenders(new Matrix4f(),false);
            RenderSystem.resizeAllAutoStorageIndexBuffers();
            try(var pass=encoder.createRenderPass(() -> "Aster off-screen terrain reflection",target.getColorTextureView(),Optional.of(new Vector4f()),target.getDepthTextureView(),OptionalDouble.of(0))) {
                RenderSystem.bindDefaultUniforms(pass);AsterRenderer.INSTANCE.bindScene(pass);
                pass.setUniform("AsterScene",scene);pass.setUniform("WaterPlanar",uniform);
                AsterRenderer.INSTANCE.bindUnderwaterFocusedScatter(pass);
                AsterRenderer.INSTANCE.bindLocalLighting(pass);
                for(int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),nearest);
                ((ChunkSectionsAccessor)chunks).aster$renderLayers(ChunkSectionLayerGroup.OPAQUE.layers(),linear,pass,atlas,mc.gameRenderer.lightmap(),AsterPipelines.WATER_PLANAR,AsterPipelines.WATER_PLANAR_INDIRECT);
            }
            draws++;
            if(indirect) indirectDraws++;else directDraws++;
        } finally {visible.clear();visible.addAll(saved);}
    }
    public void bind(RenderPass pass) {
        var nearest=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        pass.setUniform("WaterPlanar",uniform);pass.setUniform("PlanarColor",target.getColorTextureView(),linear);pass.setUniform("PlanarDepth",target.getDepthTextureView(),nearest);
    }
    public Object evidence() {
        var data=new LinkedHashMap<String,Object>();data.put("valid",valid);data.put("surfaceHeight",Double.isFinite(height) ? height : null);
        data.put("submerged",submerged);
        data.put("width",target == null ? 0 : target.width);data.put("height",target == null ? 0 : target.height);
        data.put("textureBytes",target == null ? 0L : (long)target.width*target.height*12);data.put("draws",draws);data.put("sections",sections);
        data.put("directDraws",directDraws);data.put("indirectDraws",indirectDraws);
        data.put("rangeChunks",rangeChunks);data.put("fadeStartBlocks",fadeStart);data.put("fadeEndBlocks",fadeEnd);
        data.put("farthestSectionBlocks",farthestSection);data.put("visitedSections",visitedSections);data.put("legacyRange",legacyRange);
        if(AutomatedVerification.HIDDEN && System.getProperty("aster.verificationProfile","").equals("waterhybrid")) data.put("fixtureGoldPillarOutsideMainFrustum",fixtureFeatureOutsideMainView);
        data.put("surfaceSearches",surfaceSearches);data.put("planeChanges",planeChanges);data.put("retainedSurface",retainedSurface);
        data.put("legacySurfaceSearch",legacySurfaceSearch);data.put("surfaceAnchor",surfaceAnchor == null ? null : new int[]{surfaceAnchor.getX(),surfaceAnchor.getY(),surfaceAnchor.getZ()});
        data.put("source","independently culled native opaque chunk meshes");return data;
    }
    public TextureTarget target() { return target; }
    @Override public void close() {if(target != null) target.destroyBuffers();target=null;uniform=null;dimension=null;height=Double.NaN;valid=false;lastSearchCamera=null;lastSurfaceSearch=0;surfaceAnchor=null;}
}
