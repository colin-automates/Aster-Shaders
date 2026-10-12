#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:shadows.glsl>
#include <aster_vulkan:water_caustics.glsl>
uniform sampler2D SceneDepth;
uniform sampler2D MaterialData;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
#ifdef RECEIVER_DEPTH_PROVENANCE
uniform sampler2D OpaqueDepth;
#endif
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    float depth=texture(SceneDepth,texCoord).r;
    vec4 q=InvViewProjection*vec4(texCoord*2.0-1.0,depth,1.0);
    vec3 p=q.xyz/(abs(q.w)>1e-7 ? q.w:1.0);
    vec3 crossNormal=cross(dFdx(p),dFdy(p));
    vec3 normal=crossNormal/max(length(crossNormal),1e-7);
    if(dot(normal,-p)<0.0) normal=-normal;
    bool water=texture(WaterSurface,texCoord).a>.5;
    bool surface=water && texture(WaterDepth,texCoord).r>depth;
#ifdef RECEIVER_DEPTH_PROVENANCE
    // Preserve all visible water pixels so a foreground rejection does not
    // erase the evidence that the upward-looking test exercised the surface.
    if(SceneInfo.z<.5 || depth<=.000001) {fragColor=vec4(0,0,0,1);return;}
#else
    if(SceneInfo.z<.5 || depth<=.000001 || surface) {fragColor=vec4(0,0,0,1);return;}
#endif
    float surfaceY;
    float wetDepth=receiverWaterDepth(p+CameraWorldTime.xyz,normal,surfaceY);
    bool eligible=wetDepth>.06 && !surface;
    // Screenshot alpha is forced opaque. Preserve the replay-mask decision
    // in green, independently of the actual wet-receiver decision in red.
    bool replay=texture(MaterialData,texCoord).a>.01;
#ifdef RECEIVER_DEPTH_PROVENANCE
    // D32 is copied before translucency. Exact equality identifies the
    // visible opaque endpoint, independently of the terrain-only replay.
    bool opaque=depth==texture(OpaqueDepth,texCoord).r;
    fragColor=vec4(eligible ? 1:0,opaque ? 1:0,water ? 1:0,1);
#else
    fragColor=vec4(eligible ? 1:0,eligible && replay ? 1:0,clamp(wetDepth/8.0,0.0,1.0),1);
#endif
}
