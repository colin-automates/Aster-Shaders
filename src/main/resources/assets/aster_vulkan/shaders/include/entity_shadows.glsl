#ifndef ASTER_ENTITY_SHADOWS_GLSL
#define ASTER_ENTITY_SHADOWS_GLSL
// Deterministic contact-hardening on the existing reverse-depth cascades.
// Projection depth spans 600 world blocks. Never average lit/background
// depths into the blocker estimate, and correct every tap to the receiver.
const vec2 softShadowDisk[16]=vec2[16](
    vec2(0.17677670,0.00000000),
    vec2(-0.22577219,0.20682582),
    vec2(0.03455805,-0.39377118),
    vec2(0.28457122,0.37117276),
    vec2(-0.52222319,-0.09237393),
    vec2(0.49469539,-0.31468471),
    vec2(-0.16546593,0.61552500),
    vec2(-0.31556147,-0.60759440),
    vec2(0.68464216,0.25003022),
    vec2(-0.71225609,0.29400896),
    vec2(0.34335450,-0.73372862),
    vec2(0.25373024,0.80893199),
    vec2(-0.76474589,-0.44318588),
    vec2(0.89713398,-0.19723239),
    vec2(-0.54750690,0.77877223),
    vec2(-0.12648677,-0.97608970));
vec2 shadowReceiverSlope(int cascade,vec3 dpdx,vec3 dpdy) {
    vec3 plane=cross(mat3(ShadowMatrices[cascade])*dpdx,mat3(ShadowMatrices[cascade])*dpdy);
    return abs(plane.z)>1e-10 ? -2.0*plane.xy/plane.z : vec2(0);
}
float softShadowCompare(int cascade,vec2 uv,float receiver,float bias,vec2 slope,vec2 center) {
    float corrected=receiver+clamp(dot(slope,uv-center),-.002,.002)+bias;
    return corrected>=readShadow(cascade,uv) ? 1.0 : 0.0;
}
float softShadowBilinear(int cascade,vec2 uv,float receiver,float bias,vec2 slope,vec2 center,float resolution) {
    vec2 grid=uv*resolution-.5,base=floor(grid),f=fract(grid);
    float sum=0.0;
    for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        vec2 sampleUv=(base+vec2(x,y)+.5)/resolution;
        float weight=(x==0 ? 1.0-f.x : f.x)*(y==0 ? 1.0-f.y : f.y);
        sum+=softShadowCompare(cascade,sampleUv,receiver,bias,slope,center)*weight;
    }
    return sum;
}
float softSurfaceCascade(int cascade,vec3 q,float bias,vec3 dpdx,vec3 dpdy) {
    if(max(abs(q.x),abs(q.y))>=1.0 || q.z<=0.0 || q.z>=1.0) return 1.0;
    float resolution=cascade==2 ? 1024.0 : 2048.0;
    float extent=cascade==0 ? 24.0 : cascade==1 ? 72.0 : 160.0;
    vec2 center=q.xy*.5+.5,slope=shadowReceiverSlope(cascade,dpdx,dpdy);
    float texelWorld=2.0*extent/resolution;
    // Five bilinear footprints keep blocker distance continuous as a caster
    // crosses the texel grid; each contributing comparison is weighted alone.
    float separation=0.0,blockers=0.0;
    float searchWorld=max(.30,texelWorld*1.5);
    for(int tap=0;tap<5;tap++) {
        vec2 offset=tap==0 ? vec2(0) : tap==1 ? vec2(1,0) : tap==2 ? vec2(-1,0) : tap==3 ? vec2(0,1) : vec2(0,-1);
        vec2 uv=center+offset*searchWorld/(2.0*extent);
        vec2 grid=uv*resolution-.5,base=floor(grid),f=fract(grid);
        for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
            vec2 sampleUv=(base+vec2(x,y)+.5)/resolution;
            float receiver=q.z+clamp(dot(slope,sampleUv-center),-.002,.002);
            float depth=readShadow(cascade,sampleUv);
            float weight=(x==0 ? 1.0-f.x : f.x)*(y==0 ? 1.0-f.y : f.y)*(tap==0 ? 2.0 : 1.0);
            if(depth>receiver+bias) { separation+=(depth-receiver)*weight;blockers+=weight; }
        }
    }
    if(blockers<.00001) return 1.0;
    float blockerDistance=max(0.0,separation/blockers*600.0);
    float angularRadius=mix(.020,.038,smoothstep(.025,.08,SunDirection.w));
    float radius=clamp(blockerDistance*angularRadius,texelWorld*.65,.55);
    // Keep the smallest footprint densely sampled, with the receiver plane
    // correction missing from the previous box filter. Wide penumbras use a
    // fixed disk, with no frame-varying noise or stale motion history.
    if(radius<=texelWorld*1.25) {
        vec2 grid=center*resolution-.5,base=floor(grid)-1.0,f=fract(grid);
        float sum=0.0;
        for(int y=0;y<4;y++) for(int x=0;x<4;x++) {
            float weight=(x==0 ? 1.0-f.x : x==3 ? f.x : 1.0)*(y==0 ? 1.0-f.y : y==3 ? f.y : 1.0);
            vec2 uv=(base+vec2(x,y)+.5)/resolution;
            sum+=softShadowCompare(cascade,uv,q.z,bias,slope,center)*weight;
        }
        return sum/9.0;
    }
    float sum=0.0;
    for(int tap=0;tap<16;tap++) sum+=softShadowBilinear(cascade,center+softShadowDisk[tap]*radius/(2.0*extent),q.z,bias,slope,center,resolution);
    return sum/16.0;
}
float softSurfaceShadowAt(vec3 p,float bias,vec3 dpdx,vec3 dpdy) {
    if(SceneInfo.w<.5) return 1.0;
    for(int cascade=0;cascade<3;cascade++) {
        vec4 clip=ShadowMatrices[cascade]*vec4(p,1);vec3 q=clip.xyz/clip.w;
        float edge=max(abs(q.x),abs(q.y));
        if(edge>=.98 || q.z<=0.0 || q.z>=1.0) continue;
        float visibility=softSurfaceCascade(cascade,q,bias,dpdx,dpdy),blend=smoothstep(.84,.98,edge);
        if(blend<=0.0) return visibility;
        float next=1.0;
        if(cascade<2) { vec4 c=ShadowMatrices[cascade+1]*vec4(p,1);next=softSurfaceCascade(cascade+1,c.xyz/c.w,bias,dpdx,dpdy); }
        return mix(visibility,next,blend);
    }
    return 1.0;
}
#endif
