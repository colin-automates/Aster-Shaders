#ifndef ASTER_ORIGINAL_ENVIRONMENT
#define ASTER_ORIGINAL_ENVIRONMENT
#ifndef ASTER_ENVIRONMENT_GLSL
#define ASTER_ENVIRONMENT_GLSL
#include "/lib/original/sky.glsl"
vec3 environmentDirection(vec3 d) {
    vec2 uv=vec2(atan(d.z,d.x)/(2.0*SKY_PI)+.5,.5-asin(clamp(d.y,-1.0,1.0))/SKY_PI);
    vec4 cloud=texture(SkyClouds,uv);
    // The direct BRDF supplies the sun. Excluding its disk here avoids a
    // second solar highlight in a rough environment lobe.
    return skyAtmosphere(d)*cloud.a+cloud.rgb;
}
vec3 materialSkyRadiance(float roughness,vec3 n,vec3 v) {
    vec3 r=reflect(-v,n);
    vec3 tangent=normalize(cross(r,abs(r.y)<.98 ? vec3(0,1,0) : vec3(1,0,0)));
    vec3 bitangent=cross(r,tangent);
    float spread=roughness*roughness*1.4;
    vec3 radiance=environmentDirection(r)*.40;
    radiance+=(environmentDirection(normalize(r+tangent*spread))+environmentDirection(normalize(r-tangent*spread))
        +environmentDirection(normalize(r+bitangent*spread))+environmentDirection(normalize(r-bitangent*spread)))*.15;
    return radiance;
}
vec3 materialReflectionWeight(vec3 albedo,float roughness,float metalness,vec3 n,vec3 v) {
    vec3 f0=mix(vec3(.04),albedo,metalness);
    vec3 fresnel=f0+(max(vec3(1.0-roughness),f0)-f0)*pow(1.0-max(dot(n,v),0.0),5.0);
    return fresnel*(1.0-.55*roughness)*.62;
}
vec3 materialEnvironment(vec3 albedo,float roughness,float metalness,vec3 n,vec3 v,float skyAccess) {
    if(SceneInfo.w<.5 || skyAccess<.04 || (metalness<.05 && roughness>.70))return vec3(0);
    return materialSkyRadiance(roughness,n,v)*materialReflectionWeight(albedo,roughness,metalness,n,v)*skyAccess*skyAccess;
}
float materialWetness(vec3 world,vec3 geometry,float skyAccess,float transmission) {
    if(SceneInfo.w<.5 || transmission>.01)return 0.0;
    float exposed=skyAccess*skyAccess*smoothstep(.55,.90,geometry.y);
    return clamp(2.0*(1.0-AtmosphereColor.a),0.0,1.0)*exposed;
}
float materialPuddle(vec3 world,float wetness) {
    // Broad irregular depressions, softened before they meet dry terrain.
    // This adds a wet sheen without displacing the native block surfaces.
    float patch=skyNoise(vec3(world.xz*.13,23.1))*.72+skyNoise(vec3(world.xz*.37,51.7))*.28;
    return wetness*smoothstep(.46,.67,patch);
}
#endif

#endif
