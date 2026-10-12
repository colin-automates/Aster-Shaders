#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:wind.glsl>
layout(location=0) out vec4 fragColor;
void main() {
    int i=int(gl_FragCoord.x);
    vec3 p=vec3(float(i%16)*7.37-55.1,65.0+float(i%9),float(i/16)*9.13-71.7);
    float t=12.0+float(i)*.37,rain=float(i%2);
    vec3 root=foliageWindOffset(p,0.0,false,t,rain);
    vec3 plant=foliageWindOffset(p,2.0,false,t,rain),leaf=foliageWindOffset(p,0.0,true,t,rain);
    bool anchored=length(root) == 0.0;
    bool bounded=length(plant) < .63 && length(leaf) < .13;
    bool tags=windTag(1.0/255.0) == 1 && windTag(32.0/255.0) == 32 && windTag(63.0/255.0) == 63
        && windTag(96.0/255.0) == 96 && !windTagged(128.0/255.0) && !windTagged(254.0/255.0) && !windTagged(1.0);
    tags=tags && windLeaf(96) && windLeaf(99) && !windLeaf(100)
        && !windTagged(64.0/255.0) && terrainTintTagged(67.0/255.0)
        && abs(nativeFaceShade(64.0/255.0)-.5)<.00001 && abs(nativeFaceShade(65.0/255.0)-.6)<.00001
        && abs(nativeFaceShade(66.0/255.0)-.8)<.00001 && abs(nativeFaceShade(67.0/255.0)-1.0)<.00001
        && abs(nativeFaceShade(96.0/255.0)-.5)<.00001 && abs(nativeFaceShade(99.0/255.0)-1.0)<.00001
        && nativeFaceShade(1.0)==1.0;
    bool loop=length(foliageWindOffset(p,2.0,false,1199.9999,rain)-foliageWindOffset(p,2.0,false,.0001,rain)) < .001
        && length(foliageWindOffset(p,0.0,true,1199.9999,rain)-foliageWindOffset(p,0.0,true,.0001,rain)) < .001;
    bool weather=length(foliageWindOffset(p,1.0,false,t,1.0)-foliageWindOffset(p,1.0,false,t,0.0)*1.5) < .00001;
    bool moving=length(foliageWindOffset(p,1.0,false,t,0.0)-foliageWindOffset(p,1.0,false,t+.5,0.0)) > .00001
        && length(foliageWindOffset(p,0.0,true,t,0.0)-foliageWindOffset(p,0.0,true,t+.5,0.0)) > .00001;
    fragColor=gl_FragCoord.y < 1.0 ? vec4(anchored ? 1.0 : 0.0,bounded ? 1.0 : 0.0,tags ? 1.0 : 0.0,1)
        : vec4(loop ? 1.0 : 0.0,weather ? 1.0 : 0.0,moving ? 1.0 : 0.0,1);
}
