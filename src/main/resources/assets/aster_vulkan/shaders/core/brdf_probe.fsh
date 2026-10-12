#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:pbr.glsl>
layout(location=0) out vec4 fragColor;
void main() {
    vec3 n=vec3(0,0,1),front=vec3(0,0,1),back=vec3(0,0,-1);
    vec3 positive=directBrdf(vec3(.5),.4,0.0,n,front,front);
    vec3 backEye=directBrdf(vec3(.5),.4,0.0,n,back,front);
    vec3 backLight=directBrdf(vec3(.5),.4,0.0,n,front,back);
    fragColor=vec4(length(positive) > .01 ? 1 : 0,length(backEye) < 1e-6 ? 1 : 0,length(backLight) < 1e-6 ? 1 : 0,1);
}
