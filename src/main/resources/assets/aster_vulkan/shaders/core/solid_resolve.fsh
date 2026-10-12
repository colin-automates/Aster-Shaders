#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:solid_temporal.glsl>
uniform sampler2D SolidRaw;
uniform sampler2D SolidGeometry;
uniform sampler2D SolidDepth;
uniform sampler2D SolidHistory;
uniform sampler2D SolidPreviousGeometry;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
#ifndef SOLID_HISTORY_DEBUG
layout(location=1) out vec4 historyGeometry;
#endif
void main() {
    vec4 geometry=texture(SolidGeometry,texCoord),value=texture(SolidRaw,texCoord);
    float depth=texture(SolidDepth,texCoord).r;
    bool eligible=dot(geometry.xyz,geometry.xyz)>.5 && depth>.000001;
#ifndef SOLID_HISTORY_DEBUG
    historyGeometry=geometry;
#endif
    if(!eligible) {fragColor=vec4(0);return;}
    vec3 p=solidPosition(texCoord,depth),oldPosition=p+SolidCameraDeltaReset.xyz;
    vec4 clip=SolidPreviousProjection*vec4(oldPosition,1);
    vec2 oldUv=clip.xy/max(clip.w,.001)*.5+.5,pixel=1.0/SolidSizeFrame.xy;
    bool valid=SolidCameraDeltaReset.w<.5 && value.a>.01 && clip.w>0.0
        && all(greaterThan(oldUv,pixel)) && all(lessThan(oldUv,vec2(1)-pixel));
    vec4 old=texture(SolidHistory,oldUv),oldGeometry=texture(SolidPreviousGeometry,oldUv);
    // Interpolate radiance only; a blended distance can invent a surface at an edge.
    old.a=texelFetch(SolidHistory,clamp(ivec2(oldUv*SolidSizeFrame.xy),ivec2(0),ivec2(SolidSizeFrame.xy)-1),0).a;
    valid=valid && abs(old.a-length(oldPosition))<max(.04,length(oldPosition)*.006)
        && dot(oldGeometry.xyz,geometry.xyz)>.97 && abs(oldGeometry.w-geometry.w)<.04;
    vec3 lo=vec3(1e10),hi=vec3(-1e10),mean=vec3(0),second=vec3(0);
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++) {
        vec3 c=texture(SolidRaw,texCoord+vec2(x,y)*pixel).rgb;
        lo=min(lo,c);hi=max(hi,c);mean+=c;second+=c*c;
    }
    mean/=9.0;second/=9.0;
    vec3 deviation=sqrt(max(second-mean*mean,vec3(0)));
    vec3 clipped=clamp(old.rgb,max(lo,mean-1.25*deviation),min(hi,mean+1.25*deviation));
    float speed=length((oldUv-texCoord)*SolidSizeFrame.xy);
    float weight=valid?mix(.82,.55,smoothstep(0,8,speed))*min(value.a*2.0,1.0):0.0;
#ifdef SOLID_HISTORY_DEBUG
    fragColor=vec4(1,valid?1:0,value.a>.01?1:0,1);
#else
    fragColor=vec4(mix(value.rgb,clipped,weight),length(p));
#endif
}
