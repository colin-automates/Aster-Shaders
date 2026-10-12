#ifndef ASTER_SHADOW
#define ASTER_SHADOW
vec3 shadowCoord(vec3 p) {
    vec4 q=shadowProjection*shadowModelView*vec4(p,1);q.xyz/=q.w;
    float distortion=.18+.82*length(q.xy);
    q.xy/=distortion;q.z*=.25;
    return q.xyz*.5+.5;
}
vec3 shadowVisibility(vec3 p,vec3 n,out float leaves) {
    vec3 q=shadowCoord(p+n*.018);
    leaves=0.0;
    if(any(lessThan(q,vec3(.001)))||any(greaterThan(q,vec3(.999))))return vec3(1);
    float bias=.00006+.00005*(1.0-abs(dot(n,SunDirection.xyz)));
    vec3 sum=vec3(0);
    const vec2 taps[9]=vec2[9](vec2(0),vec2(1,0),vec2(-1,0),vec2(0,1),vec2(0,-1),vec2(1,1),vec2(-1,1),vec2(1,-1),vec2(-1,-1));
    for(int i=0;i<9;i++) {
        vec2 uv=q.xy+taps[i]/float(SHADOW_RESOLUTION)*1.5;
        float opaque=texture(shadowtex1,uv).r;
        float nearest=texture(shadowtex0,uv).r;
        vec4 caster=texture(shadowcolor0,uv);
        float blocked=step(opaque+bias,q.z);
        float thin=step(nearest+bias,q.z)*(1.0-blocked);
        float leafCaster=step(.4,caster.a)*step(caster.a,.8);
        float leafBlock=blocked*leafCaster*step(abs(opaque-nearest),.0004);
        thin=max(thin,leafBlock);
        vec3 transmitted=mix(vec3(1),caster.rgb,thin);
        sum+=transmitted*(1.0-blocked*(1.0-leafBlock));
        leaves+=thin*leafCaster;
    }
    leaves/=9.0;
    return sum/9.0;
}
float cloudVisibilityIris(vec3 p) {
    vec3 sun=SkySun.xyz;
    if(sun.y<.02)return 1.0;
    vec3 ray=normalize(vec3(p.xz+cameraPosition.xz,0).xzy-cameraPosition);
    // The cloud field is rendered in the same world coordinates as the sky.
    vec3 direction=normalize(vec3(sun.x,sun.y,sun.z));
    vec2 uv=vec2(atan(direction.z,direction.x)/6.28318530718+.5,.5-asin(direction.y)/3.14159265359);
    return mix(1.0,texture(colortex8,uv).a,.50);
}
float waterDepthAlongSun(vec3 p) {
    vec3 q=shadowCoord(p);
    if(any(lessThan(q.xy,vec2(.002)))||any(greaterThan(q.xy,vec2(.998))))return 0.0;
    vec4 nearest=texture(shadowcolor0,q.xy);
    if(nearest.a<.95)return 0.0;
    float depth=texture(shadowtex0,q.xy).r;
    float travel=max(q.z-depth,0.0)*8.0/abs(shadowProjection[2][2]);
    return min(travel*max(SkySun.y,.08),32.0);
}
#endif
