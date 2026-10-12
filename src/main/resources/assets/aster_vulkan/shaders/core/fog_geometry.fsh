#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:fog.glsl>
#include <aster_vulkan:fog_temporal.glsl>
uniform sampler2D SceneDepth;
uniform sampler2D MaterialData;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec2 fragColor;
void main() {
    // Conservative nearest geometry from the full-resolution footprint.
    ivec2 size=textureSize(SceneDepth,0),base=ivec2(gl_FragCoord.xy)*2;
    float depth=0.0,skyAccess=1.0;
    for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        ivec2 pixel=clamp(base+ivec2(x,y),ivec2(0),size-1);
        float d=texelFetch(SceneDepth,pixel,0).r;
        if(d > depth) {
            depth=d;
            vec4 material=texelFetch(MaterialData,pixel,0);
            skyAccess=material.a > .01 ? mix(.1,1.0,material.r) : 1.0;
        }
    }
    if(texture(WaterSurface,texCoord).a > .5) {
        float waterDepth=texture(WaterDepth,texCoord).r;
        if(waterDepth > depth) { depth=waterDepth;skyAccess=1.0; }
    }
    if(depth <= .000001) { fragColor=vec2(-FogSettings.w,1);return; }
    vec4 p=InvViewProjection*vec4(texCoord*2.0-1.0,depth,1);
    fragColor=vec2(length(p.xyz/p.w),skyAccess);
}
