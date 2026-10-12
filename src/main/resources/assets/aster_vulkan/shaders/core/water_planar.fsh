#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:water_planar.glsl>
#include <aster_vulkan:lighting.glsl>
#include <aster_vulkan:cloud_shadow.glsl>
#include <aster_vulkan:local_lighting.glsl>
#include <aster_vulkan:pbr.glsl>
#include <aster_vulkan:environment.glsl>
#include <aster_vulkan:normals.glsl>
#include <aster_vulkan:shadows.glsl>
#include <aster_vulkan:water_caustics.glsl>
#include <aster_vulkan:water_lighting.glsl>
#include <aster_vulkan:water_volume_scattering.glsl>
uniform sampler2D Sampler0;
uniform sampler2D MaterialAtlas;
uniform sampler2D NormalAtlas;
layout(location=0) in vec2 texCoord;
layout(location=1) in vec3 tint;
layout(location=2) in vec3 relativePosition;
layout(location=3) in vec3 nativeLight;
layout(location=4) in vec2 lightLevels;
layout(location=5) in float plantQuad;
layout(location=6) in float faceShade;
layout(location=0) out vec4 fragColor;
void main() {
    vec3 dx=dFdx(relativePosition),dy=dFdy(relativePosition);
    vec3 geometry=normalize(cross(dx,dy));
    vec3 virtualEye=vec3(0,2.0*PlanarSettings.x,0),view=normalize(virtualEye-relativePosition);
    if(dot(geometry,view)<0.0) geometry=-geometry;
    vec4 material=texture(MaterialAtlas,texCoord),normalSample=texture(NormalAtlas,texCoord);
    if(plantQuad>.5)material.a=max(material.a,.65);
    vec3 normal=mappedTerrainNormal(geometry,dx,dy,dFdx(texCoord),dFdy(texCoord),normalSample);
    vec4 texel=texture(Sampler0,texCoord);
    // Exclude below-water terrain; the native pass never replays fluid/glass.
    bool submerged=SceneInfo.z > .5;
    if((submerged ? relativePosition.y > PlanarSettings.x+.001 : relativePosition.y < PlanarSettings.x-.001) || texel.a < .5) discard;
    vec3 albedo=displayToLinear(texel.rgb*terrainAlbedoTint(tint,faceShade));
    WaterLightTransport waterLight=waterLightAt(relativePosition,geometry,max(length(dx),length(dy)));
    vec3 ambient=localAmbientAt(terrainAmbient(displayToLinear(nativeLight),normal,lightLevels,waterLight.skyTransmission),displayToLinear(nativeLight),lightLevels,
        CameraWorldTime.xyz+relativePosition+geometry*.51,normal);
    material.r=max(material.r,.045);
    float variance=normalSample.a > 0.0 ? max(0.0,1.0-length(normalSample.rgb*2.0-1.0)) : 0.0;
    material.r=min(1.0,sqrt(material.r*material.r+32.0*variance*normalSample.a*normalSample.a));
    vec3 world=CameraWorldTime.xyz+relativePosition;
    float wetness=materialWetness(world,geometry,lightLevels.y,material.a);
    float puddle=materialPuddle(world,wetness)*(1.0-material.g);
    albedo*=mix(1.0,.88,wetness)*mix(1.0,.90,puddle);
    material.r=mix(material.r,max(.12,material.r*.65),wetness*(1.0-material.g));
    material.r=mix(material.r,.075,puddle);
    float visibility=material.a > .01 ? leafShadowAt(relativePosition+SunDirection.xyz*.035,.0005,dx,dy)
        : surfaceShadowAt(relativePosition+geometry*.025,.0005);
    vec3 f0=mix(vec3(.04),albedo,material.g);
    vec3 radiance=albedo*ambient*(1.0-material.g)+f0*ambient*mix(.08,.5,material.g);
    vec3 direct=material.a > .01 ? leafBrdf(albedo,material.r,normal,view,waterLight.direction,material.a)
        : directBrdf(albedo,material.r,material.g,normal,view,waterLight.direction);
    radiance+=waterDirectRadiance(direct*terrainSunRadiance()*SunDirection.w*lightLevels.y*visibility*cloudVisibility(relativePosition),waterLight);
    if(material.a<.01)radiance+=materialEnvironment(albedo,material.r,material.g,normal,view,lightLevels.y)*waterLight.skyTransmission;
    radiance+=albedo*material.b*8.0;
    radiance+=albedo*heldRadianceAt(CameraWorldTime.xyz+relativePosition+geometry*.51,normal);
    if(submerged) {
        // Only the reflected surface-to-receiver path belongs to this pass.
        // The actual eye-to-water path is attenuated once by the composite.
        vec3 returnDelta=underwaterPlanarReturnDelta(relativePosition,PlanarSettings.x);
        vec3 attenuation=underwaterPlanarReturnTransmission(returnDelta);
        radiance=radiance*attenuation+underwaterBodyRadiance()*(1.0-attenuation);
        // Integrate from the reflected surface intersection toward this bed
        // point. Composite owns the separate actual eye-to-surface segment.
        vec3 surface=world-returnDelta;
        radiance+=underwaterFocusedScatter(surface,returnDelta,max(length(dx),length(dy)));
    }
    // Fade terrain by its world distance before it leaves the chunk ring.
    // Premultiplied coverage survives filtering at silhouettes and shorelines.
    float coverage=PlanarRange.w > .5 ? 1.0 : 1.0-smoothstep(PlanarRange.x,PlanarRange.y,length(relativePosition.xz));
    fragColor=vec4(radiance*coverage,coverage);
}
