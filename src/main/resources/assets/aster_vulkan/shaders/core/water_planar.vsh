#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <aster_vulkan:water_planar.glsl>
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:wind.glsl>
#ifndef MULTIDRAW_TERRAIN
#include <minecraft:chunksection.glsl>
#endif
uniform sampler2D Sampler2;
layout(location=0) in vec3 Position;
layout(location=1) in vec4 Color;
layout(location=2) in vec2 UV0;
layout(location=3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location=4) in ivec3 ChunkPosition;
layout(location=5) in float ChunkVisibility;
#endif
layout(location=0) out vec2 texCoord;
layout(location=1) out vec3 tint;
layout(location=2) out vec3 relativePosition;
layout(location=3) out vec3 nativeLight;
layout(location=4) out vec2 lightLevels;
layout(location=5) out float plantQuad;
layout(location=6) out float faceShade;
void main() {
    relativePosition=Position+(ChunkPosition-CameraBlockPos)+CameraOffset;
    relativePosition=windPosition(relativePosition,Color.a);
    gl_Position=PlanarViewProjection*vec4(relativePosition,1);
    texCoord=UV0;tint=Color.rgb;
    int tag=windTag(Color.a);plantQuad=windTagged(Color.a) && !windLeaf(tag) ? 1.0 : 0.0;
    faceShade=nativeFaceShade(Color.a);
    nativeLight=sample_lightmap(Sampler2,UV2).rgb;lightLevels=clamp(vec2(UV2)/240.0,0.0,1.0);
}
