#ifndef ASTER_COMMON
#define ASTER_COMMON
#include "/lib/settings.glsl"
uniform mat4 gbufferModelView,gbufferModelViewInverse;
uniform mat4 gbufferProjection,gbufferProjectionInverse;
uniform mat4 gbufferPreviousModelView,gbufferPreviousProjection;
uniform mat4 shadowModelView,shadowProjection,shadowModelViewInverse,shadowProjectionInverse;
uniform vec3 cameraPosition,previousCameraPosition,sunPosition,moonPosition,shadowLightPosition;
uniform vec3 fogColor,skyColor;
uniform float frameTimeCounter,frameTime,viewWidth,viewHeight,rainStrength,wetness,nightVision;
uniform float far,near,blindness,darknessFactor;
uniform int frameCounter,worldTime,moonPhase,isEyeInWater,heldBlockLightValue,heldBlockLightValue2;
uniform ivec2 eyeBrightnessSmooth;
uniform sampler2D colortex0,colortex1,colortex2,colortex3,colortex4,colortex5;
uniform sampler2D colortex6,colortex7,colortex8,colortex9,colortex10;
uniform sampler2D colortex11,colortex12;
uniform sampler2D colortex14;
uniform sampler2D depthtex0,depthtex1,shadowtex0,shadowtex1,shadowcolor0,shadowcolor1;
#define SkyClouds colortex8
#define CameraWorldTime vec4(cameraPosition,mod(frameTimeCounter,1200.0))
#if defined(ASTER_NETHER) || defined(ASTER_END)
#define SkySun vec4(0.0,-1.0,0.0,0.0)
#define SkyMoon vec4(0.0,1.0,0.0,0.0)
#define SunDirection vec4(0.0,1.0,0.0,0.0)
#define SceneInfo vec4(viewWidth,viewHeight,isEyeInWater==1?1.0:0.0,0.0)
#else
#define SkySun vec4(normalize(mat3(gbufferModelViewInverse)*sunPosition),float(moonPhase)/8.0)
#define SkyMoon vec4(normalize(mat3(gbufferModelViewInverse)*moonPosition),float(worldTime)*6.28318530718/24000.0)
#define SunDirection vec4(normalize(mat3(gbufferModelViewInverse)*shadowLightPosition),smoothstep(-.12,.12,SkySun.y)*(.95-.6*rainStrength)+.055)
#define SceneInfo vec4(viewWidth,viewHeight,isEyeInWater==1?1.0:0.0,1.0)
#endif
#define SkyTiming vec4(frameTimeCounter*6.28318530718/1200.0,mod(frameTimeCounter,1200.0),mod(frameTimeCounter-frameTime,1200.0),rainStrength*.5)
#define AtmosphereColor vec4(fogColor,1.0-rainStrength*.5)
#define AsterSettings vec4(1.0,WAVE_HEIGHT,1.0,0.0)
#define WaterVolumeInfo vec4(0.0,0.0,0.0,1.0)
const float WATER_RIPPLE_AMPLITUDE = 1.75;
bool verificationFlag(int flag) {return false;}
vec2 frameJitter(int frame) {
#ifdef TAA
    const vec2 pattern[8]=vec2[8](vec2(.5,.333333),vec2(.25,.666667),vec2(.75,.111111),vec2(.125,.444444),vec2(.625,.777778),vec2(.375,.222222),vec2(.875,.555556),vec2(.0625,.888889));
    return (pattern[frame%8]-.5)/vec2(viewWidth,viewHeight);
#else
    return vec2(0);
#endif
}
vec3 viewPosition(vec2 uv,float depth) {
    vec4 p=gbufferProjectionInverse*vec4((uv-frameJitter(frameCounter))*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}
vec3 relativePosition(vec2 uv,float depth) {return (gbufferModelViewInverse*vec4(viewPosition(uv,depth),1.0)).xyz;}
vec3 worldRay(vec2 uv) {
    // Minecraft can use an infinite far plane. Its homogeneous direction
    // remains finite even when the far point has w=0.
    vec4 ray=gbufferProjectionInverse*vec4((uv-frameJitter(frameCounter))*2.0-1.0,1.0,1.0);
    return normalize(mat3(gbufferModelViewInverse)*ray.xyz);
}
vec2 projectView(vec3 p) {
    vec4 clip=gbufferProjection*vec4(p,1);return clip.xy/clip.w*.5+.5+frameJitter(frameCounter);
}
#include "/lib/original/color.glsl"
#include "/lib/original/pbr.glsl"
#include "/lib/original/lighting.glsl"
#include "/lib/original/waves.glsl"
#ifdef ASTER_FRAGMENT
#include "/lib/original/sky.glsl"
#include "/lib/original/environment.glsl"
#include "/lib/original/water.glsl"
#endif
#endif
