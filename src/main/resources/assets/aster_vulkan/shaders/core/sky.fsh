#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:sky.glsl>
#include <aster_vulkan:fog_volume.glsl>
uniform sampler2D FogVolume;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec3 color=asterSkyRadiance(cameraRay(texCoord,InvViewProjection));
    if(SceneInfo.w > .5 && SceneInfo.z < .5 && !verificationFlag(1)) {
        vec4 fog=fogAtDepth(FogVolume,texCoord,FOG_RANGE);color=color*fog.a+fog.rgb;
    }
    fragColor=vec4(color,1.0);
}
