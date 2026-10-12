#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
#include <minecraft:terrainglobals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <aster_vulkan:scene.glsl>
uniform sampler2D Sampler0;
layout(location=0) in vec2 texCoord;
layout(location=1) flat in float casterLeaf;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 texel=verificationFlag(16) ? texture(Sampler0,texCoord)
        : UseRgss == 1 ? sampleRGSS(Sampler0,texCoord,1.0/TextureSize) : sampleNearest(Sampler0,texCoord,1.0/TextureSize);
    if (texel.a < 0.5) discard;
    // Record the nearest accepted caster alongside its unchanged depth.
    // Solid terrain and entity shadow shaders write zero to this marker.
    float leafDensity=dot(texel.rgb,vec3(.2126,.7152,.0722));
    fragColor = vec4(casterLeaf*leafDensity*leafDensity,0,0,1);
}
