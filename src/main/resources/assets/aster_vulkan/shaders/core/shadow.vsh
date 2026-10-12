#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:wind.glsl>
#ifndef MULTIDRAW_TERRAIN
#include <minecraft:chunksection.glsl>
#endif
layout(std140) uniform ShadowProjection { mat4 LightMatrix; };
layout(location=0) in vec3 Position;
layout(location=1) in vec4 Color;
layout(location=2) in vec2 UV0;
layout(location=3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location=4) in ivec3 ChunkPosition;
layout(location=5) in float ChunkVisibility;
#endif
layout(location=0) out vec2 texCoord;
layout(location=1) flat out float casterLeaf;
void main() {
    vec3 p = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    p=windPosition(p,Color.a);
    gl_Position = LightMatrix * vec4(p, 1.0);
    texCoord = UV0;
    casterLeaf=windLeaf(windTag(Color.a)) ? 1.0 : 0.0;
}
