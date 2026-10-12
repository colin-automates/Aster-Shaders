#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#define SKY_NO_CLOUD_TEXTURE
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:water_temporal.glsl>
uniform sampler2D OpaqueDepth;
layout(location=5) in vec3 relativePosition;
layout(location=6) in float waterMaterial;
layout(location=10) in float waterShoreWeight;
layout(location=0) out vec4 fragColor;
void main() {
    vec3 geometry=normalize(cross(dFdx(relativePosition),dFdy(relativePosition)));
    if(dot(geometry,-relativePosition) < 0.0) geometry=-geometry;
    float footprint=max(length(dFdx(relativePosition).xz),length(dFdy(relativePosition).xz));
    if(!verificationFlag(512) && !verificationFlag(4096)) footprint*=WaterSettings.x/SceneInfo.x;
    vec3 normal=waterShadingNormal(relativePosition,geometry,footprint,waterShoreWeight);
    if(waterMaterial < .5 || (SceneInfo.z > .5 && verificationFlag(512))) discard;
    vec2 uv=gl_FragCoord.xy/WaterSettings.xy;
    if(gl_FragCoord.z < texture(OpaqueDepth,uv).r) discard;
    // Reverse-Z writes select the nearest visible water, independently of the
    // native transparent sort/OIT stages; glass/lava never enter this history.
    fragColor=vec4(normal,1.0+waterShoreWeight);
}
