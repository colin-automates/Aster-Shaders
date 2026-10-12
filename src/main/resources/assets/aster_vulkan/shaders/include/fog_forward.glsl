#include <aster_vulkan:fog_volume.glsl>
uniform sampler2D FogVolume;
vec4 fogSurface(vec4 color) {
    if(SceneInfo.w < .5 || SceneInfo.z > .5 || verificationFlag(1) || verificationFlag(128) || verificationFlag(134217728)) return color;
    vec2 uv=gl_FragCoord.xy/SceneInfo.xy;
    vec4 position=InvViewProjection*vec4(uv*2.0-1.0,gl_FragCoord.z,1);
    vec4 fog=fogAtDepth(FogVolume,uv,length(position.xyz/position.w));
    #ifdef OIT_ACCUMULATE
    color.rgb=color.rgb*fog.a+fog.rgb*color.a;
    #else
    color.rgb=color.rgb*fog.a+fog.rgb;
    #endif
    return color;
}
