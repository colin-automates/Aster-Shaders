#ifndef ASTER_WATER_CAUSTIC_SOURCE
#define ASTER_WATER_CAUSTIC_SOURCE
#include <aster_vulkan:water_ripples.glsl>
// A common entry grid lets all four receiver depths reuse the same field.
// Centre its flat-ray travel at 3 blocks: deviations from the .5..5.5 planes
// stay below 2.84 blocks even at grazing air incidence, inside the unchanged
// four-block near-map flux guard. Receivers remain registered in world space.
vec2 causticSourcePosition(ivec2 node,float grid,bool far) {
    float extent=far?256.0:64.0;
    vec2 origin=WaterVolumeInfo.xz-(far?vec2(96):vec2(0));
    vec3 flatRay=refract(-normalize(SkySun.xyz),vec3(0,1,0),1.0/1.333);
    vec2 centreOffset=flatRay.xz/max(-flatRay.y,.2)*3.0;
    return origin+vec2(node)/grid*extent-centreOffset;
}
vec3 causticSourceField(vec2 position,float offset,bool far) {
    float t=waterPeriodicTime(CameraWorldTime.w+offset);
    // Use the same weather strength as displaced geometry and shaded normals.
    // A calm source field would make rainy focusing disagree with the surface.
    float rain=1.0-AtmosphereColor.a;
    return waterWaveField(position,t,rain)*AsterSettings.y
        +waterRippleField(position,t,rain,far?.0625:.025);
}
#endif
