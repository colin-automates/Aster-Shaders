#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:lighting.glsl>
#include <aster_vulkan:cloud_shadow.glsl>
#include <aster_vulkan:local_lighting.glsl>
#include <aster_vulkan:fog.glsl>
#include <aster_vulkan:fog_temporal.glsl>
#include <aster_vulkan:fog_volume.glsl>
#include <aster_vulkan:fog_environment.glsl>
#include <aster_vulkan:shadows.glsl>
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    if(SceneInfo.w < .5 || SceneInfo.z > .5) {fragColor=vec4(0);return;}
    ivec2 tileSize=ivec2(FogSettings.xy);
    ivec3 cell=fogAtlasCell(ivec2(gl_FragCoord.xy),tileSize*8);
    vec2 uv=(vec2(cell.xy)+.5)/vec2(tileSize);
    vec3 ray=fogRay(uv,InvViewProjection);
    float noise=fract(dot(vec3(cell),vec3(.754877666,.569840296,.438579))+FogCameraDeltaFrame.w*.618033989);
    float lo=fogSliceDistance(float(cell.z)),hi=fogSliceDistance(float(cell.z+1));
    vec3 p=ray*mix(lo,hi,.35+.30*noise),world=p+CameraWorldTime.xyz;
    vec3 air=fogAirLight(world);
    float density=fogWorldDensity(world,air);
    // Skylight controls incoming illumination, never the density of the ray.
    vec3 ambient=fogAmbientRadiance(displayToLinear(AtmosphereColor.rgb),ray)*(.06+.94*air.r*air.r);
    float visibility=shadowAt(p,.0003)*cloudVisibility(p);
    // Strengthen low-sun shafts through actual shadow gaps. Density and
    // extinction stay unchanged, preserving nearby material contrast.
    float shaftRadiance=.065+.20*lightingDusk()*lightingDay()*(1.0-lightingRain()*.65);
    vec3 sun=terrainSunRadiance()*shaftRadiance*SunDirection.w*fogPhase(dot(ray,SunDirection.xyz))*visibility;
    vec4 colored=localLightAt(world);
    float power=max(colored.r,max(colored.g,colored.b));
    vec3 local=mix(vec3(1.0,.52,.20)*pow(air.g,3.0),colored.rgb*power*power,colored.a)*2.8;
    fragColor=vec4(max(ambient+sun+local,vec3(0))*density,density);
}
