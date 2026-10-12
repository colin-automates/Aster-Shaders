#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:lighting.glsl>
#include <aster_vulkan:fog.glsl>
#include <aster_vulkan:fog_temporal.glsl>
#include <aster_vulkan:shadows.glsl>
uniform sampler2D FogGeometry;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    if(SceneInfo.w < .5 || SceneInfo.z > .5) { fragColor=vec4(0,0,0,1);return; }
    vec2 geometry=texture(FogGeometry,texCoord).rg;
    float distance=min(abs(geometry.r),FogSettings.w),stepLength=distance/16.0;
    vec3 ray=fogRay(texCoord,InvViewProjection);
    float jitter=fract(dot(gl_FragCoord.xy,vec2(.754877666,.569840296))+FogCameraDeltaFrame.w*.618033989);
    vec3 ambient=fogAmbientRadiance(displayToLinear(AtmosphereColor.rgb),ray);
    vec3 sunlight=terrainSunRadiance()*(verificationFlag(64) ? .15 : .04)*SunDirection.w*fogPhase(dot(ray,SunDirection.xyz));
    vec3 scattering=vec3(0);float transmittance=1.0,litExtinction=0.0;
    ivec2 pixel=ivec2(gl_FragCoord.xy);
    int tapPhase=(pixel.x*13+pixel.y*7+int(FogCameraDeltaFrame.w)*4)%9;
    for(int i=0;i<16;i++) {
        vec3 p=ray*(float(i)+jitter)*stepLength;
        float segment=fogSegmentTransmittance(fogDensity(p,geometry.g),stepLength);
#ifdef FOG_REFERENCE
        float visibility=shadowAt(p,.0004);
#else
        int tapIndex=(tapPhase+i*5)%9;
        float visibility=0.0;
        for(int j=0;j<3;j++) {
            int stratifiedTap=(tapIndex+j*4)%9;
            vec2 tap=vec2(stratifiedTap%3-1,stratifiedTap/3-1);
            visibility+=fogShadowAt(p,.0004,tap)/3.0;
        }
#endif
        litExtinction+=transmittance*(1.0-segment)*visibility;
        scattering+=transmittance*(1.0-segment)*(ambient+sunlight*visibility);
        transmittance*=segment;
    }
#ifdef FOG_SHADOW_DEBUG
    float visibility=litExtinction/max(1.0-transmittance,.00001);
    fragColor=vec4(1.0-visibility,visibility,1.0-transmittance,1);
#else
    fragColor=vec4(max(scattering,vec3(0)),clamp(transmittance,0.0,1.0));
#endif
}
