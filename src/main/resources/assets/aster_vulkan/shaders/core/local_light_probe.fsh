#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:local_lighting.glsl>
layout(location=0) out vec4 fragColor;
void main() {
    int sampleIndex=int(gl_FragCoord.x);
    vec3 left=vec3(46,66,-1),right=vec3(53,66,-1),up=vec3(0,1,0);
    vec3 radiance;
    if(sampleIndex==0)radiance=sourceRadianceAt(left,up);
    else if(sampleIndex==1)radiance=sourceRadianceAt(right,up);
    else if(sampleIndex==2)radiance=sourceRadianceAt(vec3(49.5,66,-1),up);
    else if(sampleIndex==3)radiance=heldRadianceAt(left,vec3(0,0,1));
    else if(sampleIndex==4)radiance=heldRadianceAt(right,vec3(0,0,1));
    else radiance=localLightAt(left).rgb;
    fragColor=vec4(clamp(radiance*.25,0.0,1.0),1);
}
