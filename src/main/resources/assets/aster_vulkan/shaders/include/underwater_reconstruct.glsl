uniform sampler2D UnderwaterShafts;
vec3 underwaterShaftsAt(vec2 uv,vec3 origin,vec3 delta) {
    float distance=length(delta);
    ivec2 size=textureSize(UnderwaterShafts,0);
    vec2 grid=uv*vec2(size)-.5,f=fract(grid);ivec2 base=ivec2(floor(grid));
    vec3 sum=vec3(0);float total=0.0;
    for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        vec4 sampleValue=texelFetch(UnderwaterShafts,clamp(base+ivec2(x,y),ivec2(0),size-1),0);
        float error=abs(sampleValue.a-distance),tolerance=.25+distance*.02;
        float weight=(x==0 ? 1.0-f.x : f.x)*(y==0 ? 1.0-f.y : f.y);
        weight*=sampleValue.a>0.0 && error<tolerance ? exp(-error*error/(tolerance*tolerance)) : 0.0;
        sum+=sampleValue.rgb*weight;total+=weight;
    }
    // A conservative half-res endpoint can reject every neighbor exactly at
    // a silhouette. Trace that pixel's actual segment instead of drawing a
    // black missing-light seam or importing light from the other depth layer.
    return total>.0001 ? sum/total : underwaterSunShafts(origin,delta);
}
