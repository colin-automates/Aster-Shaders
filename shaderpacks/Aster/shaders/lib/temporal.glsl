#ifndef ASTER_TEMPORAL
#define ASTER_TEMPORAL
vec3 toYCoCg(vec3 c){return vec3(dot(c,vec3(.25,.5,.25)),c.r-c.b,c.g-(c.r+c.b)*.5);}
vec3 fromYCoCg(vec3 c){float t=c.x-c.z*.5;return vec3(t+c.y*.5,c.x+c.z*.5,t-c.y*.5);}
// Cubic reconstruction avoids repeatedly blurring history with a bilinear
// filter as the jittered sample moves between pixel centres.
vec3 historyColor(vec2 uv) {
    vec2 size=vec2(viewWidth,viewHeight),position=uv*size-.5;
    vec2 base=floor(position),f=position-base;
    vec2 w0=f*(-.5+f*(1.0-.5*f));
    vec2 w1=1.0+f*f*(-2.5+1.5*f);
    vec2 w2=f*(.5+f*(2.0-1.5*f));
    vec2 w3=f*f*(-.5+.5*f),w12=w1+w2;
    vec2 p0=(base-.5)/size,p12=(base+.5+w2/w12)/size,p3=(base+2.5)/size;
    vec3 color=texture(colortex6,vec2(p0.x,p0.y)).rgb*w0.x*w0.y;
    color+=texture(colortex6,vec2(p12.x,p0.y)).rgb*w12.x*w0.y;
    color+=texture(colortex6,vec2(p3.x,p0.y)).rgb*w3.x*w0.y;
    color+=texture(colortex6,vec2(p0.x,p12.y)).rgb*w0.x*w12.y;
    color+=texture(colortex6,p12).rgb*w12.x*w12.y;
    color+=texture(colortex6,vec2(p3.x,p12.y)).rgb*w3.x*w12.y;
    color+=texture(colortex6,vec2(p0.x,p3.y)).rgb*w0.x*w3.y;
    color+=texture(colortex6,vec2(p12.x,p3.y)).rgb*w12.x*w3.y;
    color+=texture(colortex6,p3).rgb*w3.x*w3.y;
    return max(color,vec3(0));
}
float previousViewDistance(float depth) {
    return abs(gbufferPreviousProjection[3][2]/(depth*2.0-1.0+gbufferPreviousProjection[2][2]));
}
vec3 temporalResolve(vec2 uv,vec3 current,float depth) {
#ifndef TAA
    return current;
#else
    float type=texelFetch(colortex1,ivec2(gl_FragCoord.xy),0).a;
    if(frameCounter<3||type>2.5)return current;
    bool sky=depth>=.99999;
    vec4 previous;
    if(sky) previous=gbufferPreviousProjection*vec4(mat3(gbufferPreviousModelView)*worldRay(uv),0);
    else {
        vec3 p=relativePosition(uv,depth)+cameraPosition-previousCameraPosition+texture(colortex4,uv).xyz;
        previous=gbufferPreviousProjection*gbufferPreviousModelView*vec4(p,1);
    }
    vec2 q=previous.xy/previous.w*.5+.5+frameJitter(max(frameCounter-1,0));
    if(previous.w<=0||any(lessThan(q,vec2(.001)))||any(greaterThan(q,vec2(.999))))return current;
    float previousDepth=sky?1.0:previous.z/previous.w*.5+.5;
    float saved=texelFetch(colortex7,ivec2(q*vec2(viewWidth,viewHeight)),0).r;
    if(sky!= (saved>=.99999))return current;
    if(!sky) {
        float expected=previousViewDistance(previousDepth),actual=previousViewDistance(saved);
        // Test in metres: a fixed depth-buffer epsilon admits background
        // history through distant leaves because perspective depth is nonlinear.
        if(abs(expected-actual)>.06+expected*.01)return current;
    }
    vec3 lo=vec3(1e10),hi=vec3(-1e10),mean=vec3(0),square=vec3(0);
    ivec2 center=ivec2(gl_FragCoord.xy),maximum=ivec2(viewWidth,viewHeight)-1;
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++) {
        vec3 sampleColor=toYCoCg(texelFetch(colortex0,clamp(center+ivec2(x,y),ivec2(0),maximum),0).rgb);
        lo=min(lo,sampleColor);hi=max(hi,sampleColor);
        mean+=sampleColor;square+=sampleColor*sampleColor;
    }
    mean/=9.0;
    vec3 sigma=sqrt(max(square/9.0-mean*mean,vec3(0)));
    lo=max(lo,mean-sigma*1.5);hi=min(hi,mean+sigma*1.5);
    vec3 history=toYCoCg(historyColor(q));
    history=clamp(history,lo,hi);
    float movement=length((q-uv)*vec2(viewWidth,viewHeight));
    float weight=mix(.84,.60,clamp(movement/12.0,0,1));
    if(type>.5&&type<1.5)weight=min(weight,.70);
    if(type>1.5&&type<2.5)weight=min(weight,mix(.72,.45,clamp(movement/8.0,0,1)));
    return mix(current,max(fromYCoCg(history),vec3(0)),weight);
#endif
}
#endif
