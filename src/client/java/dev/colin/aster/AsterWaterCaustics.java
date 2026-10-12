package dev.colin.aster;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.util.*;

/** Shared native GPU accumulation of refracted wave patches at four receiver depths. */
public final class AsterWaterCaustics implements AutoCloseable {
    private static final int RESOLUTION=1024,RAW_RESOLUTION=2048,GRID=1024,PROBE_RESOLUTION=512;
    private static final int FAR_RESOLUTION=2048,FAR_RAW_RESOLUTION=4096,FAR_GRID=2048,TILE_GRID=128;
    // Keep the cached wave field and receiver images at their checked precision.
    // Evaluate a coarser forward-raster mesh independently of those resolutions.
    private static final int RASTER_FACTOR=Integer.getInteger("aster.causticRasterFactor",2);
    static {
        if(RASTER_FACTOR!=1&&RASTER_FACTOR!=2) throw new IllegalArgumentException("Caustic raster factor must be 1 or 2");
    }
    private static int rasterGrid(boolean far) { return (far?FAR_GRID:GRID)/RASTER_FACTOR; }
    private TextureTarget raw,density,temporary;
    private TextureTarget farRaw,farDensity,farTemporary;
    private TextureTarget field,farField;
    private TextureTarget shallowRaw,shallowDensity,shallowTemporary;
    private TextureTarget farShallowRaw,farShallowDensity,farShallowTemporary;
    private GpuBuffer gridIndices;
    private GpuBuffer farGridIndices;
    private final GpuQueryPool[] queries=new GpuQueryPool[8];
    private final ArrayList<Double> timings=new ArrayList<>();
    private int frames;
    private GpuBufferSlice parameters(float axis,float offset,boolean flat,boolean far) {
        return parameters(axis,offset,flat,far,false);
    }
    private GpuBufferSlice parameters(float axis,float offset,boolean flat,boolean far,boolean raster) {
        try(var stack=MemoryStack.stackPush()) {
            var data=stack.malloc(16);
            data.putFloat(raster?rasterGrid(far):far?FAR_GRID:GRID).putFloat(axis).putFloat(offset).putFloat((far?2:0)+(flat?1:0)).flip();
            var device=RenderSystem.getDevice();
            return device.createCommandEncoder().transientMemory().uploadGpu(data,device.getDeviceInfo().limits().minUniformOffsetAlignment(),GpuBuffer.USAGE_UNIFORM);
        }
    }
    private static GpuBuffer createIndices(int grid,String label) {
        // A persistent index grid shares surface vertices between triangles.
        // Only positions/normals are evaluated each frame; indices upload once.
        var indices=MemoryUtil.memAlloc(grid*grid*6*Short.BYTES);
        try {
            for(int z=0;z<grid;z++) for(int x=0;x<grid;x++) {
                int a=z*(grid+1)+x,b=a+1,c=a+grid+1,d=c+1;
                indices.putShort((short)a).putShort((short)b).putShort((short)c).putShort((short)c).putShort((short)b).putShort((short)d);
            }
            indices.flip();
            return RenderSystem.getDevice().createBuffer(()->label,GpuBuffer.USAGE_INDEX|GpuBuffer.USAGE_COPY_DST,indices);
        } finally {MemoryUtil.memFree(indices);}
    }
    private void ensure() {
        if(density!=null) return;
        gridIndices=createIndices(TILE_GRID,"Aster shared tiled caustic grid indices");
        // Both levels use the same topology. Their world positions differ in
        // uniforms, so the immutable index buffer can be shared as well.
        farGridIndices=gridIndices;
        density=new TextureTarget("Aster wave caustic density",RESOLUTION,RESOLUTION,GpuFormat.RGBA16_FLOAT,null);
        temporary=new TextureTarget("Aster wave caustic filter",RESOLUTION,RESOLUTION,GpuFormat.RGBA16_FLOAT,null);
        raw=new TextureTarget("Aster supersampled wave caustic density",RAW_RESOLUTION,RAW_RESOLUTION,GpuFormat.RGBA16_FLOAT,null);
        farDensity=new TextureTarget("Aster far wave caustic density",FAR_RESOLUTION,FAR_RESOLUTION,GpuFormat.RGBA16_FLOAT,null);
        farTemporary=new TextureTarget("Aster far wave caustic filter",FAR_RESOLUTION,FAR_RESOLUTION,GpuFormat.RGBA16_FLOAT,null);
        farRaw=new TextureTarget("Aster supersampled far wave caustic density",FAR_RAW_RESOLUTION,FAR_RAW_RESOLUTION,GpuFormat.RGBA16_FLOAT,null);
        field=new TextureTarget("Aster near caustic source field",GRID+1,GRID+1,GpuFormat.RGBA32_FLOAT,null);
        farField=new TextureTarget("Aster far caustic source field",FAR_GRID+1,FAR_GRID+1,GpuFormat.RGBA32_FLOAT,null);
        shallowDensity=new TextureTarget("Aster shallow caustic density",RESOLUTION,RESOLUTION,GpuFormat.R16_FLOAT,null);
        shallowTemporary=new TextureTarget("Aster shallow caustic filter",RESOLUTION,RESOLUTION,GpuFormat.R16_FLOAT,null);
        shallowRaw=new TextureTarget("Aster supersampled shallow caustics",RAW_RESOLUTION,RAW_RESOLUTION,GpuFormat.R16_FLOAT,null);
        farShallowDensity=new TextureTarget("Aster far shallow caustic density",FAR_RESOLUTION,FAR_RESOLUTION,GpuFormat.R16_FLOAT,null);
        farShallowTemporary=new TextureTarget("Aster far shallow caustic filter",FAR_RESOLUTION,FAR_RESOLUTION,GpuFormat.R16_FLOAT,null);
        farShallowRaw=new TextureTarget("Aster supersampled far shallow caustics",FAR_RAW_RESOLUTION,FAR_RAW_RESOLUTION,GpuFormat.R16_FLOAT,null);
    }
    private void draw(GpuBufferSlice scene,float offset,boolean flat,boolean far) {
        ensure();var encoder=RenderSystem.getDevice().createCommandEncoder();
        var rawTarget=far?farRaw:raw;var densityTarget=far?farDensity:density;var filterTarget=far?farTemporary:temporary;
        var fieldTarget=far?farField:field;
        int grid=rasterGrid(far);
        try(var pass=encoder.createRenderPass(()->"Aster shared caustic height and slope field",fieldTarget.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_FIELD));
            pass.setUniform("AsterScene",scene);pass.setUniform("CausticParameters",parameters(0,offset,flat,far));
            pass.draw(3,1,0,0);
        }
        try(var pass=encoder.createRenderPass(()->far?"Aster far refracted wave patch accumulation":"Aster near refracted wave patch accumulation",rawTarget.getColorTextureView(),Optional.of(new Vector4f(0,0,0,0)))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_MAP));
            pass.setUniform("AsterScene",scene);pass.setUniform("CausticParameters",parameters(far?FAR_RAW_RESOLUTION:RAW_RESOLUTION,offset,flat,far,true));
            pass.setUniform("CausticField",fieldTarget.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setIndexBuffer(gridIndices,IndexType.SHORT);
            int tiles=grid/TILE_GRID;
            pass.drawIndexed(TILE_GRID*TILE_GRID*6,4*tiles*tiles,0,0,0);
        }
        try(var pass=encoder.createRenderPass(()->"Aster caustic pixel footprint integration",densityTarget.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_PREFILTER));
            pass.setUniform("CausticInput",rawTarget.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));pass.draw(3,1,0,0);
        }
        for(int axis=0;axis<2;axis++) {
            var source=axis==0?densityTarget:filterTarget;var target=axis==0?filterTarget:densityTarget;
            try(var pass=encoder.createRenderPass(()->"Aster finite caustic beam filter",target.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_FILTER));
                pass.setUniform("CausticParameters",parameters(axis,0,false,far));
                pass.setUniform("CausticInput",source.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(3,1,0,0);
            }
        }
        drawShallow(scene,offset,flat,far);
    }
    private void drawShallow(GpuBufferSlice scene,float offset,boolean flat,boolean far) {
        drawReceiverDepth(scene,.875f,flat,far);
    }
    private void drawReceiverDepth(GpuBufferSlice scene,float depth,boolean flat,boolean far) {
        // The .875-block receiver reuses the existing source field and tile
        // indices. R16 targets carry this one depth without four-channel waste.
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        var rawTarget=far?farShallowRaw:shallowRaw;var densityTarget=far?farShallowDensity:shallowDensity;
        var filterTarget=far?farShallowTemporary:shallowTemporary;int tiles=rasterGrid(far)/TILE_GRID;
        try(var pass=encoder.createRenderPass(()->"Aster shallow refracted wave accumulation",rawTarget.getColorTextureView(),Optional.of(new Vector4f(0)))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_SHALLOW_MAP));
            pass.setUniform("AsterScene",scene);pass.setUniform("CausticParameters",parameters(far?FAR_RAW_RESOLUTION:RAW_RESOLUTION,depth,flat,far,true));
            pass.setUniform("CausticField",(far?farField:field).getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setIndexBuffer(gridIndices,IndexType.SHORT);pass.drawIndexed(TILE_GRID*TILE_GRID*6,tiles*tiles,0,0,0);
        }
        try(var pass=encoder.createRenderPass(()->"Aster shallow pixel footprint integration",densityTarget.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_SHALLOW_PREFILTER));
            pass.setUniform("CausticInput",rawTarget.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));pass.draw(3,1,0,0);
        }
        for(int axis=0;axis<2;axis++) {
            var source=axis==0?densityTarget:filterTarget;var target=axis==0?filterTarget:densityTarget;
            try(var pass=encoder.createRenderPass(()->"Aster shallow finite beam filter",target.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_SHALLOW_FILTER));
                pass.setUniform("CausticParameters",parameters(axis,depth,false,far));
                pass.setUniform("CausticInput",source.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));pass.draw(3,1,0,0);
            }
        }
    }
    public void render(GpuBufferSlice scene) {
        var device=RenderSystem.getDevice();var encoder=device.createCommandEncoder();int ring=frames%queries.length;
        if(queries[ring]==null) queries[ring]=device.createTimestampQueryPool(2);
        else if(frames>=queries.length) {
            var times=queries[ring].getValues(0,2);
            if(times[0].isPresent()&&times[1].isPresent()) {
                timings.add((times[1].getAsLong()-times[0].getAsLong())*device.getDeviceInfo().timestampPeriod()/1_000_000.0);
                if(timings.size()>1200) timings.removeFirst();
            }
        }
        encoder.writeTimestamp(queries[ring],0);draw(scene,0,false,false);draw(scene,0,false,true);encoder.writeTimestamp(queries[ring],1);frames++;
    }
    public void bind(RenderPass pass) {
        if(density!=null) pass.setUniform("WaterCaustics",density.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if(farDensity!=null) pass.setUniform("WaterCausticsFar",farDensity.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if(shallowDensity!=null) pass.setUniform("WaterCausticsShallow",shallowDensity.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if(farShallowDensity!=null) pass.setUniform("WaterCausticsShallowFar",farShallowDensity.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
    public void bindSourceFields(RenderPass pass) {
        var linear=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if(field!=null) pass.setUniform("WaterCausticField",field.getColorTextureView(),linear);
        if(farField!=null) pass.setUniform("WaterCausticFieldFar",farField.getColorTextureView(),linear);
    }
    private void capture(String label,boolean flat,boolean far) {
        // Minecraft screenshots force alpha opaque. Encode the fourth depth
        // in a separate RGB half so it is measured rather than silently lost.
        var target=new TextureTarget("Aster caustic density acceptance",PROBE_RESOLUTION,PROBE_RESOLUTION*2,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Aster caustic density readback",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_DENSITY_PROBE));
            pass.setUniform("CausticInput",(far?farDensity:density).getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image->{
            try {
                double[] mean=new double[4],min={8,8,8,8},max=new double[4];int samples=0;
                for(int y=32;y<PROBE_RESOLUTION-32;y++) for(int x=32;x<PROBE_RESOLUTION-32;x++) {
                    int pixel=image.getPixel(x,PROBE_RESOLUTION*2-1-y);
                    int fourth=image.getPixel(x,PROBE_RESOLUTION-1-y);
                    int[] channels={ARGB.red(pixel),ARGB.green(pixel),ARGB.blue(pixel),ARGB.red(fourth)};
                    for(int c=0;c<4;c++) {double value=channels[c]*8.0/255.0;mean[c]+=value;min[c]=Math.min(min[c],value);max[c]=Math.max(max[c],value);}samples++;
                }
                boolean passed=true;for(int c=0;c<4;c++) {mean[c]/=samples;passed&=mean[c]>(flat?.98:.90)&&mean[c]<(flat?1.02:1.10);if(flat) passed&=min[c]>.98&&max[c]<1.02;}
                var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("flatReference",flat);result.put("cacheResolution",far?FAR_RESOLUTION:RESOLUTION);result.put("rawResolution",far?FAR_RAW_RESOLUTION:RAW_RESOLUTION);result.put("probeResolution",PROBE_RESOLUTION);result.put("samplesPerDepth",samples);result.put("worldExtent",far?256:64);
                result.put("depths",List.of(.5,1.5,3.5,5.5));result.put("densityMean",mean);result.put("densityMin",min);result.put("densityMax",max);
                result.put("scope","Actual native RGBA16F accumulation, normalized reduction and two beam-filter passes; fixed 512-square 8-bit diagnostic readback, excludes "+(far?16:4)+"m border, not a quality score.");
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                java.nio.file.Files.writeString(folder.resolve(label+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));image.writeToFile(folder.resolve(label+".png"));
            }catch(Exception ex){throw new RuntimeException(ex);}finally{image.close();target.destroyBuffers();}
        });
    }
    public void captureAcceptance(GpuBufferSlice scene) {
        if(!AutomatedVerification.HIDDEN) return;
        for(boolean far:new boolean[]{false,true}) {
            String prefix=far?"water-caustic-far-":"water-caustic-";
            draw(scene,0,true,far);capture(prefix+"flat-probe",true,far);captureShallow(prefix+"shallow-flat-probe",true,far);
            draw(scene,0,false,far);capture(prefix+"density-probe",false,far);captureShallow(prefix+"shallow-density-probe",false,far);captureFieldAcceptance(scene,prefix,far);
            draw(scene,1200,false,far);capture(prefix+"clock-probe",false,far);captureShallow(prefix+"shallow-clock-probe",false,far);
            draw(scene,0,false,far);
            for(float depth:new float[]{1.5f,2.875f,4.875f}) {
                drawReceiverDepth(scene,depth,false,far);
                captureDepthReconstruction(scene,prefix,depth,far);
            }
            // Restore the normal shallow layer before ordinary frame rendering.
            drawReceiverDepth(scene,.875f,false,far);
        }
    }
    private void captureDepthReconstruction(GpuBufferSlice scene,String prefix,float depth,boolean far) {
        if(!AutomatedVerification.HIDDEN) throw new IllegalStateException("Private caustic reconstruction probe only");
        var target=new TextureTarget("Aster depth reconstruction diagnostic",512,512,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Aster direct depth versus stored plane reconstruction",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_RECONSTRUCTION_PROBE));
            pass.setUniform("AsterScene",scene);pass.setUniform("CausticParameters",parameters(0,depth,false,far));
            pass.setUniform("CausticInput",(far?farDensity:density).getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("CausticReference",(far?farShallowDensity:shallowDensity).getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image->{
                try {
                    // RGB channels are direct density, current beam-following
                    // interpolation and unwarped interpolation. Alpha is unused.
                    var result=new LinkedHashMap<String,Object>();
                    result.put("depth",depth);result.put("far",far);
                    result.put("regionExtent",far?64:16);result.put("worldExtent",far?256:64);
                    result.put("receiverFootprint",far?.125:.035);
                    result.put("densityCodeStep",8.0/255.0);result.put("densityCodeMaximum",8);
                    result.put("identityControl",depth==1.5f);result.put("resolution",512);
                    result.put("channels",List.of("direct depth","beam-following interpolation","unwarped interpolation"));
                    result.put("region","Central quarter of the selected atlas, derived from the exact bound scene origin; same source field and clock for all three channels");
                    result.put("scope","Hidden diagnostic with source-patch accumulation at the actual depth and matched beam/pixel filtering; quantized and capped density readback, not a visual score or radiance measurement. Normal .875 depth is restored after the diagnostic.");
                    String name=prefix+"reconstruction-"+Float.toString(depth).replace('.','_');
                    var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                    java.nio.file.Files.writeString(folder.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    image.writeToFile(folder.resolve(name+".png"));
                }catch(Exception ex){throw new RuntimeException(ex);}finally{image.close();target.destroyBuffers();}
        });
    }
    private void captureShallow(String label,boolean flat,boolean far) {
        var target=new TextureTarget("Aster shallow caustic acceptance",PROBE_RESOLUTION,PROBE_RESOLUTION,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Aster shallow density readback",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_SHALLOW_DENSITY_PROBE));
            pass.setUniform("CausticInput",(far?farShallowDensity:shallowDensity).getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image->{
            try {
                double mean=0,min=8,max=0;int samples=0;
                for(int y=32;y<PROBE_RESOLUTION-32;y++) for(int x=32;x<PROBE_RESOLUTION-32;x++) {
                    double value=ARGB.red(image.getPixel(x,y))*8.0/255.0;mean+=value;min=Math.min(min,value);max=Math.max(max,value);samples++;
                }
                mean/=samples;boolean passed=mean>(flat?.98:.90)&&mean<(flat?1.02:1.10);
                if(flat) passed&=min>.98&&max<1.02;
                var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("flatReference",flat);result.put("depth",.875);
                result.put("samplesPerDepth",samples);result.put("cacheResolution",far?FAR_RESOLUTION:RESOLUTION);result.put("rawResolution",far?FAR_RAW_RESOLUTION:RAW_RESOLUTION);result.put("worldExtent",far?256:64);
                result.put("densityMean",mean);result.put("densityMin",min);result.put("densityMax",max);result.put("sourceFormat","R16_FLOAT");
                result.put("scope","Native fifth depth, normalized raw reduction/beam filtering with the original flat .98..1.02 and dynamic .90..1.10 flux limits and unchanged four/sixteen-block probe border; not a visual score.");
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                java.nio.file.Files.writeString(folder.resolve(label+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));image.writeToFile(folder.resolve(label+".png"));
            }catch(Exception ex){throw new RuntimeException(ex);}finally{image.close();target.destroyBuffers();}
        });
    }
    private void captureFieldAcceptance(GpuBufferSlice scene,String prefix,boolean far) {
        var target=new TextureTarget("Aster caustic field precision",512,1,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Aster cached versus direct source field",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_CAUSTIC_FIELD_PROBE));
            pass.setUniform("AsterScene",scene);pass.setUniform("CausticParameters",parameters(0,0,false,far));
            pass.setUniform("CausticField",(far?farField:field).getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image->{
            try {
                int heightPassed=0,slopePassed=0,finitePassed=0;
                for(int x=0;x<512;x++) {
                    int pixel=image.getPixel(x,0);
                    if(ARGB.red(pixel)==255) heightPassed++;
                    if(ARGB.green(pixel)==255) slopePassed++;
                    if(ARGB.blue(pixel)==255) finitePassed++;
                }
                var result=new LinkedHashMap<String,Object>();
                result.put("passed",heightPassed==512&&slopePassed==512&&finitePassed==512);
                result.put("samples",512);result.put("heightPassed",heightPassed);result.put("slopePassed",slopePassed);result.put("finitePassed",finitePassed);
                result.put("heightSlopeTolerance",.00001);result.put("sourceFormat","RGBA32_FLOAT");result.put("sourceGrid",far?FAR_GRID:GRID);
                result.put("scope","GPU texelFetch versus direct wave evaluation at the same common entry-grid nodes, including all four corners; precision evidence, not a quality score or equality to the previous depth-shifted sampling grid.");
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                java.nio.file.Files.writeString(folder.resolve(prefix+"field-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                image.writeToFile(folder.resolve(prefix+"field-probe.png"));
            }catch(Exception ex){throw new RuntimeException(ex);}finally{image.close();target.destroyBuffers();}
        });
    }
    public Object evidence() {
        var sorted=new ArrayList<>(timings);Collections.sort(sorted);
        var result=new LinkedHashMap<String,Object>();result.put("resolution",RESOLUTION);result.put("rawResolution",RAW_RESOLUTION);result.put("worldExtent",64);result.put("sourceGrid",GRID);result.put("depths",List.of(.5,1.5,3.5,5.5));
        long nearBytes=(2L*RESOLUTION*RESOLUTION+(long)RAW_RESOLUTION*RAW_RESOLUTION)*8,farBytes=(2L*FAR_RESOLUTION*FAR_RESOLUTION+(long)FAR_RAW_RESOLUTION*FAR_RAW_RESOLUTION)*8;
        long fieldBytes=((long)(GRID+1)*(GRID+1)+(long)(FAR_GRID+1)*(FAR_GRID+1))*16;
        long shallowBytes=(2L*RESOLUTION*RESOLUTION+(long)RAW_RESOLUTION*RAW_RESOLUTION+2L*FAR_RESOLUTION*FAR_RESOLUTION+(long)FAR_RAW_RESOLUTION*FAR_RAW_RESOLUTION)*2;
        result.put("textureBytes",nearBytes+farBytes+fieldBytes+shallowBytes);result.put("nearTextureBytes",nearBytes);result.put("farTextureBytes",farBytes);result.put("trianglesPerFrame",(rasterGrid(false)*rasterGrid(false)+rasterGrid(true)*rasterGrid(true))*2*5);result.put("renderedFrames",frames);result.put("timingSamples",sorted.size());
        result.put("rasterFactor",RASTER_FACTOR);result.put("nearRasterGrid",rasterGrid(false));result.put("farRasterGrid",rasterGrid(true));
        result.put("shallowDepth",.875);result.put("shallowTextureBytes",shallowBytes);result.put("shallowTextureFormat","R16_FLOAT");result.put("shallowUpdatedEveryFrame",true);
        result.put("sourceFieldBytes",fieldBytes);result.put("sourceFieldFormat","RGBA32_FLOAT");result.put("sharedDepthWaveField",true);result.put("sourceEntryCentreDepth",3.0);
        result.put("waveFieldEvaluationsPerFrame",(GRID+1)*(GRID+1)+(FAR_GRID+1)*(FAR_GRID+1));
        result.put("farResolution",FAR_RESOLUTION);result.put("farRawResolution",FAR_RAW_RESOLUTION);result.put("farSourceGrid",FAR_GRID);result.put("farWorldExtent",256);result.put("farWaveFootprint",.0625);result.put("farUpdatedEveryFrame",true);
        result.put("indexedSourceGrid",true);result.put("indexBufferBytes",(long)TILE_GRID*TILE_GRID*6*Short.BYTES);result.put("uniqueSourceVerticesPerDepth",(GRID+1)*(GRID+1));
        result.put("sharedLevelIndexBuffer",true);result.put("sourceTileSize",TILE_GRID);result.put("sourceTileIndexType","UINT16");
        result.put("nearInstancesPerDepth",(rasterGrid(false)/TILE_GRID)*(rasterGrid(false)/TILE_GRID));result.put("farInstancesPerDepth",(rasterGrid(true)/TILE_GRID)*(rasterGrid(true)/TILE_GRID));
        result.put("totalIndexBufferBytes",(long)TILE_GRID*TILE_GRID*6*Short.BYTES);
        if(!sorted.isEmpty()){result.put("gpuMedianMs",sorted.get(sorted.size()/2));result.put("gpuP95Ms",sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.95))));}
        result.put("sharedRefractedPatchAccumulation",true);return result;
    }
    @Override public void close() {
        if(density!=null){density.destroyBuffers();density=null;}if(temporary!=null){temporary.destroyBuffers();temporary=null;}
        if(raw!=null){raw.destroyBuffers();raw=null;}
        if(farDensity!=null){farDensity.destroyBuffers();farDensity=null;}if(farTemporary!=null){farTemporary.destroyBuffers();farTemporary=null;}
        if(farRaw!=null){farRaw.destroyBuffers();farRaw=null;}
        if(field!=null){field.destroyBuffers();field=null;}if(farField!=null){farField.destroyBuffers();farField=null;}
        if(shallowRaw!=null){shallowRaw.destroyBuffers();shallowRaw=null;}if(shallowDensity!=null){shallowDensity.destroyBuffers();shallowDensity=null;}if(shallowTemporary!=null){shallowTemporary.destroyBuffers();shallowTemporary=null;}
        if(farShallowRaw!=null){farShallowRaw.destroyBuffers();farShallowRaw=null;}if(farShallowDensity!=null){farShallowDensity.destroyBuffers();farShallowDensity=null;}if(farShallowTemporary!=null){farShallowTemporary.destroyBuffers();farShallowTemporary=null;}
        if(farGridIndices!=null && farGridIndices!=gridIndices) farGridIndices.close();farGridIndices=null;
        if(gridIndices!=null){gridIndices.close();gridIndices=null;}
        for(int i=0;i<queries.length;i++)if(queries[i]!=null){queries[i].close();queries[i]=null;}frames=0;timings.clear();
    }
}
