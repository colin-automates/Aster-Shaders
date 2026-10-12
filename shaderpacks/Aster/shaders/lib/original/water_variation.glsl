#ifndef ASTER_ORIGINAL_WATER_VARIATION
#define ASTER_ORIGINAL_WATER_VARIATION
#ifndef ASTER_WATER_VARIATION_GLSL
#define ASTER_WATER_VARIATION_GLSL
uint waterCellHash(ivec2 cell,uint seed) {
    uint h=uint(cell.x)*1597334677u ^ uint(cell.y)*3812015801u ^ seed;
    h^=h>>16;h*=2246822519u;h^=h>>13;h*=3266489917u;h^=h>>16;
    return h;
}
float waterCellValue(ivec2 cell,uint seed) { return float(waterCellHash(cell,seed)>>8)*(2.0/16777215.0)-1.0; }
// Continuous value and exact spatial gradient. Quintic interpolation also
// keeps the first two derivatives continuous across the noise cell borders.
vec3 waterGroupNoise(vec2 p,uint seed,ivec2 baseCell) {
    ivec2 cell=baseCell+ivec2(floor(p));vec2 u=fract(p);
    vec2 f=u*u*u*(u*(u*6.0-15.0)+10.0);
    vec2 df=30.0*u*u*(u-1.0)*(u-1.0);
    float a=waterCellValue(cell,seed),b=waterCellValue(cell+ivec2(1,0),seed);
    float c=waterCellValue(cell+ivec2(0,1),seed),d=waterCellValue(cell+ivec2(1,1),seed);
    return vec3(mix(mix(a,b,f.x),mix(c,d,f.x),f.y),
                mix(b-a,d-c,f.y)*df.x,mix(c-a,d-b,f.x)*df.y);
}
vec3 waterGroupNoise(vec2 p,uint seed) { return waterGroupNoise(p,seed,ivec2(0)); }
vec2 waterRippleGradient(ivec2 cell,uint seed) {
    // Eight directions keep the hash evaluation free of transcendental work.
    uint h=waterCellHash(cell,seed);
    vec2 g=mix(vec2(1,0),vec2(.70710678,.70710678),float(h&1u));
    g=mix(g,vec2(-g.y,g.x),float((h>>1)&1u));
    // Independent bits vary individual lens strength, so every noise knot does
    // not contribute the same amplitude. Normalize the exact discrete-uniform
    // second moment to one, retaining the averaged height/slope variance used
    // by the existing roughness estimate. This is not a pointwise slope bound.
    float strength=(.35+float((h>>3)&65535u)*(1.0/65535.0))*1.11397822227;
    return g*(1.0-2.0*float((h>>2)&1u))*strength;
}
vec3 waterRippleKernel(vec2 p,ivec2 cell,uint seed) {
    vec2 g=waterRippleGradient(cell,seed);
    float support=max(.5-dot(p,p),0.0),s=dot(g,p);
    float cube=support*support*support,fourth=cube*support;
    return vec3(fourth*s,fourth*g-8.0*cube*s*p);
}
vec3 waterRippleNoise(vec2 p,uint seed,ivec2 baseCell) {
    // Standard simplex lattice geometry, with our full integer cell hash and
    // radial kernel differentiated explicitly. This is separate from the broad
    // wave envelopes so their geometry and shore taper keep the same behaviour.
    const float skew=.3660254037844386,unskew=.2113248654051871;
    ivec2 cell=ivec2(floor(p+(p.x+p.y)*skew));
    vec2 x=p-(vec2(cell)-float(cell.x+cell.y)*unskew);
    ivec2 side=x.x>x.y ? ivec2(1,0) : ivec2(0,1);
    vec3 field=waterRippleKernel(x,baseCell+cell,seed)
        +waterRippleKernel(x-vec2(side)+unskew,baseCell+cell+side,seed)
        +waterRippleKernel(x-1.0+2.0*unskew,baseCell+cell+ivec2(1),seed);
    // Integral gradient energy of unit hashed directions is pi*sqrt(3)/4032
    // before normalization. Match the previous two-axis variance of 1.49248.
    return field*33.2551776;
}
vec3 waterGroupOctave(vec2 p,float scale,vec2 drift,uint seed) {
    vec3 n=waterGroupNoise(p*scale+drift,seed);
    return vec3(n.x,n.yz*scale);
}
void waterWaveVariation(vec2 p,float t,out vec3 a,out vec3 b) {
    // World coordinates make patches independent of camera motion. Circular
    // drift closes the existing 1200-second clock without resetting noise.
    float angle=t*(6.28318530718/1200.0);
    vec2 driftA=vec2(cos(angle*3.0),sin(angle*3.0))*.65;
    vec2 driftB=vec2(sin(angle*5.0),cos(angle*5.0))*.55;
    a=.70*waterGroupOctave(p,.042,driftA,104729u)
      +.30*waterGroupOctave(p,.101,driftB+vec2(17.3,-9.1),130363u);
    b=.72*waterGroupOctave(p,.061,driftB+vec2(-11.7,5.9),155921u)
      +.28*waterGroupOctave(p,.137,driftA+vec2(7.1,21.2),196613u);
}
#endif

#endif
