#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:water_temporal.glsl>
#include <aster_vulkan:water_reflection.glsl>
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
uniform sampler2D RawReflection;
uniform sampler2D PreviousSurface;
uniform sampler2D PreviousDepth;
uniform sampler2D PreviousReflection;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 surface=texture(WaterSurface,texCoord),current=texture(RawReflection,texCoord);
    float rawConfidence=current.a;
    if(surface.a < .5) { fragColor=vec4(0); return; }
    // A verified submerged planar sample already contains this frame's moving
    // receiver lighting, without the jittered SSR trace. Heavy accumulation
    // smears those deterministic light patterns. Keep normal history validity
    // tests and reduce smoothing only while that independent source is present.
    float independentSubmerged=SceneInfo.z > .5 ? smoothstep(.95,.995,rawConfidence) : 0.0;
    vec3 p=waterPosition(texCoord,texture(WaterDepth,texCoord).r,InvViewProjection);
    vec3 low=current.rgb,high=current.rgb;
    vec4 spatial=vec4(0);float spatialWeight=0.0;
    ivec2 here=ivec2(gl_FragCoord.xy);
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        ivec2 neighbor=clamp(here+ivec2(x,y),ivec2(0),ivec2(WaterSettings.xy)-1);
        vec4 neighborSurface=texelFetch(WaterSurface,neighbor,0);
        if(neighborSurface.a < .5) continue;
        vec4 value=texelFetch(RawReflection,neighbor,0);
        low=min(low,value.rgb);high=max(high,value.rgb);
        if(!verificationFlag(512)) {
            vec3 neighborP=waterPosition((vec2(neighbor)+.5)/WaterSettings.xy,texelFetch(WaterDepth,neighbor,0).r,InvViewProjection);
            if(abs(neighborP.y-p.y) > .15 || abs(length(neighborP)-length(p)) > .15+length(p)*.035) continue;
            float agreement=max(dot(normalize(neighborSurface.xyz),normalize(surface.xyz)),0.0);
            float w=float((x == 0 ? 2 : 1)*(y == 0 ? 2 : 1))*pow(agreement,32.0);
            spatial+=value*w;spatialWeight+=w;
        }
    }
    if(spatialWeight > .0001) current=mix(spatial/spatialWeight,current,independentSubmerged*.5);
    vec3 previousP=p+CameraDeltaFrame.xyz;
    vec4 clip=PreviousViewProjection*vec4(previousP,1);
    float weight=0.0;
    float historyConfidence=current.a;
    vec3 history=current.rgb;
    vec2 uv=clip.xy/max(clip.w,.0001)*.5+.5;
    if(WaterSettings.z < .5 && WaterSettings.w > .5 && clip.w > 0.0 && all(greaterThan(uv,vec2(0))) && all(lessThan(uv,vec2(1)))) {
        vec4 oldReflection=current;
        if(!verificationFlag(512) && !verificationFlag(4096)) {
            vec2 grid=uv*WaterSettings.xy-.5,blend=fract(grid);
            ivec2 base=ivec2(floor(grid));
            vec4 sum=vec4(0);float total=0.0;
            for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
                ivec2 tap=base+ivec2(x,y);
                if(any(lessThan(tap,ivec2(0))) || any(greaterThanEqual(tap,ivec2(WaterSettings.xy)))) continue;
                vec4 tapSurface=texelFetch(PreviousSurface,tap,0);
                if(tapSurface.a < .5) continue;
                vec3 tapP=waterPosition((vec2(tap)+.5)/WaterSettings.xy,texelFetch(PreviousDepth,tap,0).r,PreviousInvViewProjection);
                if(!waterHistoryCompatible(tapP,tapSurface,previousP,normalize(surface.xyz))) continue;
                float w=(x == 0 ? 1.0-blend.x : blend.x)*(y == 0 ? 1.0-blend.y : blend.y);
                sum+=texelFetch(PreviousReflection,tap,0)*w;total+=w;
            }
            // Each tap retains the original mask, depth, height and normal
            // limits. One rejected nearest texel must not veto other valid
            // taps. Interpolate only their radiance, with partial coverage
            // reducing reuse. Missing geometry never receives history.
            if(total > .05) {
                oldReflection=sum/total;
                weight=clamp(total,0.0,1.0);
            }
        } else {
            // The original nearest-sample verification controls remain.
            ivec2 pixel=clamp(ivec2(uv*WaterSettings.xy),ivec2(0),ivec2(WaterSettings.xy)-1);
            vec4 oldSurface=texelFetch(PreviousSurface,pixel,0);
            vec3 oldP=waterPosition((vec2(pixel)+.5)/WaterSettings.xy,texelFetch(PreviousDepth,pixel,0).r,PreviousInvViewProjection);
            if(waterHistoryCompatible(oldP,oldSurface,previousP,normalize(surface.xyz))) {
                oldReflection=texelFetch(PreviousReflection,pixel,0);
                weight=1.0;
            }
        }
        if(weight > .05) {
            vec3 margin=(high-low)*.1+vec3(.015);
            history=clamp(oldReflection.rgb,low-margin,high+margin);
            float change=length(history-current.rgb)/(length(current.rgb)+.1);
            // A screen-space miss is a change in sample confidence, not a
            // disoccluded water surface. Retain bounded history here, otherwise
            // hit/miss transitions remain as noisy as the unfiltered trace.
            float confidenceReaction=mix(1.0,.5,smoothstep(.1,.5,abs(oldReflection.a-current.a)));
            float colorReaction=mix(1.0,.7,smoothstep(.15,.8,change));
            float baseWeight=min(.97,pow(.85,clamp(PostSettings.x,.001,.1)*30.0));
            weight*=baseWeight*confidenceReaction*colorReaction;
            weight*=mix(1.0,.35,independentSubmerged);
            if(!verificationFlag(512) && !verificationFlag(4096) && !verificationFlag(8192)) {
                // An SSR miss can follow a valid ray on the same water surface.
                // Bridge it briefly rather than clipping reflected radiance to
                // a sky-only neighborhood on the first missing sample. Coverage,
                // depth/height/normal validation above still rejects occlusion.
                float bridge=(1.0-smoothstep(.02,.15,current.a))*smoothstep(.04,.2,oldReflection.a);
                history=mix(history,oldReflection.rgb,bridge);
                float missWeight=exp(-clamp(PostSettings.x,.001,.1)/.065);
                weight=mix(weight,missWeight*weight/max(baseWeight*confidenceReaction*colorReaction,.0001),bridge);
            }
            historyConfidence=oldReflection.a;
        }
    }
#ifdef WATER_DEBUG
    fragColor=vec4(1,weight,rawConfidence,1);
#else
    fragColor=vec4(mix(current.rgb,history,weight),mix(current.a,historyConfidence,weight));
#endif
}
