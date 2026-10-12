#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:fog_volume.glsl>
uniform sampler2D FogVolume;
uniform sampler2D FoggedOpaque;
uniform sampler2D OpaqueDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec3 color=texture(FoggedOpaque,texCoord).rgb;
    if(SceneInfo.w > .5 && SceneInfo.z < .5 && !verificationFlag(1) && !verificationFlag(128) && !verificationFlag(134217728)) {
        float depth=texture(OpaqueDepth,texCoord).r;
        vec4 p=InvViewProjection*vec4(texCoord*2.0-1.0,depth,1);
        float distance=depth <= .000001 ? FOG_RANGE : length(p.xyz/p.w);
        vec4 fog=fogAtDepth(FogVolume,texCoord,distance);
        color=max(color-fog.rgb,vec3(0))/max(fog.a,.0001);
    }
    fragColor=vec4(color,1);
}
