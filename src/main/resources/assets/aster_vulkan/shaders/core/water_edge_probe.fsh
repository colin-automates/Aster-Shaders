#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:water_refraction.glsl>
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    int side=int(gl_FragCoord.y)%4;
    bool horizontal=side<2,farEdge=(side%2)==1;
    vec2 padding=.5/SceneInfo.xy;
    float d=(horizontal ? padding.x : padding.y)+texCoord.x*.05;
    vec2 uv=vec2(.5);
    if(horizontal) uv.x=farEdge ? 1.0-d : d;
    else uv.y=farEdge ? 1.0-d : d;
    bool previous=verificationFlag(33554432);
    vec2 parallel=horizontal ? vec2(0,.018) : vec2(.018,0);
    vec2 limited=waterRefractionOffset(uv,parallel,SceneInfo.xy,previous);
    bool parallelStable=length(limited-parallel)<.000001;
    vec2 outward=horizontal ? vec2(farEdge ? .025 : -.025,.018) : vec2(.018,farEdge ? .025 : -.025);
    vec2 sampleUv=uv+waterRefractionOffset(uv,outward,SceneInfo.xy,previous);
    bool bounded=all(greaterThanEqual(sampleUv,padding-.000001)) && all(lessThanEqual(sampleUv,1.0-padding+.000001));
    vec2 stepUv=horizontal ? vec2(.00005,0) : vec2(0,.00005);
    vec2 lo=uv-stepUv,hi=uv+stepUv;
    vec2 loSample=lo+waterRefractionOffset(lo,outward,SceneInfo.xy,previous);
    vec2 hiSample=hi+waterRefractionOffset(hi,outward,SceneInfo.xy,previous);
    float slope=horizontal ? (hiSample.x-loSample.x)/.0001 : (hiSample.y-loSample.y)/.0001;
    fragColor=vec4(parallelStable ? 1.0 : 0.0,bounded ? 1.0 : 0.0,slope>.24 ? 1.0 : 0.0,1);
}
