#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:cloud_density.glsl>
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    if(SkySun.y<.08) {fragColor=vec4(1);return;}
    vec3 world=CameraWorldTime.xyz+vec3((texCoord.x-.5)*768.0,0,(texCoord.y-.5)*768.0);
    float start=max(0.0,(180.0-world.y)/SkySun.y);
    float end=max(start,(425.0-world.y)/SkySun.y);
    float stepSize=(end-start)/32.0;
    float rain=clamp((1.0-AtmosphereColor.a)*2.0,0.0,1.0);
    vec2 drift=vec2(sin(SkyTiming.x),cos(SkyTiming.x))*18.0;
    float optical=0.0;
    for(int i=0;i<32;i++) {
        vec3 position=world+SkySun.xyz*(start+(float(i)+.5)*stepSize);
        vec3 p=vec3(position.x/220.0+drift.x,(position.y-180.0)/245.0,position.z/220.0+drift.y);
        float footprint=max(stepSize*length(SkySun.xz)/220.0,stepSize*SkySun.y/245.0);
        optical+=cloudDensity(p,rain,footprint)*stepSize*.012;
    }
    fragColor=vec4(vec3(exp(-optical)),1);
}
