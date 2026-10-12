// Native tint alpha holds bend height or leaf type; texture alpha is untouched.
int windTag(float alpha) { return int(floor(alpha*255.0+.5)); }
bool windLeaf(int tag) {return tag>=96 && tag<=99;}
bool windTagged(float alpha) { int tag=windTag(alpha);return (tag >= 1 && tag <= 63) || windLeaf(tag); }
bool terrainTintTagged(float alpha) {int tag=windTag(alpha);return (tag>=1 && tag<=67)||windLeaf(tag);}
float nativeFaceShade(float alpha) {
    int tag=windTag(alpha);
    if(!((tag>=64 && tag<=67)||windLeaf(tag)))return 1.0;
    int face=tag>=96?tag-96:tag-64;
    return face==0?.5:face==1?.6:face==2?.8:1.0;
}

vec3 foliageWindOffset(vec3 p,float height,bool leaf,float t,float rain) {
    // Integer frequencies make the 1200-second native clock loop continuous.
    float clock=t*(6.28318530718/1200.0);
    float gust=.65+.35*sin(dot(p.xz,vec2(.071,.047))-clock*83.0);
    float sway=sin(dot(p.xz,vec2(.19,.13))-clock*283.0)
              +.32*sin(dot(p.xz,vec2(-.31,.23))+clock*461.0);
    float cross=sin(dot(p.xz,vec2(.23,-.17))-clock*347.0);
    float weather=1.0+.5*clamp(rain,0.0,1.0);
    if(leaf) {
        // Continuous world-space deformation keeps shared leaf corners together.
        // No per-block phase/hash that would tear open a canopy's cube faces.
        float flutter=sin(dot(p,vec3(1.7,.9,1.3))+clock*977.0);
        return vec3(.92*(.035*sway*gust+.008*flutter)+.014*cross,
                    .009*sin(dot(p,vec3(.37,.51,.23))+clock*419.0)*gust,
                    .39*(.035*sway*gust+.008*flutter)-.025*cross)*weather;
    }
    float bend=height*(.7+.3*min(height,1.0));
    // Bottom vertices have exactly zero motion. A double plant shares the
    // same height=1 bend at its lower/upper join, including chunk boundaries.
    return vec3(.92*(.11*sway*gust+.025*gust)+.024*cross,0,
                .39*(.11*sway*gust+.025*gust)-.055*cross)*bend*weather;
}

vec3 windPosition(vec3 relative,float alpha) {
    int tag=windTag(alpha);
    if(AsterSettings.x < .5 || !windTagged(alpha) || verificationFlag(268435456)) return relative;
    return relative+foliageWindOffset(relative+CameraWorldTime.xyz,float(tag-1)/31.0,windLeaf(tag),
                                     SkyTiming.y,1.0-AtmosphereColor.a);
}
