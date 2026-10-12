#ifndef ASTER_ORIGINAL_LIGHTING
#define ASTER_ORIGINAL_LIGHTING
#ifndef ASTER_LIGHTING_GLSL
#define ASTER_LIGHTING_GLSL
// Verdant Cinema: one palette drives surfaces and atmospheric illumination.
// This is authored sky/bounce fill, not a global illumination solver.
float lightingDay() { return smoothstep(-.12,.12,SkySun.y); }
float lightingDusk() { return exp(-pow((SkySun.y-.10)/.29,2.0)); }
float lightingRain() { return clamp((1.0-AtmosphereColor.a)*2.0,0.0,1.0); }
vec3 lightingFogColor(vec3 nativeColor) {
    if(SceneInfo.w<.5)return nativeColor;
    float luminance=dot(nativeColor,vec3(.2126,.7152,.0722));
    return mix(nativeColor,vec3(.96,1.0,1.03)*luminance,lightingRain()*.88);
}
vec3 lightingAmbientPalette() {
    vec3 day=vec3(.72,.90,1.18);
    vec3 dusk=vec3(.92,.88,.99);
    vec3 palette=mix(day,dusk,lightingDusk()*.85);
    palette=mix(vec3(.66,.79,1.03),palette,lightingDay());
    return mix(palette,vec3(.85,.91,.97),lightingRain()*.78);
}
vec3 terrainAmbient(vec3 nativeLight,vec3 normal,vec2 lightLevels) {
    if(SceneInfo.w < .5)return nativeLight;
    float skyShare=lightLevels.y/max(lightLevels.y+lightLevels.x*1.6,.001);
    // Broad sky illumination keeps vertical leaf/terrain faces readable.
    // The lower hemisphere is a restrained ground bounce, not a black floor.
    float hemisphere=.68+.32*clamp(normal.y*.5+.5,0.0,1.0);
    // Low sun keeps a warm direct beam while the broad sky fill falls away.
    // Native dusk lightmaps stay bright long enough to flatten that separation.
    float twilightFill=mix(1.0,.27,lightingDusk());
    float skyIntensity=mix(1.0,.55,lightingDay());
    vec3 skyFill=lightingAmbientPalette()*(.96*hemisphere*twilightFill*skyIntensity);
    float luminance=dot(nativeLight,vec3(.2126,.7152,.0722));
    vec3 neutralSky=mix(vec3(luminance),nativeLight,.20);
    return mix(nativeLight,neutralSky*skyFill,skyShare);
}
vec3 terrainAmbient(vec3 nativeLight,vec3 normal,vec2 lightLevels,vec3 skyTransmission) {
    if(SceneInfo.w < .5)return nativeLight;
    // Native lightmap shares remain an approximation. Only the declared sky
    // contribution receives incident water extinction; block fill is separate.
    float skyShare=lightLevels.y/max(lightLevels.y+lightLevels.x*1.6,.001);
    float hemisphere=.68+.32*clamp(normal.y*.5+.5,0.0,1.0);
    float twilightFill=mix(1.0,.27,lightingDusk());
    float skyIntensity=mix(1.0,.55,lightingDay());
    vec3 skyFill=lightingAmbientPalette()*(.96*hemisphere*twilightFill*skyIntensity);
    float luminance=dot(nativeLight,vec3(.2126,.7152,.0722));
    vec3 neutralSky=mix(vec3(luminance),nativeLight,.20);
    return mix(nativeLight,neutralSky*skyFill*skyTransmission,skyShare);
}
vec3 terrainAlbedoTint(vec3 nativeTint,float faceShade) {
    // Native meshing records its cardinal shade before wind deformation.
    // AO and biome tint survive; Aster supplies directional scene light once.
    return clamp(nativeTint/max(faceShade,.5),0.0,1.0);
}
vec3 terrainSunRadiance() {
    float elevation=smoothstep(.13,.68,SkySun.y);
    vec3 daylight=mix(vec3(5.35,2.48,1.08),vec3(4.55,4.20,3.62),elevation);
    daylight=mix(daylight,vec3(2.30,2.38,2.47),lightingRain()*.85);
    vec3 moonlight=vec3(1.18,1.48,1.98);
    return mix(moonlight,daylight,lightingDay());
}
vec3 cinematicAtmosphere(vec3 nativeColor,vec3 direction) {
    float lateDay=(1.0-smoothstep(.20,.55,SunDirection.y))*smoothstep(.025,.08,SunDirection.w);
    float horizon=exp(-max(direction.y,0.0)*5.0);
    float sunFacing=pow(max(dot(direction,SunDirection.xyz),0.0),2.0);
    float warmth=lateDay*horizon*(.35+.65*sunFacing)*.65;
    return mix(nativeColor,vec3(1.4,.48,.12),warmth);
}
vec3 fogAmbientRadiance(vec3 nativeColor,vec3 direction) {
    // Supplemental haze must not overwhelm dark foliage with a luminous wash.
    if(verificationFlag(64)) return cinematicAtmosphere(nativeColor,direction)*.8;
    vec3 palette=lightingAmbientPalette();
    float horizon=exp(-max(direction.y,0.0)*4.0);
    float sunFacing=pow(max(dot(direction,SkySun.xyz),0.0),2.0);
    vec3 dusk=vec3(.85,.34,.14)*lightingDusk()*lightingDay()*horizon*(.18+.82*sunFacing);
    return nativeColor*palette*.20+dusk*.025*(1.0-lightingRain());
}
#endif

#endif
