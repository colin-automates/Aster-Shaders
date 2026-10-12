#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:solid_temporal.glsl>
#include <aster_vulkan:environment.glsl>
uniform sampler2D SolidGeometry;
uniform sampler2D SolidResponse;
uniform sampler2D SolidDepth;
uniform sampler2D SolidScene;
uniform sampler2D SolidMip0;
uniform sampler2D SolidMip1;
uniform sampler2D SolidMip2;
uniform sampler2D SolidMip3;
uniform sampler2D SolidMotion;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
vec3 sceneRadiance(vec2 uv,float level) {
    if(level<1.0)return mix(textureLod(SolidScene,uv,0).rgb,textureLod(SolidMip0,uv,0).rgb,level);
    if(level<2.0)return mix(textureLod(SolidMip0,uv,0).rgb,textureLod(SolidMip1,uv,0).rgb,level-1.0);
    if(level<3.0)return mix(textureLod(SolidMip1,uv,0).rgb,textureLod(SolidMip2,uv,0).rgb,level-2.0);
    return mix(textureLod(SolidMip2,uv,0).rgb,textureLod(SolidMip3,uv,0).rgb,level-3.0);
}
void main() {
    vec4 geometry=texture(SolidGeometry,texCoord);
    float depth=texture(SolidDepth,texCoord).r;
    bool eligible=dot(geometry.xyz,geometry.xyz)>.5 && depth>.000001;
    eligible=eligible && !(texture(WaterSurface,texCoord).a>.5 && texture(WaterDepth,texCoord).r>=depth);
    if(!eligible){fragColor=vec4(0);return;}
    vec3 p=solidPosition(texCoord,depth),n=normalize(geometry.xyz);
    vec3 direction=reflect(normalize(p),n),origin=p+n*(.025+min(length(p)*.0005,.025));
    vec3 fallback=materialSkyRadiance(geometry.w,n,normalize(-p));
    float previousGap=-1.0,previousTravel=0.0;
    float phase=fract(SolidSizeFrame.z*.618033989)*.5;
    for(int i=0;i<48;i++) {
        float index=float(i)+phase,travel=.08+index*.10+index*index*.025;
        vec3 ray=origin+direction*travel;
        vec4 clip=SolidCurrentProjection*vec4(ray,1);
        if(clip.w<=.001)break;
        vec2 uv=clip.xy/clip.w*.5+.5;
        if(any(lessThan(uv,vec2(.002)))||any(greaterThan(uv,vec2(.998))))break;
        float hitDepth=textureLod(SolidDepth,uv,0).r;
        if(hitDepth<=.000001){previousGap=-1.0;previousTravel=travel;continue;}
        float gap=length(ray)-length(solidPosition(uv,hitDepth));
        if(i>0 && previousGap<0.0 && gap>=0.0) {
            float lo=previousTravel,hi=travel;
            for(int j=0;j<6;j++) {
                float mid=(lo+hi)*.5;
                vec3 point=origin+direction*mid;
                vec4 q=SolidCurrentProjection*vec4(point,1);
                uv=q.xy/q.w*.5+.5;
                hitDepth=textureLod(SolidDepth,uv,0).r;
                float error=hitDepth>.000001?length(point)-length(solidPosition(uv,hitDepth)):-1.0;
                if(error>=0.0)hi=mid;else lo=mid;
            }
            vec3 point=origin+direction*hi;
            vec4 q=SolidCurrentProjection*vec4(point,1);uv=q.xy/q.w*.5+.5;
            hitDepth=textureLod(SolidDepth,uv,0).r;
            gap=hitDepth>.000001?length(point)-length(solidPosition(uv,hitDepth)):-1.0;
            if(gap>=0.0 && gap<.06+hi*.003 && hi>.12) {
                float edge=min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y));
                float confidence=smoothstep(.006,.055,edge)*(1.0-smoothstep(40.0,64.0,hi));
                // Roughness grows the projected reflection cone, selecting the
                // independent radiance reductions rather than blurring depth.
                vec3 tangent=normalize(cross(direction,abs(direction.y)<.98?vec3(0,1,0):vec3(1,0,0)));
                vec4 dx=SolidCurrentProjection*vec4(tangent,0),dy=SolidCurrentProjection*vec4(cross(direction,tangent),0);
                vec2 ndc=q.xy/q.w;
                float radius=max(length((dx.xy-ndc*dx.w)*SceneInfo.xy),length((dy.xy-ndc*dy.w)*SceneInfo.xy))
                    *.5*geometry.w*geometry.w*hi*.3/max(q.w,.001);
                float level=clamp(log2(max(radius,1.0)),0.0,4.0);
                vec3 sampleColor=sceneRadiance(uv,level);
                vec4 response=texture(SolidResponse,texCoord);
#ifdef SOLID_DEBUG
                fragColor=vec4(1,confidence>.01?1:0,geometry.w,1);
#else
                // Delta replaces the existing sky lobe exactly once. Indoor
                // scene reflections do not require outdoor sky access.
                float reactive=textureLod(SolidMotion,uv,0).a;
                fragColor=vec4((sampleColor-fallback*response.a)*confidence,
                    confidence*(reactive>.05?.45:1.0));
#endif
                return;
            }
        }
        previousGap=gap;previousTravel=travel;
    }
#ifdef SOLID_DEBUG
    fragColor=vec4(1,0,geometry.w,1);
#else
    fragColor=vec4(0);
#endif
}
