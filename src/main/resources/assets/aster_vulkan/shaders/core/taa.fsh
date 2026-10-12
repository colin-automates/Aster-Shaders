#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform SceneTemporal {
    mat4 TaaInverseCurrent;
    mat4 TaaPreviousProjection;
    vec4 TaaCameraDeltaReset;
    vec4 TaaSizeFrame;
    vec4 TaaJitter;
};
uniform sampler2D TaaInput;
uniform sampler2D TaaDepth;
uniform sampler2D TaaMotion;
uniform sampler2D TaaHistory;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
uniform sampler2D TaaOpaqueColor;
uniform sampler2D TaaSceneColor;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;

// Clip in a bounded luminance space so HDR emitters do not stretch the box.
vec3 bounded(vec3 c) { return c/(1.0+dot(c,vec3(.2126,.7152,.0722))); }
vec3 unbounded(vec3 c) { return max(c,vec3(0))/max(1.0-dot(c,vec3(.2126,.7152,.0722)),.0001); }
vec3 clipBox(vec3 c,vec3 lo,vec3 hi) {
    vec3 midpoint=(hi+lo)*.5, extent=max((hi-lo)*.5,vec3(.0001));
    vec3 delta=c-midpoint;
    float distance=max(abs(delta.x)/extent.x,max(abs(delta.y)/extent.y,abs(delta.z)/extent.z));
    return midpoint+delta/max(distance,1.0);
}
// Separable Catmull-Rom with the four corner taps omitted and normalized.
vec3 historyColor(vec2 uv) {
    vec2 size=TaaSizeFrame.xy, p=uv*size-.5, base=floor(p), f=p-base;
    vec2 w0=f*(-.5+f*(1.0-.5*f));
    vec2 w1=1.0+f*f*(-2.5+1.5*f);
    vec2 w2=f*(.5+f*(2.0-1.5*f));
    vec2 w3=f*f*(-.5+.5*f), w12=w1+w2;
    vec2 a=(base-.5)/size,b=(base+.5+w2/w12)/size,c=(base+2.5)/size;
    vec3 sum=texture(TaaHistory,b).rgb*w12.x*w12.y;
    sum+=texture(TaaHistory,vec2(a.x,b.y)).rgb*w0.x*w12.y;
    sum+=texture(TaaHistory,vec2(c.x,b.y)).rgb*w3.x*w12.y;
    sum+=texture(TaaHistory,vec2(b.x,a.y)).rgb*w12.x*w0.y;
    sum+=texture(TaaHistory,vec2(b.x,c.y)).rgb*w12.x*w3.y;
    return max(sum/(w12.x*(w0.y+w12.y+w3.y)+(w0.x+w3.x)*w12.y),vec3(0));
}
void main() {
    vec2 pixel=1.0/TaaSizeFrame.xy;
    // The output grid is unjittered. Both current fetches and previous projection
    // are corrected exactly once; the history itself is already reconstructed.
    vec2 uv=texCoord+TaaJitter.xy;
    float depth=texture(TaaDepth,uv).r;
    vec4 motion=texture(TaaMotion,uv);
#ifdef TAA_MOTION_DEBUG
    fragColor=vec4(min(length(motion.xyz)*1024.0,1.0),motion.a>.05&&motion.a<.15?1:0,0,1);
    return;
#endif
    float waterDepth=texture(WaterDepth,uv).r;
    if(texture(WaterSurface,uv).a>.5 && waterDepth>=depth) { depth=waterDepth;motion=vec4(0,0,0,1); }
    vec3 opaque=texture(TaaOpaqueColor,uv).rgb,scene=texture(TaaSceneColor,uv).rgb;
    float transparency=length(scene-opaque)/max(length(opaque),.1);
    motion.a=max(motion.a,smoothstep(.025,.15,transparency));
    vec3 current=texture(TaaInput,uv).rgb;
    vec4 p=TaaInverseCurrent*vec4(uv*2.0-1.0,depth,1.0);
    vec3 relative=p.xyz/max(p.w,.000001);
    bool sky=depth<=.000001;
    vec3 previousPosition=relative+TaaCameraDeltaReset.xyz+motion.xyz;
    vec4 oldClip=TaaPreviousProjection*vec4(previousPosition,1);
    if(sky) {
        vec4 nearPoint=TaaInverseCurrent*vec4(uv*2.0-1.0,1,1);
        vec4 farPoint=TaaInverseCurrent*vec4(uv*2.0-1.0,0,1);
        vec3 direction=normalize(farPoint.xyz*nearPoint.w-nearPoint.xyz*farPoint.w);
        oldClip=TaaPreviousProjection*vec4(direction,0);
    }
    vec2 oldUv=oldClip.xy/max(oldClip.w,.000001)*.5+.5-TaaJitter.zw;
    bool valid=TaaCameraDeltaReset.w<.5 && oldClip.w>0
        && all(greaterThanEqual(oldUv,pixel)) && all(lessThanEqual(oldUv,vec2(1)-pixel));
    float expected=sky?0:length(previousPosition);
    // Nearest history depth avoids fabricating a surface across a silhouette.
    if(valid) {
        float oldDistance=texelFetch(TaaHistory,clamp(ivec2(oldUv*TaaSizeFrame.xy),ivec2(0),ivec2(TaaSizeFrame.xy)-1),0).a;
        valid=sky ? oldDistance<.001 : abs(oldDistance-expected)<max(.04,expected*.008);
    }
    if(!valid) {
#ifdef TAA_DEBUG
        fragColor=vec4(1,0,motion.a>.5?1:0,1);
#else
        fragColor=vec4(current,sky?0:length(relative));
#endif
        return;
    }
    vec3 lo=vec3(1e10),hi=vec3(-1e10),mean=vec3(0),second=vec3(0);
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        vec3 c=bounded(texture(TaaInput,uv+vec2(x,y)*pixel).rgb);
        lo=min(lo,c);hi=max(hi,c);mean+=c;second+=c*c;
    }
    mean/=9.0;second/=9.0;
    vec3 deviation=sqrt(max(second-mean*mean,vec3(0)));
    lo=max(lo,mean-deviation*1.35);hi=min(hi,mean+deviation*1.35);
    vec3 old=bounded(historyColor(oldUv));
    vec3 clipped=clipBox(old,lo,hi);
    float change=length(old-clipped)/max(length(bounded(current)),.05);
    float speed=length((oldUv-texCoord)*TaaSizeFrame.xy);
    float weight=mix(.91,.72,smoothstep(0,12,speed));
    weight*=1.0-smoothstep(.05,.5,change)*.65;
    // Only terrain has reliable object motion in this first integration. Native
    // entities, transparent layers and water retain at most one short history.
    weight=mix(weight,min(weight,.35),clamp(motion.a,0.0,1.0));
    if(sky) weight=min(weight,.8);
    if(!valid) weight=0;
#ifdef TAA_DEBUG
    fragColor=vec4(valid?0:1,valid?1:0,motion.a>.5?1:0,1);
#else
    vec3 resolved=unbounded(mix(bounded(current),clipped,weight));
    fragColor=vec4(resolved,sky?0:length(relative));
#endif
}
