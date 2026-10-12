#ifndef ASTER_SETTINGS
#define ASTER_SETTINGS
#define SHADOW_RESOLUTION 2048 // [1024 2048 4096]
#define SHADOW_RANGE 160.0 // [96.0 128.0 160.0 192.0]
#define WAVE_HEIGHT 0.10 // [0.05 0.10 0.15]
#define REFLECTION_STEPS 48 // [24 48 64]
#define FOG_STEPS 12 // [8 12 16]
#define CLOUD_STEPS 128 // [64 96 128]
#define BLOOM_STRENGTH 0.065 // [0.0 0.035 0.065 0.10]
#define EXPOSURE_MIN 0.45 // [0.25 0.45 0.65]
#define EXPOSURE_MAX 2.80 // [1.8 2.8 4.0]
#define TAA
#define NORMAL_MAPS
#define WATER_CAUSTICS
const int shadowMapResolution = SHADOW_RESOLUTION;
const float shadowDistance = SHADOW_RANGE;
const float sunPathRotation = 30.0;
const float shadowIntervalSize = 2.0;
const float ambientOcclusionLevel = 1.0;
const bool shadowHardwareFiltering = false;
const int noiseTextureResolution = 128;
/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA16F;
const int colortex2Format = RGBA16F;
const int colortex3Format = RGBA16F;
const int colortex4Format = RGBA16F;
const int colortex5Format = RGBA16F;
const int colortex6Format = RGBA16F;
const int colortex7Format = RG32F;
const int colortex8Format = RGBA16F;
const int colortex9Format = RGBA16F;
const int colortex10Format = RG32F;
const int colortex11Format = RGBA16F;
const int colortex12Format = RGBA16F;
const int colortex14Format = RGBA16F;
const int shadowcolor0Format = RGBA16F;
const int shadowcolor1Format = R32F;
*/
const bool colortex6Clear = false;
const bool colortex7Clear = false;
const bool colortex10Clear = false;
const vec4 colortex1ClearColor = vec4(0.5,1.0,0.5,0.0);
const vec4 colortex2ClearColor = vec4(1.0,0.0,0.0,1.0);
#endif
