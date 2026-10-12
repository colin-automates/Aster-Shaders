#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
uniform sampler2D MaterialAtlas;
#ifdef SOLID_MATERIAL
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:terrainglobals.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:lighting.glsl>
#include <aster_vulkan:environment.glsl>
#include <aster_vulkan:material_surface.glsl>
#include <aster_vulkan:normals.glsl>
#include <aster_vulkan:fog_forward.glsl>
uniform sampler2D NormalAtlas;
uniform sampler2D Sampler0;
layout(location=5) in vec3 relativePosition;
layout(location=6) in float waterMaterial;
layout(location=8) in vec3 albedoTint;
layout(location=12) in float faceShade;
layout(location=2) out vec4 reflectionGeometry;
layout(location=3) out vec4 reflectionResponse;
#endif
layout(location=3) in vec2 texCoord0;
layout(location=4) in float chunkVisibility;
layout(location=7) in vec2 materialLight;
#if defined(WIND_MASK) || defined(FOLIAGE_MASK) || defined(TEMPORAL_MATERIAL)
layout(location=11) in vec2 windMetadata;
#endif
layout(location=0) out vec4 fragColor;
#ifdef TEMPORAL_MATERIAL
layout(location=13) in vec3 previousWindDelta;
layout(location=1) out vec4 temporalMotion;
#endif
void main() {
    // EQUAL depth reuses the world's alpha coverage, including its mipmapped
    // sampling and RGSS. A second atlas alpha test would punch different holes.
    vec4 material=texture(MaterialAtlas,texCoord0);
#ifdef SOLID_MATERIAL
    vec3 dpdx=dFdx(relativePosition),dpdy=dFdy(relativePosition);
    vec3 geometry=normalize(cross(dpdx,dpdy));
    if(dot(geometry,-relativePosition)<0.0)geometry=-geometry;
    vec4 normalSample=texture(NormalAtlas,texCoord0);
    vec3 normal=mappedTerrainNormal(geometry,dpdx,dpdy,dFdx(texCoord0),dFdy(texCoord0),normalSample);
    vec4 texel=UseRgss==1?sampleRGSS(Sampler0,texCoord0,1.0/TextureSize):sampleNearest(Sampler0,texCoord0,1.0/TextureSize);
    vec3 albedo=displayToLinear(texel.rgb*terrainAlbedoTint(albedoTint,faceShade)),surface=material.rgb;
    surface.r=materialFilteredRoughness(surface.r,normalSample);
    materialRainSurface(albedo,surface,relativePosition,geometry,materialLight.y,material.a);
    reflectionGeometry=vec4(0);reflectionResponse=vec4(0);
    if(SceneInfo.w>.5 && SceneInfo.z<.5 && !verificationFlag(4) && waterMaterial<.5 && material.a<.01 && windMetadata.x<.5
        && (surface.g>=.05 || surface.r<=.70) && surface.r<.85) {
        vec3 weight=materialReflectionWeight(albedo,surface.r,surface.g,normal,normalize(-relativePosition));
        float distance=length(relativePosition);
        vec3 nativeTransmission=apply_fog(vec4(1),distance,fog_cylindrical_distance(relativePosition),FogEnvironmentalStart,FogEnvironmentalEnd,
            FogRenderDistanceStart,FogRenderDistanceEnd,vec4(0)).rgb;
        float airTransmission=1.0;
        if(!verificationFlag(1) && !verificationFlag(128) && !verificationFlag(134217728))
            airTransmission=fogAtDepth(FogVolume,gl_FragCoord.xy/SceneInfo.xy,distance).a;
        reflectionGeometry=vec4(normal,surface.r);
        reflectionResponse=vec4(weight*nativeTransmission*airTransmission*chunkVisibility,materialLight.y<.04?0.0:materialLight.y*materialLight.y);
    }
#endif
#ifdef TEMPORAL_MATERIAL
    temporalMotion=vec4(previousWindDelta,windMetadata.x>.5?.1:0);
#endif
#ifdef WIND_MASK
    fragColor=vec4(windMetadata.x > .5 && windMetadata.x < 1.5 ? 1.0 : 0.0,
                   windMetadata.x > 1.5 ? 1.0 : 0.0,windMetadata.y,1);
#elif defined(FOLIAGE_MASK)
    fragColor=vec4(material.a > .01 || windMetadata.x > .5 ? 1.0 : 0.0,0,0,1);
#else
    fragColor=vec4(materialLight.y,materialLight.x,clamp(material.b*2.0,0.0,1.0),chunkVisibility);
#endif
}
