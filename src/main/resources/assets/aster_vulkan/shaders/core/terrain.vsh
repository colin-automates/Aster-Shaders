#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:wind.glsl>
#ifndef MULTIDRAW_TERRAIN
#include <minecraft:chunksection.glsl>
#endif
layout(location=0) in vec3 Position;
layout(location=1) in vec4 Color;
layout(location=2) in vec2 UV0;
layout(location=3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location=4) in ivec3 ChunkPosition;
layout(location=5) in float ChunkVisibility;
#endif
#if !defined(OIT_ALPHA_ONLY) && !defined(MATERIAL_PASS)
uniform sampler2D Sampler2;
#endif
layout(location=0) out float sphericalVertexDistance;
layout(location=1) out float cylindricalVertexDistance;
layout(location=2) out vec4 vertexColor;
layout(location=3) out vec2 texCoord0;
layout(location=4) out float chunkVisibility;
layout(location=5) out vec3 relativePosition;
layout(location=6) out float waterMaterial;
layout(location=7) out vec2 materialLight;
layout(location=8) out vec3 albedoTint;
layout(location=9) out vec3 nativeLight;
layout(location=10) out float waterShoreWeight;
layout(location=11) out vec2 windMetadata;
layout(location=12) out float faceShade;
#ifdef TEMPORAL_MATERIAL
layout(location=13) out vec3 previousWindDelta;
#endif
// The metadata replay must produce exactly the same depth as the world draw.
invariant gl_Position;
void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    bool taggedWater=Color.a > .5 && Color.a < .999;
    vec4 tint=vec4(Color.rgb,(taggedWater || terrainTintTagged(Color.a)) ? 1.0 : Color.a);
    int foliageTag=windTag(Color.a);
    faceShade=nativeFaceShade(Color.a);
    windMetadata=vec2(windTagged(Color.a) ? (windLeaf(foliageTag) ? 2.0 : 1.0) : 0.0,
                      foliageTag >= 1 && foliageTag <= 63 ? float(foliageTag-1)/62.0 : 0.0);
#ifdef TEMPORAL_MATERIAL
    previousWindDelta=vec3(0);
    if(AsterSettings.x>.5 && windTagged(Color.a) && !verificationFlag(268435456)) {
        vec3 world=pos+CameraWorldTime.xyz;
        float height=float(foliageTag-1)/31.0;
        previousWindDelta=foliageWindOffset(world,height,windLeaf(foliageTag),SkyTiming.z,SkyTiming.w)
                         -foliageWindOffset(world,height,windLeaf(foliageTag),SkyTiming.y,1.0-AtmosphereColor.a);
    }
#endif
    pos=windPosition(pos,Color.a);
    waterMaterial = (taggedWater || insideSprite(UV0, WaterStillRect) || insideSprite(UV0, WaterFlowRect)) ? 1.0 : 0.0;
    waterShoreWeight=taggedWater ? clamp((Color.a*255.0-128.0)/126.0,0.0,1.0) : 1.0;
    if(verificationFlag(8192)) waterShoreWeight=1.0;
    if (AsterSettings.x > 0.5 && waterMaterial > 0.5) {
        // Keep bottom vertices anchored; the block grid limits geometric wave detail.
        // Native fluid tops are lowered by 0.001. Adding that epsilon here
        // wraps a full-height top to zero and incorrectly freezes its waves.
        float top = smoothstep(0.15, 0.85, fract(Position.y));
        pos.y += waterWave(pos.xz + CameraWorldTime.xz) * AsterSettings.y * top * waterShoreWeight;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    relativePosition = pos;
#if !defined(OIT_ALPHA_ONLY) && !defined(MATERIAL_PASS)
    nativeLight = sample_lightmap(Sampler2, UV2).rgb;
    vertexColor = tint * vec4(nativeLight,1);
#else
    vertexColor = tint;
    nativeLight = vec3(1);
#endif
    texCoord0 = UV0;
    albedoTint = Color.rgb;
    materialLight = clamp(vec2(UV2)/240.0,0.0,1.0);
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist-16.0)/16.0, 0.0, 1.0));
}
