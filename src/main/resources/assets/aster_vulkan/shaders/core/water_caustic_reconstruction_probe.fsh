#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water_caustic_source.glsl>
layout(std140) uniform CausticParameters { vec4 CausticRender; };
uniform sampler2D CausticInput;
uniform sampler2D CausticReference;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
vec4 probePixelDensity(sampler2D density,vec2 uv,float footprint,float extent) {
    float texel=extent/float(textureSize(density,0).x);
    float support=max(footprint*footprint-texel*texel,0.0);
    vec4 center=textureLod(density,uv,0.0);
    if(support<=1e-8) return center;
    vec2 offset=vec2(.5*sqrt(support)/extent,0.0);
    return center/3.0+(textureLod(density,uv+offset,0.0)
        +textureLod(density,uv-offset,0.0)
        +textureLod(density,uv+offset.yx,0.0)
        +textureLod(density,uv-offset.yx,0.0))/6.0;
}
void main() {
    bool far=CausticRender.w>=2.0;
    float extent=far?256.0:64.0,footprint=far?.125:.035;
    float depth=CausticRender.z;
    vec2 uv=.375+texCoord*.25;
    vec2 origin=WaterVolumeInfo.xz-(far?vec2(96):vec2(0));
    vec2 world=origin+uv*extent;
    vec3 incident=-normalize(SkySun.xyz);
    vec3 flatRay=refract(incident,vec3(0,1,0),1.0/1.333);
    vec2 entry=world-flatRay.xz/max(-flatRay.y,.2)*depth;
    float rain=1.0-AtmosphereColor.a;
    // Match the currently used beam estimate: near receiver footprint .035,
    // or the far source-filter footprint .0625. The direct density below
    // carries the same source height/slopes as the original cached planes.
    vec2 slope=waterWaveField(entry,CameraWorldTime.w,rain).yz*AsterSettings.y
        +waterRippleField(entry,CameraWorldTime.w,rain,far?.0625:.035*1.2).yz;
    vec3 ray=refract(incident,normalize(vec3(-slope.x,1,-slope.y)),1.0/1.333);
    vec2 beam=ray.xz/max(-ray.y,.2);
    vec4 planes=vec4(.5,1.5,3.5,5.5);
    int lower=depth<1.5?0:depth<3.5?1:2;
    float low=planes[lower],high=planes[lower+1];
    float blend=clamp((depth-low)/(high-low),0.0,1.0);
    vec4 lowDensity=probePixelDensity(CausticInput,uv+beam*(low-depth)/extent,footprint,extent);
    vec4 highDensity=probePixelDensity(CausticInput,uv+beam*(high-depth)/extent,footprint,extent);
    float warped=mix(lowDensity[lower],highDensity[lower+1],blend);
    vec4 plain=probePixelDensity(CausticInput,uv,footprint,extent);
    float unwarped=mix(plain[lower],plain[lower+1],blend);
    float actual=probePixelDensity(CausticReference,uv,footprint,extent).r;
    fragColor=vec4(clamp(vec3(actual,warped,unwarped)/8.0,0.0,1.0),1);
}
