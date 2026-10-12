uniform sampler2D Shadow0;
uniform sampler2D Shadow1;
uniform sampler2D Shadow2;
float readShadow(int cascade,vec2 uv) {
    if(cascade == 0) return texture(Shadow0,uv).r;
    if(cascade == 1) return texture(Shadow1,uv).r;
    return texture(Shadow2,uv).r;
}
float cascadeShadow(int cascade,vec3 q,float bias) {
    if(max(abs(q.x),abs(q.y)) >= 1.0 || q.z <= 0 || q.z >= 1) return 1;
    vec2 uv=q.xy*.5+.5;
    float texel=cascade == 2 ? 1.0/1024.0 : 1.0/2048.0,sum=0;
    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++)
        sum += q.z+bias >= readShadow(cascade,uv+vec2(x,y)*texel) ? 1.0 : 0.0;
    return sum/9.0;
}
float shadowAt(vec3 p,float bias) {
    if(SceneInfo.w < .5) return 1;
    for(int i=0;i<3;i++) {
        vec4 clip=ShadowMatrices[i]*vec4(p,1);
        vec3 q=clip.xyz/clip.w;
        float edge=max(abs(q.x),abs(q.y));
        if(edge >= .98 || q.z <= 0 || q.z >= 1) continue;
        float visibility=cascadeShadow(i,q,bias),blend=smoothstep(.84,.98,edge);
        if(blend <= 0) return visibility;
        float nextVisibility=1;
        if(i<2) { vec4 nextClip=ShadowMatrices[i+1]*vec4(p,1); nextVisibility=cascadeShadow(i+1,nextClip.xyz/nextClip.w,bias); }
        return mix(visibility,nextVisibility,blend);
    }
    return 1;
}
float surfaceCascadeShadow(int cascade,vec3 q,float bias) {
    if(max(abs(q.x),abs(q.y)) >= 1.0 || q.z <= 0 || q.z >= 1) return 1;
    float resolution=cascade == 2 ? 1024.0 : 2048.0;
    vec2 grid=(q.xy*.5+.5)*resolution-.5;
    vec2 base=floor(grid)-1.0,fraction=fract(grid);
    float sum=0;
    // Bilinear interpolation of a 3x3 box PCF needs 16 unique comparisons.
    // Compare first, then filter visibility; averaging depths would be wrong.
    for(int y=0;y<4;y++) for(int x=0;x<4;x++) {
        float wx=x == 0 ? 1.0-fraction.x : x == 3 ? fraction.x : 1.0;
        float wy=y == 0 ? 1.0-fraction.y : y == 3 ? fraction.y : 1.0;
        vec2 uv=(base+vec2(x,y)+.5)/resolution;
        sum+=(q.z+bias >= readShadow(cascade,uv) ? 1.0 : 0.0)*wx*wy;
    }
    return sum/9.0;
}
float surfaceShadowAt(vec3 p,float bias) {
    for(int i=0;i<3;i++) {
        vec4 clip=ShadowMatrices[i]*vec4(p,1);vec3 q=clip.xyz/clip.w;
        float edge=max(abs(q.x),abs(q.y));
        if(edge >= .98 || q.z <= 0 || q.z >= 1) continue;
        float visibility=surfaceCascadeShadow(i,q,bias),blend=smoothstep(.84,.98,edge);
        if(blend <= 0) return visibility;
        float nextVisibility=1;
        if(i<2) { vec4 nextClip=ShadowMatrices[i+1]*vec4(p,1);nextVisibility=surfaceCascadeShadow(i+1,nextClip.xyz/nextClip.w,bias); }
        return mix(visibility,nextVisibility,blend);
    }
    return 1;
}
float leafCascadeShadow(int cascade,vec3 q,float bias,vec3 dpdx,vec3 dpdy,float radius) {
    if(max(abs(q.x),abs(q.y)) >= 1.0 || q.z <= 0 || q.z >= 1) return 1;
    float resolution=cascade == 2 ? 1024.0 : 2048.0;
    float cascadeRadius=cascade == 0 ? 24.0 : cascade == 1 ? 72.0 : 160.0;
    vec2 uv=q.xy*.5+.5;
    vec3 plane=cross(mat3(ShadowMatrices[cascade])*dpdx,mat3(ShadowMatrices[cascade])*dpdy);
    vec2 slope=abs(plane.z) > 1e-10 ? -2.0*plane.xy/plane.z : vec2(0);
    float spacing=max(1.0,radius*resolution/(2.0*cascadeRadius));
    float sum=0.0;
    // A fixed Gaussian footprint averages tiny cutout shadows without a
    // changing random pattern. Each tap interpolates comparison results.
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        vec2 tapUv=uv+vec2(x,y)*spacing/resolution;
        vec2 grid=tapUv*resolution-.5,base=floor(grid),fraction=fract(grid);
        float visibility=0.0;
        for(int by=0;by<2;by++) for(int bx=0;bx<2;bx++) {
            vec2 sampleUv=(base+vec2(bx,by)+.5)/resolution;
            // Correct the receiver plane across the wider footprint so
            // sloped leaf faces do not shadow themselves during filtering.
            float receiver=q.z+clamp(dot(slope,sampleUv-uv),-.001,.001)+bias;
            float weight=(bx == 0 ? 1.0-fraction.x : fraction.x)*(by == 0 ? 1.0-fraction.y : fraction.y);
            visibility+=(receiver >= readShadow(cascade,sampleUv) ? 1.0 : 0.0)*weight;
        }
        sum+=visibility*(x == 0 ? .5 : .25)*(y == 0 ? .5 : .25);
    }
    return sum;
}
float leafShadowAt(vec3 p,float bias,vec3 dpdx,vec3 dpdy) {
    float radius=.24+min(length(p)*.003,.24);
    for(int i=0;i<3;i++) {
        vec4 clip=ShadowMatrices[i]*vec4(p,1);vec3 q=clip.xyz/clip.w;
        float edge=max(abs(q.x),abs(q.y));
        if(edge >= .98 || q.z <= 0 || q.z >= 1) continue;
        float visibility=leafCascadeShadow(i,q,bias,dpdx,dpdy,radius),blend=smoothstep(.84,.98,edge);
        if(blend <= 0) return visibility;
        float nextVisibility=1;
        if(i<2) { vec4 nextClip=ShadowMatrices[i+1]*vec4(p,1);nextVisibility=leafCascadeShadow(i+1,nextClip.xyz/nextClip.w,bias,dpdx,dpdy,radius); }
        return mix(visibility,nextVisibility,blend);
    }
    return 1;
}
// Three distributed, uniformly cycled members of the same 3x3 PCF footprint.
// The volume's 16 ray samples and temporal filter integrate it over time.
// Surface and leaf lighting use their deterministic filters above.
float fogCascadeShadow(int cascade,vec3 q,float bias,vec2 tap) {
    if(max(abs(q.x),abs(q.y)) >= 1.0 || q.z <= 0 || q.z >= 1) return 1;
    float texel=cascade == 2 ? 1.0/1024.0 : 1.0/2048.0;
    return q.z+bias >= readShadow(cascade,q.xy*.5+.5+tap*texel) ? 1.0 : 0.0;
}
float fogShadowAt(vec3 p,float bias,vec2 tap) {
    for(int i=0;i<3;i++) {
        vec4 clip=ShadowMatrices[i]*vec4(p,1);
        vec3 q=clip.xyz/clip.w;
        float edge=max(abs(q.x),abs(q.y));
        if(edge >= .98 || q.z <= 0 || q.z >= 1) continue;
        float visibility=fogCascadeShadow(i,q,bias,tap),blend=smoothstep(.84,.98,edge);
        if(blend <= 0) return visibility;
        float nextVisibility=1;
        if(i<2) { vec4 nextClip=ShadowMatrices[i+1]*vec4(p,1);nextVisibility=fogCascadeShadow(i+1,nextClip.xyz/nextClip.w,bias,tap); }
        return mix(visibility,nextVisibility,blend);
    }
    return 1;
}
