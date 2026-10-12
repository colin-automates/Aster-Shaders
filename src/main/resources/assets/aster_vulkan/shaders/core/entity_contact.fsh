#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <aster_vulkan:scene.glsl>
#ifndef OIT_ALPHA_ONLY
#include <aster_vulkan:fog_forward.glsl>
#endif
vec3 entityLinear(vec3 c) { return mix(c/12.92,pow(max((c+.055)/1.055,vec3(0)),vec3(2.4)),step(vec3(.04045),c)); }

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color.rgb = entityLinear(color.rgb);
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(entityLinear(FogColor.rgb) * color.a, FogColor.a);
    #else
    color.rgb = entityLinear(color.rgb);
    vec4 fogColor = vec4(entityLinear(FogColor.rgb),FogColor.a);
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 color = texture(Sampler0, clamp(texCoord0, 0.0, 1.0));
    color *= vertexColor * ColorModulator;
    // Keep a subtle ambient contact cue, without doubling the directional
    // silhouette. Alpha and accumulation stages use exactly the same opacity.
    if (SceneInfo.w > .5 && !verificationFlag(524288)) {
        vec2 radial=(texCoord0-.5)*2.0;
        color.a *= verificationFlag(67108864) ? .30 : .20*exp(-1.5*dot(radial,radial));
    }
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = fogSurface(calculateFinalColor(color));
    #endif
}
