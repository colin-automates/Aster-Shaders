#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
uniform sampler2D Luminance;
uniform sampler2D PreviousExposure;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec2 fragColor;
void main() {
    float luminance = exp2(texture(Luminance,vec2(0.5)).a);
    float target = clamp(0.14/max(luminance,0.004),PostSettings.z,PostSettings.w);
    float previous = texture(PreviousExposure,vec2(0.5)).r;
    float rate = target < previous ? 2.5 : 0.8;
    float weight = PostSettings.y > 0.5 ? 1.0 : 1.0-exp(-PostSettings.x*rate);
    fragColor = vec2(mix(previous,target,weight),luminance);
}
