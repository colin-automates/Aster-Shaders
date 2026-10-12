#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:water_temporal.glsl>
#include <aster_vulkan:water_reflection.glsl>
#include <aster_vulkan:water_planar.glsl>
uniform sampler2D PlanarColor;
uniform sampler2D PlanarDepth;
uniform sampler2D OpaqueColor;
uniform sampler2D OpaqueMip0;
uniform sampler2D OpaqueMip1;
uniform sampler2D OpaqueMip2;
uniform sampler2D OpaqueMip3;
uniform sampler2D OpaqueDepth;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
vec3 planarReflection(vec3 p,vec3 direction,vec3 sky,out float confidence) {
    confidence=0.0;
    if(PlanarSettings.y < .5 || abs(p.y-PlanarSettings.x) > .3) return sky;
    vec3 point=vec3(p.x,PlanarSettings.x,p.z);
    vec2 uv=vec2(0);vec4 clip;float lastTravel=0.0;
    // Project the reflected ray into the independent terrain view. Iteration
    // uses its real reverse depth to warp reflections with the wave normals.
    for(int i=0;i<3;i++) {
        clip=PlanarViewProjection*vec4(point,1);
        if(clip.w <= .001) return sky;
        uv=clip.xy/clip.w*.5+.5;
        if(any(lessThan(uv,vec2(.002))) || any(greaterThan(uv,vec2(.998)))) return sky;
        float depth=textureLod(PlanarDepth,uv,0).r;
        if(depth <= .000001) return sky;
        vec3 hit=waterPosition(uv,depth,PlanarInvViewProjection);
        float travel=dot(hit-p,direction);
        if(travel <= 0.0 || travel > (PlanarRange.w > .5 ? 112.0 : PlanarRange.z)) return sky;
        lastTravel=travel;
        point=p+direction*travel;
    }
    vec4 value=textureLod(PlanarColor,uv,0);
    float edge=min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y));
    confidence=value.a*smoothstep(.005,.035,edge);
    if(PlanarRange.w > .5) confidence*=1.0-smoothstep(80.0,112.0,lastTravel);
    // A filtered edge contains premultiplied coverage from the clear target.
    // Recover straight radiance before mixing it with the sky exactly once.
    return mix(sky,value.rgb/max(value.a,.001),confidence);
}
vec3 reflectionRadiance(vec2 uv,float level) {
    // These are persistent scene-linear color reductions, not bloom. Blend
    // adjacent levels explicitly; the native opaque snapshot has no mip chain.
    if(level < 1.0) return mix(textureLod(OpaqueColor,uv,0).rgb,textureLod(OpaqueMip0,uv,0).rgb,level);
    if(level < 2.0) return mix(textureLod(OpaqueMip0,uv,0).rgb,textureLod(OpaqueMip1,uv,0).rgb,level-1.0);
    if(level < 3.0) return mix(textureLod(OpaqueMip1,uv,0).rgb,textureLod(OpaqueMip2,uv,0).rgb,level-2.0);
    return mix(textureLod(OpaqueMip2,uv,0).rgb,textureLod(OpaqueMip3,uv,0).rgb,level-3.0);
}
void main() {
    vec4 surface=texture(WaterSurface,texCoord);
    vec3 p=waterPosition(texCoord,max(texture(WaterDepth,texCoord).r,.000001),InvViewProjection);
    vec3 n=normalize(surface.xyz+vec3(0,.000001,0)),direction=reflect(normalize(p),n),origin=p+n*.08;
    // Evaluate derivatives before coverage rejection or the divergent march.
    // Project these at a confirmed hit to estimate its texture footprint.
    vec3 originDx=dFdx(origin),originDy=dFdy(origin);
    vec3 directionDx=dFdx(direction),directionDy=dFdy(direction);
    float footprint=max(length(dFdx(p).xz),length(dFdy(p).xz))*WaterSettings.x/SceneInfo.x;
    float roughness=waterSurfaceRoughness(footprint);
    if(surface.a < .5) { fragColor=vec4(0); return; }
    bool legacy=verificationFlag(512);
    vec3 fallback=SceneInfo.z > .5 && !legacy ? underwaterBodyRadiance() : waterSky(direction);
    float planarConfidence=0.0;
    if(!legacy && !verificationFlag(4096)) fallback=planarReflection(p,direction,fallback,planarConfidence);
    // The independent submerged view includes the floor's light patterns and
    // water path. Prefer it to the incomplete screen snapshot under TIR.
    if(SceneInfo.z > .5 && planarConfidence > .95) {
#ifdef WATER_SOURCE_DEBUG
        fragColor=vec4(0,planarConfidence,1,1);
#else
        fragColor=vec4(fallback,planarConfidence);
#endif
        return;
    }
    float previousGap=-1.0,previousTravel=0.0;
    // Vary the step phase, then refine crossings. History reduces the small
    // sampling variations without freezing the animated surface normals.
    float jitter=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233))+CameraDeltaFrame.w*1.618)*43758.5453)-.5;
    for(int i=0;i<28;i++) {
        if(legacy && i >= 20) break;
        float stepIndex=max(0.0,float(i)+jitter*.6);
        float travel=legacy ? .35+stepIndex*stepIndex*.16 : .18+stepIndex*stepIndex*.08;
        vec3 ray=origin+direction*travel;
        vec4 clip=CurrentViewProjection*vec4(ray,1);
        if(clip.w <= 0.0) break;
        vec2 uv=clip.xy/clip.w*.5+.5;
        if(any(lessThan(uv,vec2(.001))) || any(greaterThan(uv,vec2(.999)))) break;
        float depth=texture(OpaqueDepth,uv).r;
        if(depth <= .000001) { previousGap=-1.0; previousTravel=travel; continue; }
        float gap=length(ray)-length(waterPosition(uv,depth,InvViewProjection));
        float coarseGap=gap;
        if(i > 0 && previousGap < 0.0 && gap >= 0.0) {
            float lo=previousTravel,hi=travel;
            for(int refine=0;refine<6;refine++) {
                if(legacy && refine == 5) break;
                float mid=(lo+hi)*.5;
                vec3 point=origin+direction*mid;
                vec4 hitClip=CurrentViewProjection*vec4(point,1);
                uv=hitClip.xy/hitClip.w*.5+.5;
                depth=texture(OpaqueDepth,uv).r;
                gap=depth > .000001 ? length(point)-length(waterPosition(uv,depth,InvViewProjection)) : -1.0;
                if(gap >= 0.0) hi=mid; else lo=mid;
            }
            if(!legacy && !verificationFlag(1024)) {
                // hi is the confirmed crossing side. The last midpoint can
                // lie before the surface even when the bracket contains a hit.
                vec3 hitPoint=origin+direction*hi;
                vec4 hitClip=CurrentViewProjection*vec4(hitPoint,1);
                uv=hitClip.xy/hitClip.w*.5+.5;
                depth=texture(OpaqueDepth,uv).r;
                gap=depth > .000001 ? length(hitPoint)-length(waterPosition(uv,depth,InvViewProjection)) : -1.0;
            }
            float thickness=legacy ? .20+travel*.015 : .08+travel*.004;
            if(gap >= 0.0 && gap < thickness) {
                float edge=min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y));
                float confidence=smoothstep(0.0,.08,edge)*(1.0-travel/70.0);
                vec3 radiance=textureLod(OpaqueColor,uv,0).rgb;
                if(!legacy && !verificationFlag(4096)) {
                    vec4 hitClip=CurrentViewProjection*vec4(origin+direction*hi,1);
                    vec2 ndc=hitClip.xy/hitClip.w;
                    vec4 dx=CurrentViewProjection*vec4(originDx+directionDx*hi,0);
                    vec4 dy=CurrentViewProjection*vec4(originDy+directionDy*hi,0);
                    vec2 uvDx=.5*(dx.xy-ndc*dx.w)/hitClip.w;
                    vec2 uvDy=.5*(dy.xy-ndc*dy.w)/hitClip.w;
                    float pixelSpan=max(length(uvDx*SceneInfo.xy),length(uvDy*SceneInfo.xy));
                    float projectionScale=length(vec3(CurrentViewProjection[0].y,CurrentViewProjection[1].y,CurrentViewProjection[2].y));
                    // A restrained roughness cone softens distant reflected
                    // texels; the projected ray footprint handles wave squeeze.
                    float coneSpan=.12*roughness*roughness*hi*SceneInfo.y*projectionScale/max(hitClip.w,.001);
                    float level=clamp(log2(max(max(pixelSpan*.5,coneSpan),1.0)),0.0,4.0);
                    radiance=reflectionRadiance(uv,level);
                }
#ifdef WATER_SOURCE_DEBUG
                fragColor=vec4(confidence,planarConfidence,1,1);
#else
                fragColor=vec4(mix(fallback,radiance,confidence),confidence);
#endif
                return;
            }
        }
        // Keep the coarse sample's gap for the next interval. Refinement must
        // not attach a midpoint gap to the previous coarse travel distance.
        previousGap=coarseGap;
        if(legacy || verificationFlag(1024)) previousGap=gap;
        previousTravel=travel;
    }
#ifdef WATER_SOURCE_DEBUG
    fragColor=vec4(0,planarConfidence,1,1);
#else
    fragColor=vec4(fallback,0);
#endif
}
