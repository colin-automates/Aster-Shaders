package dev.colin.aster;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Optional;

public final class AsterPipelines {
    public static final BindGroupLayout SCENE = BindGroupLayout.builder()
        .withUniform("AsterScene", UniformType.UNIFORM_BUFFER).build();
    public static final BindGroupLayout LOCAL_LIGHTING = BindGroupLayout.builder()
        .withUniform("LocalLighting",UniformType.UNIFORM_BUFFER)
        .withUniform("LocalLightVolume",UniformType.COMBINED_IMAGE_SAMPLER).build();
    public static final BindGroupLayout SKY_INPUT = samplers("SkyClouds");
    public static final RenderPipeline LOCAL_LIGHT_PROBE = RenderPipeline.builder().withLocation(id("pipeline/local_light_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/local_light_probe"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(LOCAL_LIGHTING).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final BindGroupLayout CLOUD_SHADOW_INPUT = samplers("CloudShadow");
    public static final BindGroupLayout FOG_FORWARD = samplers("FogVolume");
    private static final BindGroupLayout SKY_SCENE = BindGroupLayout.builder().withUniform("AsterScene",UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout CAUSTIC_PARAMETERS=BindGroupLayout.builder().withUniform("CausticParameters",UniformType.UNIFORM_BUFFER).build();
    public static final RenderPipeline WATER_CAUSTIC_FIELD=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_field"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_field"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(CAUSTIC_PARAMETERS).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA32_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_FIELD_PROBE=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_field_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_field_probe"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(CAUSTIC_PARAMETERS).withBindGroupLayout(samplers("CausticField"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_MAP=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_map"))
        .withVertexShader(id("core/water_caustic_map")).withFragmentShader(id("core/water_caustic_map"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(CAUSTIC_PARAMETERS).withBindGroupLayout(samplers("CausticField")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.of(new BlendFunction(BlendFactor.ONE,BlendFactor.ONE)),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_FILTER=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_filter"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_filter"))
        .withBindGroupLayout(CAUSTIC_PARAMETERS).withBindGroupLayout(samplers("CausticInput")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_DENSITY_PROBE=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_density_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_density_probe"))
        .withBindGroupLayout(samplers("CausticInput")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_PREFILTER=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_prefilter"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_prefilter"))
        .withBindGroupLayout(samplers("CausticInput")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_SHALLOW_MAP=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_shallow_map"))
        .withVertexShader(id("core/water_caustic_map")).withFragmentShader(id("core/water_caustic_map")).withShaderDefine("CAUSTIC_SHALLOW")
        .withBindGroupLayout(SCENE).withBindGroupLayout(CAUSTIC_PARAMETERS).withBindGroupLayout(samplers("CausticField"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.of(new BlendFunction(BlendFactor.ONE,BlendFactor.ONE)),GpuFormat.R16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_SHALLOW_FILTER=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_shallow_filter"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_filter")).withShaderDefine("CAUSTIC_SHALLOW")
        .withBindGroupLayout(CAUSTIC_PARAMETERS).withBindGroupLayout(samplers("CausticInput"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.R16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_SHALLOW_PREFILTER=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_shallow_prefilter"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_prefilter"))
        .withBindGroupLayout(samplers("CausticInput")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.R16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_SHALLOW_DENSITY_PROBE=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_shallow_density_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_density_probe")).withShaderDefine("CAUSTIC_SHALLOW")
        .withBindGroupLayout(samplers("CausticInput")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline SKY_CLOUDS = RenderPipeline.builder().withLocation(id("pipeline/sky_clouds"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/sky_clouds"))
        .withBindGroupLayout(SKY_SCENE).withBindGroupLayout(samplers("CloudNoise")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline CLOUD_SHADOW = RenderPipeline.builder().withLocation(id("pipeline/cloud_shadow"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/cloud_shadow"))
        .withBindGroupLayout(SKY_SCENE).withBindGroupLayout(samplers("CloudNoise")).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline SKY = RenderPipeline.builder().withLocation(id("pipeline/sky"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/sky"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(SKY_INPUT).withBindGroupLayout(FOG_FORWARD).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final BindGroupLayout SHADOW_MATRIX = BindGroupLayout.builder()
        .withUniform("ShadowProjection", UniformType.UNIFORM_BUFFER).build();
    public static final BindGroupLayout MATERIAL_INPUT = samplers("MaterialAtlas","NormalAtlas");
    public static final BindGroupLayout SHADOW_DEPTH_INPUT = samplers("Shadow0","Shadow1","Shadow2");
    public static final BindGroupLayout SHADOW_INPUT = samplers("Shadow0","Shadow1","Shadow2","ShadowCaster0","ShadowCaster1","ShadowCaster2");
    public static final BindGroupLayout WATER_INPUTS = BindGroupLayout.builder()
        .withUniform("OpaqueColor", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("OpaqueDepth", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterReflection", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterSurface", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterDepth", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterVolume", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterCaustics", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterCausticsFar", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterCausticsShallow", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterCausticsShallowFar", UniformType.COMBINED_IMAGE_SAMPLER).build();
    public static final BindGroupLayout WATER_TEMPORAL = BindGroupLayout.builder()
        .withUniform("WaterTemporal",UniformType.UNIFORM_BUFFER).build();
    public static final BindGroupLayout WATER_PLANAR_MATRIX = BindGroupLayout.builder().withUniform("WaterPlanar",UniformType.UNIFORM_BUFFER).build();
    public static final RenderPipeline WATER_PLANAR=waterPlanar(false),WATER_PLANAR_INDIRECT=waterPlanar(true);
    public static final RenderPipeline WATER_SURFACE = waterSurface(false);
    public static final RenderPipeline WATER_SURFACE_INDIRECT = waterSurface(true);
    private static final BindGroupLayout WATER_TRACE_INPUTS = samplers("OpaqueColor","OpaqueDepth","WaterSurface","WaterDepth","OpaqueMip0","OpaqueMip1","OpaqueMip2","OpaqueMip3","PlanarColor","PlanarDepth");
    private static final BindGroupLayout WATER_RESOLVE_INPUTS = samplers("WaterSurface","WaterDepth","RawReflection","PreviousSurface","PreviousDepth","PreviousReflection");
    public static final RenderPipeline WATER_TRACE = waterFullscreen("water_trace",WATER_TRACE_INPUTS,false);
    public static final RenderPipeline WATER_TRACE_DEBUG = waterFullscreen("water_trace",WATER_TRACE_INPUTS,true);
    public static final RenderPipeline WATER_RESOLVE = waterFullscreen("water_resolve",WATER_RESOLVE_INPUTS,false);
    public static final RenderPipeline WATER_DEBUG = waterFullscreen("water_resolve",WATER_RESOLVE_INPUTS,true);
    public static final RenderPipeline WATER_CONTACT_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_contact_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_contact_probe"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(WATER_TEMPORAL).withBindGroupLayout(samplers("WaterSurface","WaterDepth"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_CAUSTIC_RECONSTRUCTION_PROBE=RenderPipeline.builder().withLocation(id("pipeline/water_caustic_reconstruction_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_caustic_reconstruction_probe"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(CAUSTIC_PARAMETERS).withBindGroupLayout(samplers("CausticInput","CausticReference"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_EDGE_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_edge_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_edge_probe"))
        .withBindGroupLayout(SCENE).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_probe"))
        .withBindGroupLayout(SCENE).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final BindGroupLayout FOG_TEMPORAL = BindGroupLayout.builder()
        .withUniform("FogTemporal",UniformType.UNIFORM_BUFFER).build();
    public static final RenderPipeline FOG_VOLUME_LIGHT = fogFullscreen("fog_volume_light",GpuFormat.RGBA16_FLOAT,samplers("FogEnvironment","FogTerrain","Shadow0","Shadow1","Shadow2"),false);
    public static final RenderPipeline FOG_VOLUME_HISTORY = fogFullscreen("fog_volume_history",GpuFormat.RGBA16_FLOAT,samplers("FogRawVolume","FogPreviousVolume"),false);
    public static final RenderPipeline FOG_VOLUME_HISTORY_DEBUG = fogFullscreen("fog_volume_history",GpuFormat.RGBA8_UNORM,samplers("FogRawVolume","FogPreviousVolume"),true,"FOG_VOLUME_DEBUG");
    public static final RenderPipeline FOG_VOLUME_INTEGRATE = fogFullscreen("fog_volume_integrate",GpuFormat.RGBA16_FLOAT,samplers("FogScanInput"),false);
    public static final RenderPipeline FOG_VOLUME_DEBUG = fullscreen("fog_volume_debug",GpuFormat.RGBA8_UNORM,samplers("FogInput"),false);
    public static final RenderPipeline FOG_OPAQUE_RECOVER = RenderPipeline.builder().withLocation(id("pipeline/fog_opaque_recover"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/fog_opaque_recover"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(samplers("FogVolume","FoggedOpaque","OpaqueDepth"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline FOG_VOLUME_PROBE = RenderPipeline.builder().withLocation(id("pipeline/fog_volume_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/fog_volume_probe"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(samplers("FogVolume","FogCoefficients"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline FOG_GEOMETRY = fogFullscreen("fog_geometry",GpuFormat.RG32_FLOAT,samplers("SceneDepth","MaterialData","WaterSurface","WaterDepth"),false);
    public static final RenderPipeline FOG_TRACE = fogFullscreen("fog_trace",GpuFormat.RGBA16_FLOAT,samplers("FogGeometry","Shadow0","Shadow1","Shadow2"),false);
    public static final RenderPipeline FOG_TRACE_REFERENCE = fogFullscreen("fog_trace",GpuFormat.RGBA16_FLOAT,samplers("FogGeometry","Shadow0","Shadow1","Shadow2"),false,"FOG_REFERENCE");
    public static final RenderPipeline FOG_SHADOW_DEBUG = fogFullscreen("fog_trace",GpuFormat.RGBA8_UNORM,samplers("FogGeometry","Shadow0","Shadow1","Shadow2"),true);
    private static final BindGroupLayout FOG_HISTORY_INPUTS = samplers("FogGeometry","RawFog","PreviousFogGeometry","PreviousFog");
    public static final RenderPipeline FOG_RESOLVE = fogFullscreen("fog_resolve",GpuFormat.RGBA16_FLOAT,FOG_HISTORY_INPUTS,false);
    public static final RenderPipeline FOG_HISTORY_DEBUG = fogFullscreen("fog_resolve",GpuFormat.RGBA8_UNORM,FOG_HISTORY_INPUTS,true);
    public static final RenderPipeline FOG_DEBUG = fullscreen("fog_debug",GpuFormat.RGBA8_UNORM,samplers("FogInput"),false);
    public static final RenderPipeline FOG_COMPARE = fullscreen("fog_compare",GpuFormat.RGBA8_UNORM,samplers("FilteredFog","ReferenceFog","RawFog","ReferenceRawFog"),false);
    public static final RenderPipeline FOG_PROBE = fogFullscreen("fog_probe",GpuFormat.RGBA8_UNORM,samplers("RawFog"),false);
    public static final BindGroupLayout POST_INPUTS = BindGroupLayout.builder()
        .withUniform("UnderwaterShafts", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("SceneColor", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("SceneDepth", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("MaterialData", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("FogVolume", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterSurface", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("WaterDepth", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("Shadow0", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("Shadow1", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("Shadow2", UniformType.COMBINED_IMAGE_SAMPLER).build();

    public static final RenderPipeline SHADOW = shadow(false);
    public static final RenderPipeline SHADOW_INDIRECT = shadow(true);
    public static final RenderPipeline MATERIAL = material(false);
    public static final RenderPipeline MATERIAL_INDIRECT = material(true);
    public static final RenderPipeline MATERIAL_TEMPORAL = material(false,false,false,true);
    public static final RenderPipeline MATERIAL_TEMPORAL_INDIRECT = material(true,false,false,true);
    public static final RenderPipeline MATERIAL_REFLECTION = material(false,false,false,true,true);
    public static final RenderPipeline MATERIAL_REFLECTION_INDIRECT = material(true,false,false,true,true);
    private static final BindGroupLayout SOLID_TEMPORAL=BindGroupLayout.builder().withUniform("SolidTemporal",UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout SOLID_INPUTS=samplers("WaterSurface","WaterDepth","SolidGeometry","SolidResponse","SolidDepth","SolidScene","SolidMotion","SolidMip0","SolidMip1","SolidMip2","SolidMip3","SolidRaw","SolidHistory","SolidPreviousGeometry","SolidResolved","SolidResolvedGeometry");
    public static final RenderPipeline SOLID_TRACE=solid("solid_trace",false),SOLID_DEBUG=solid("solid_trace",true);
    public static final RenderPipeline SOLID_RESOLVE=solid("solid_resolve",false),SOLID_HISTORY_DEBUG=solid("solid_resolve",true);
    public static final RenderPipeline SOLID_APPLY=solid("solid_apply",false);
    private static RenderPipeline solid(String shader,boolean debug) {
        var builder=RenderPipeline.builder().withLocation(id("pipeline/"+shader+(debug?"_debug":"")))
            .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/"+shader))
            .withBindGroupLayout(SCENE).withBindGroupLayout(SKY_INPUT).withBindGroupLayout(SOLID_TEMPORAL).withBindGroupLayout(SOLID_INPUTS)
            .withCull(false).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(Optional.empty(),debug?GpuFormat.RGBA8_UNORM:GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL));
        if(debug)builder.withShaderDefine(shader.equals("solid_resolve")?"SOLID_HISTORY_DEBUG":"SOLID_DEBUG");
        if(shader.equals("solid_resolve") && !debug)builder.withColorTargetState(1,new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL));
        return builder.build();
    }
    public static final RenderPipeline FOLIAGE_MASK = material(false,true);
    public static final RenderPipeline FOLIAGE_MASK_INDIRECT = material(true,true);
    public static final RenderPipeline WIND_MASK = windMask(false),WIND_MASK_INDIRECT=windMask(true);
    public static final RenderPipeline WIND_PROBE = RenderPipeline.builder().withLocation(id("pipeline/wind_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/wind_probe"))
        .withBindGroupLayout(SCENE).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline UNDERWATER_SHAFT_TRACE = RenderPipeline.builder()
        .withLocation(id("pipeline/underwater_shafts"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/underwater_shafts"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(SKY_INPUT)
        .withBindGroupLayout(samplers("SceneDepth","WaterSurface","WaterDepth","WaterVolume","Shadow0","Shadow1","Shadow2"))
        .withBindGroupLayout(samplers("WaterCaustics","WaterCausticsFar","WaterCausticsShallow","WaterCausticsShallowFar","WaterCausticField","WaterCausticFieldFar"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline COMPOSITE = RenderPipeline.builder()
        .withLocation(id("pipeline/composite"))
        .withVertexShader(id("core/fullscreen"))
        .withFragmentShader(id("core/composite"))
        .withBindGroupLayout(samplers("WaterVolume","WaterCaustics","WaterCausticsFar","WaterCausticsShallow","WaterCausticsShallowFar","WaterCausticField","WaterCausticFieldFar")).withBindGroupLayout(SKY_INPUT)
        .withBindGroupLayout(SCENE).withBindGroupLayout(POST_INPUTS)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL)).build();
    public static final BindGroupLayout HDR_INPUT = samplers("HdrInput");
    public static final RenderPipeline DOWNSAMPLE = fullscreen("downsample",GpuFormat.RGBA16_FLOAT,HDR_INPUT,false);
    public static final RenderPipeline DOWNSAMPLE_FIRST = fullscreen("downsample",GpuFormat.RGBA16_FLOAT,HDR_INPUT,true);
    public static final RenderPipeline DOWNSAMPLE_TAA = fullscreen("downsample",GpuFormat.RGBA16_FLOAT,HDR_INPUT,true,"TAA_INPUT");
    public static final RenderPipeline EXPOSURE = fullscreen("exposure",GpuFormat.RG16_FLOAT,samplers("Luminance","PreviousExposure"),false);
    public static final RenderPipeline TONEMAP = fullscreen("tonemap",GpuFormat.RGBA8_UNORM,samplers("HdrInput","Exposure","Bloom0","Bloom1","Bloom2","Bloom3"),false);
    public static final RenderPipeline TONEMAP_TAA = fullscreen("tonemap",GpuFormat.RGBA8_UNORM,samplers("HdrInput","Exposure","Bloom0","Bloom1","Bloom2","Bloom3"),false,"TAA_INPUT");
    private static final BindGroupLayout SCENE_TEMPORAL=BindGroupLayout.builder().withUniform("SceneTemporal",UniformType.UNIFORM_BUFFER).build();
    private static final BindGroupLayout TAA_INPUTS=samplers("TaaInput","TaaDepth","TaaMotion","TaaHistory","WaterSurface","WaterDepth","TaaOpaqueColor","TaaSceneColor");
    public static final RenderPipeline TAA=temporalAa(false),TAA_DEBUG=temporalAa(true);
    public static final RenderPipeline TAA_MOTION_DEBUG=temporalAa(true,true);
    private static RenderPipeline temporalAa(boolean debug) {
        return temporalAa(debug,false);
    }
    private static RenderPipeline temporalAa(boolean debug,boolean motion) {
        var builder=RenderPipeline.builder().withLocation(id("pipeline/taa"+(motion?"_motion_debug":debug?"_debug":"")))
            .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/taa"))
            .withBindGroupLayout(SCENE_TEMPORAL).withBindGroupLayout(TAA_INPUTS).withCull(false)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(Optional.empty(),debug?GpuFormat.RGBA8_UNORM:GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL));
        if(debug) builder.withShaderDefine("TAA_DEBUG");
        if(motion) builder.withShaderDefine("TAA_MOTION_DEBUG");
        return builder.build();
    }
    public static final RenderPipeline HDR_PROBE = fullscreen("hdr_probe",GpuFormat.RGBA8_UNORM,samplers("HdrInput","Exposure","MaterialData"),false);
    public static final RenderPipeline HDR_DEBUG = fullscreen("hdr_debug",GpuFormat.RGBA8_UNORM,HDR_INPUT,false);
    public static final RenderPipeline MATERIAL_PROBE = RenderPipeline.builder()
        .withLocation(id("pipeline/material_probe")).withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/material_probe"))
        .withBindGroupLayout(MATERIAL_INPUT).withBindGroupLayout(BindGroupLayout.builder().withUniform("MaterialProbe",UniformType.UNIFORM_BUFFER).build())
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build();
    public static final RenderPipeline BRDF_PROBE = RenderPipeline.builder()
        .withLocation(id("pipeline/brdf_probe")).withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/brdf_probe"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build();
    public static final RenderPipeline SHADOW_DEBUG = RenderPipeline.builder()
        .withLocation(id("pipeline/shadow_debug")).withVertexShader(id("core/fullscreen"))
        .withFragmentShader(id("core/shadow_debug"))
        .withBindGroupLayout(BindGroupLayout.builder().withUniform("Shadow0",UniformType.COMBINED_IMAGE_SAMPLER).build())
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build();
    public static final RenderPipeline SHADOW_PROBE = RenderPipeline.builder()
        .withLocation(id("pipeline/shadow_probe")).withVertexShader(id("core/fullscreen"))
        .withFragmentShader(id("core/shadow_probe")).withBindGroupLayout(SCENE)
        .withBindGroupLayout(BindGroupLayout.builder().withUniform("Shadow0",UniformType.COMBINED_IMAGE_SAMPLER).build())
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build();

    private static RenderPipeline shadow(boolean indirect) {
        var builder = RenderPipeline.builder()
            .withLocation(id("pipeline/shadow" + (indirect ? "_indirect" : "")))
            .withVertexShader(id("core/shadow"))
            .withFragmentShader(id("core/shadow"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(SHADOW_MATRIX)
            .withBindGroupLayout(SCENE)
            .withVertexBinding(0, DefaultVertexFormat.BLOCK)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false);
        if (indirect) builder.withVertexBinding(1, DefaultVertexFormat.CHUNK_DATA_INSTANCED).withShaderDefine("MULTIDRAW_TERRAIN");
        else builder.withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION);
        return builder.build();
    }

    private static BindGroupLayout samplers(String... names) {
        var builder = BindGroupLayout.builder();
        for (var name : names) builder.withUniform(name,UniformType.COMBINED_IMAGE_SAMPLER);
        return builder.build();
    }
    private static RenderPipeline fullscreen(String shader, GpuFormat format, BindGroupLayout inputs, boolean first, String... defines) {
        var builder = RenderPipeline.builder().withLocation(id("pipeline/"+shader+(first ? "_first" : "")+(defines.length>0?"_taa":"")))
            .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/"+shader))
            .withBindGroupLayout(inputs).withCull(false)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(Optional.empty(),format,ColorTargetState.WRITE_ALL));
        if (first) builder.withShaderDefine("FIRST_DOWNSAMPLE");
        for(String define:defines) builder.withShaderDefine(define);
        if (shader.equals("exposure") || shader.equals("tonemap")) builder.withBindGroupLayout(SCENE);
        return builder.build();
    }
    private static RenderPipeline material(boolean indirect) {
        return material(indirect,false);
    }
    private static RenderPipeline windMask(boolean indirect) {
        return material(indirect,false,true);
    }
    private static RenderPipeline material(boolean indirect,boolean foliageMask) {
        return material(indirect,foliageMask,false);
    }
    private static RenderPipeline material(boolean indirect,boolean foliageMask,boolean windMask) {
        return material(indirect,foliageMask,windMask,false);
    }
    private static RenderPipeline material(boolean indirect,boolean foliageMask,boolean windMask,boolean temporal) {
        return material(indirect,foliageMask,windMask,temporal,false);
    }
    private static RenderPipeline material(boolean indirect,boolean foliageMask,boolean windMask,boolean temporal,boolean reflections) {
        var builder = RenderPipeline.builder().withLocation(id("pipeline/material"+(indirect ? "_indirect" : "")+(foliageMask ? "_foliage" : "")+(windMask ? "_wind" : "")+(temporal?"_temporal":"")+(reflections?"_reflection":"")))
            .withVertexShader(id("core/terrain")).withFragmentShader(id("core/material"))
            .withShaderDefine("MATERIAL_PASS").withBindGroupLayout(SCENE).withBindGroupLayout(MATERIAL_INPUT)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS).withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO).withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0,DefaultVertexFormat.BLOCK).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(new DepthStencilState(CompareOp.EQUAL,false)).withCull(false);
        if (indirect) builder.withVertexBinding(1,DefaultVertexFormat.CHUNK_DATA_INSTANCED).withShaderDefine("MULTIDRAW_TERRAIN");
        else builder.withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION);
        if(foliageMask) builder.withShaderDefine("FOLIAGE_MASK");
        if(windMask) builder.withShaderDefine("WIND_MASK");
        if(temporal) builder.withShaderDefine("TEMPORAL_MATERIAL")
            .withColorTargetState(1,new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL));
        if(reflections)builder.withShaderDefine("SOLID_MATERIAL").withBindGroupLayout(FOG_FORWARD).withBindGroupLayout(SKY_INPUT)
            .withColorTargetState(2,new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL))
            .withColorTargetState(3,new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL));
        return builder.build();
    }
    private static RenderPipeline waterSurface(boolean indirect) {
        var builder=RenderPipeline.builder().withLocation(id("pipeline/water_surface"+(indirect ? "_indirect" : "")))
            .withVertexShader(id("core/terrain")).withFragmentShader(id("core/water_surface"))
            .withShaderDefine("MATERIAL_PASS").withBindGroupLayout(SCENE).withBindGroupLayout(WATER_TEMPORAL)
            .withBindGroupLayout(samplers("OpaqueDepth"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS).withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG).withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO)
            .withVertexBinding(0,DefaultVertexFormat.BLOCK).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true)).withCull(false);
        if(indirect) builder.withVertexBinding(1,DefaultVertexFormat.CHUNK_DATA_INSTANCED).withShaderDefine("MULTIDRAW_TERRAIN");
        else builder.withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION);
        return builder.build();
    }
    private static RenderPipeline waterPlanar(boolean indirect) {
        var builder=RenderPipeline.builder().withLocation(id("pipeline/water_planar"+(indirect ? "_indirect" : "")))
            .withVertexShader(id("core/water_planar")).withFragmentShader(id("core/water_planar"))
            .withBindGroupLayout(SCENE).withBindGroupLayout(LOCAL_LIGHTING).withBindGroupLayout(CLOUD_SHADOW_INPUT).withBindGroupLayout(SKY_INPUT).withBindGroupLayout(WATER_PLANAR_MATRIX).withBindGroupLayout(SHADOW_DEPTH_INPUT).withBindGroupLayout(MATERIAL_INPUT)
            .withBindGroupLayout(samplers("WaterVolume","WaterCaustics","WaterCausticsFar","WaterCausticsShallow","WaterCausticsShallowFar","WaterCausticField","WaterCausticFieldFar"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS).withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0).withBindGroupLayout(samplers("Sampler2"))
            .withVertexBinding(0,DefaultVertexFormat.BLOCK).withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
            .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true));
        if(indirect) builder.withVertexBinding(1,DefaultVertexFormat.CHUNK_DATA_INSTANCED).withShaderDefine("MULTIDRAW_TERRAIN");
        else builder.withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION);
        return builder.build();
    }
    private static RenderPipeline waterFullscreen(String shader,BindGroupLayout inputs,boolean debug) {
        var builder=RenderPipeline.builder().withLocation(id("pipeline/"+shader+(debug ? "_debug" : "")))
            .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/"+shader))
            .withBindGroupLayout(SCENE).withBindGroupLayout(WATER_TEMPORAL).withBindGroupLayout(inputs)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(Optional.empty(),debug ? GpuFormat.RGBA8_UNORM : GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL));
        if(shader.equals("water_trace")) builder.withBindGroupLayout(WATER_PLANAR_MATRIX).withBindGroupLayout(SKY_INPUT);
        if(debug) builder.withShaderDefine(shader.equals("water_trace") ? "WATER_SOURCE_DEBUG" : "WATER_DEBUG");
        return builder.build();
    }
    private static RenderPipeline fogFullscreen(String shader,GpuFormat format,BindGroupLayout inputs,boolean debug) {
        return fogFullscreen(shader,format,inputs,debug,null);
    }
    private static RenderPipeline fogFullscreen(String shader,GpuFormat format,BindGroupLayout inputs,boolean debug,String variant) {
        var builder=RenderPipeline.builder().withLocation(id("pipeline/"+shader+(debug ? "_debug" : "")+(variant == null ? "" : "_"+variant.toLowerCase(java.util.Locale.ROOT))))
            .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/"+shader))
            .withBindGroupLayout(SCENE).withBindGroupLayout(FOG_TEMPORAL).withBindGroupLayout(inputs)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(Optional.empty(),format,ColorTargetState.WRITE_ALL));
        if(debug) builder.withShaderDefine(shader.equals("fog_trace") ? "FOG_SHADOW_DEBUG" : "FOG_DEBUG");
        if(shader.equals("fog_volume_light")) builder.withBindGroupLayout(LOCAL_LIGHTING).withBindGroupLayout(CLOUD_SHADOW_INPUT);
        if(variant != null) builder.withShaderDefine(variant);
        return builder.build();
    }
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("aster_vulkan", path); }
    public static List<RenderPipeline> all() { return List.of(SOLID_TRACE,SOLID_DEBUG,SOLID_RESOLVE,SOLID_HISTORY_DEBUG,SOLID_APPLY,MATERIAL_REFLECTION,MATERIAL_REFLECTION_INDIRECT,TAA,TAA_DEBUG,TAA_MOTION_DEBUG,MATERIAL_TEMPORAL,MATERIAL_TEMPORAL_INDIRECT,DOWNSAMPLE_TAA,TONEMAP_TAA,UNDERWATER_SHAFT_TRACE,WATER_CAUSTIC_RECONSTRUCTION_PROBE,WATER_CAUSTIC_SHALLOW_MAP,WATER_CAUSTIC_SHALLOW_PREFILTER,WATER_CAUSTIC_SHALLOW_FILTER,WATER_CAUSTIC_SHALLOW_DENSITY_PROBE,WATER_CAUSTIC_FIELD,WATER_CAUSTIC_FIELD_PROBE,WATER_CAUSTIC_PREFILTER,WATER_CAUSTIC_MAP,WATER_CAUSTIC_FILTER,WATER_CAUSTIC_DENSITY_PROBE,SKY, SKY_CLOUDS, CLOUD_SHADOW, LOCAL_LIGHT_PROBE, SHADOW, SHADOW_INDIRECT, MATERIAL, MATERIAL_INDIRECT, FOLIAGE_MASK, FOLIAGE_MASK_INDIRECT, COMPOSITE, DOWNSAMPLE, DOWNSAMPLE_FIRST, EXPOSURE, TONEMAP, HDR_PROBE, HDR_DEBUG, MATERIAL_PROBE,BRDF_PROBE,SHADOW_DEBUG,SHADOW_PROBE,WATER_SURFACE,WATER_SURFACE_INDIRECT,WATER_PLANAR,WATER_PLANAR_INDIRECT,WATER_TRACE_DEBUG,WATER_TRACE,WATER_RESOLVE,WATER_DEBUG,WATER_CONTACT_PROBE,FOG_GEOMETRY,FOG_TRACE,FOG_RESOLVE,FOG_HISTORY_DEBUG,FOG_DEBUG,FOG_PROBE,FOG_SHADOW_DEBUG,FOG_TRACE_REFERENCE,FOG_COMPARE,FOG_VOLUME_LIGHT,FOG_VOLUME_HISTORY,FOG_VOLUME_INTEGRATE,FOG_VOLUME_PROBE,FOG_VOLUME_DEBUG,FOG_VOLUME_HISTORY_DEBUG,FOG_OPAQUE_RECOVER); }
    public static final RenderPipeline WATER_LIGHTING_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_lighting_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_lighting_probe"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(SHADOW_DEPTH_INPUT).withBindGroupLayout(SKY_INPUT).withBindGroupLayout(samplers("WaterVolume","WaterCaustics","WaterCausticsFar","WaterCausticsShallow","WaterCausticsShallowFar","WaterCausticField","WaterCausticFieldFar"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_REPLAY_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_replay_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_replay_probe"))
        .withBindGroupLayout(samplers("ActualHdr","ClearedReplayHdr"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_RECEIVER_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_receiver_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_receiver_probe"))
        .withBindGroupLayout(SCENE).withBindGroupLayout(SHADOW_DEPTH_INPUT).withBindGroupLayout(SKY_INPUT)
        .withBindGroupLayout(samplers("SceneDepth","MaterialData","WaterSurface","WaterDepth","WaterVolume","WaterCaustics","WaterCausticsFar","WaterCausticsShallow","WaterCausticsShallowFar"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_RECEIVER_PROVENANCE_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_receiver_provenance_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_receiver_probe"))
        .withShaderDefine("RECEIVER_DEPTH_PROVENANCE")
        .withBindGroupLayout(SCENE).withBindGroupLayout(SHADOW_DEPTH_INPUT).withBindGroupLayout(SKY_INPUT)
        .withBindGroupLayout(samplers("SceneDepth","MaterialData","WaterSurface","WaterDepth","WaterVolume","WaterCaustics","WaterCausticsFar","WaterCausticsShallow","WaterCausticsShallowFar","OpaqueDepth"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    public static final RenderPipeline WATER_WAVE_PROBE = RenderPipeline.builder().withLocation(id("pipeline/water_wave_probe"))
        .withVertexShader(id("core/fullscreen")).withFragmentShader(id("core/water_wave_probe"))
        .withBindGroupLayout(SCENE).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
    private AsterPipelines() {}
}
