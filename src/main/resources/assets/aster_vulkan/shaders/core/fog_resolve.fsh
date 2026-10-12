#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:fog.glsl>
#include <aster_vulkan:fog_temporal.glsl>
uniform sampler2D FogGeometry;
uniform sampler2D RawFog;
uniform sampler2D PreviousFogGeometry;
uniform sampler2D PreviousFog;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 geometry=texture(FogGeometry,texCoord).rg;
    vec4 current=texture(RawFog,texCoord),history=current;
    bool sky=geometry.r < 0.0;
    vec3 p=fogRay(texCoord,InvViewProjection)*abs(geometry.r);
    // Sky rotation reprojects a direction. Finite endpoints include camera
    // translation; endpoint validation is a bounded approximation to a volume.
    vec3 previousP=p+(sky ? vec3(0) : FogCameraDeltaFrame.xyz);
    vec4 clip=FogPreviousViewProjection*vec4(previousP,1);
    vec2 uv=clip.xy/max(clip.w,.0001)*.5+.5;
    float weight=0.0;
    if(FogSettings.z < .5 && clip.w > 0.0 && all(greaterThan(uv,vec2(0))) && all(lessThan(uv,vec2(1)))) {
        ivec2 pixel=clamp(ivec2(uv*FogSettings.xy),ivec2(0),ivec2(FogSettings.xy)-1);
        vec2 oldGeometry=texelFetch(PreviousFogGeometry,pixel,0).rg;
        float error=abs(oldGeometry.r-length(previousP));
        float tolerance=.15+length(previousP)*.012;
        bool compatible=sky ? oldGeometry.r < 0.0 : oldGeometry.r > 0.0 && error < tolerance;
        compatible=compatible && abs(oldGeometry.g-geometry.g) < .08;
        if(compatible) {
            vec4 low=current,high=current;
            ivec2 here=ivec2(gl_FragCoord.xy);
            for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
                ivec2 neighbor=clamp(here+ivec2(x,y),ivec2(0),ivec2(FogSettings.xy)-1);
                float d=texelFetch(FogGeometry,neighbor,0).r;
                if((d < 0.0) != sky || (!sky && abs(d-geometry.r) > .3+geometry.r*.02)) continue;
                vec4 c=texelFetch(RawFog,neighbor,0);
                low=min(low,c);high=max(high,c);
            }
            vec4 margin=(high-low)*.1+vec4(.003,.003,.003,.003);
            history=clamp(texelFetch(PreviousFog,pixel,0),low-margin,high+margin);
            float change=length(history.rgb-current.rgb)/(length(current.rgb)+.02);
            float base=min(.97,pow(.88,clamp(PostSettings.x,.001,.1)*30.0));
            weight=base*FogReaction.x*mix(1.0,.6,smoothstep(.3,1.5,change));
        }
    }
#ifdef FOG_DEBUG
    fragColor=vec4(sky ? 0 : 1,weight,clamp(abs(geometry.r)/FogSettings.w,0.0,1.0),1);
#else
    fragColor=mix(current,history,weight);
#endif
}
