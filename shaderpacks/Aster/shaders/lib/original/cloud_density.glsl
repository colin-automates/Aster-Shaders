#ifndef ASTER_ORIGINAL_CLOUD_DENSITY
#define ASTER_ORIGINAL_CLOUD_DENSITY
#ifndef ASTER_CLOUD_DENSITY_GLSL
#define ASTER_CLOUD_DENSITY_GLSL
uniform sampler2D CloudNoise;
float cloudNoise(vec3 p) {
    vec3 cell=mod(floor(p),128.0),f=fract(p);f=f*f*(3.0-2.0*f);
    float next=mod(cell.z+1.0,128.0);
    vec2 tile0=vec2(mod(cell.z,16.0),floor(cell.z/16.0))*130.0;
    vec2 tile1=vec2(mod(next,16.0),floor(next/16.0))*130.0;
    vec2 pixel=cell.xy+f.xy+1.5;
    return mix(textureLod(CloudNoise,(tile0+pixel)/vec2(2080,1040),0.0).r,
               textureLod(CloudNoise,(tile1+pixel)/vec2(2080,1040),0.0).r,f.z);
}

// Weather varies over hundreds of blocks. The warped 3D field creates lobes
// inside those systems, rather than placing one similar sphere in every cell.
float cloudShape(vec3 p,float rain) {
    if(p.y <= 0.0 || p.y >= 1.0) return 0.0;
    vec2 w=p.xz;
    float weather=cloudNoise(vec3(w*.34,19.7))*.72+cloudNoise(vec3(w*.83,57.2))*.28;
    float coverage=smoothstep(.28,.73,weather);
    float type=cloudNoise(vec3(w*.47+vec2(27.4,8.2),33.6));
    float base=.035+.13*cloudNoise(vec3(w*.69,81.2));
    float ceiling=mix(.42,.99,smoothstep(.18,.72,type));
    float h=(p.y-base)/max(ceiling-base,.1);
    if(h <= 0.0 || h >= 1.0) return 0.0;
    float profile=smoothstep(0.0,.075,h)*(1.0-smoothstep(.68,1.0,h));
    vec2 warp=vec2(cloudNoise(vec3(w*.78,7.1)),cloudNoise(vec3(w*.78+11.5,42.8)))-.5;
    // Broader billows carry the silhouette; finer noise only shapes edges.
    // This field is shared by the sky and the ground cloud-shadow pass.
    vec3 q=vec3((w+warp*.65)*1.60,h*1.65);
    // The lower-frequency octave forms irregular connected banks; the next
    // two build unequal billows without a shared radius or orientation.
    float coarse=cloudNoise(q),middle=cloudNoise(q*2.07+vec3(17.1,9.2,13.7));
    float fine=cloudNoise(q*4.31+vec3(43.2,28.4,6.1));
    float shape=mix(coarse*.67+middle*.25+fine*.08,coarse*.78+middle*.18+fine*.04,type);
    float threshold=.70-coverage*.32-rain*.19+(1.0-profile)*.28;
    return max(shape-threshold,0.0)*6.0*profile;
}
float cloudDensity(vec3 p,float rain,float footprint) {
    float shape=cloudShape(p,rain);
    if(shape <= .001) return 0.0;
    vec3 q=p*17.0;
    float fineFilter=1.0-smoothstep(.015,.055,footprint);
    float erosion=cloudNoise(q+vec3(6.4,12.1,8.3))*.68+mix(.5,cloudNoise(q*2.03+23.7),fineFilter)*.32;
    // Remove fine structure as ray footprints grow near the horizon.
    float detail=1.0-smoothstep(.03,.12,footprint);
    return max(shape-(1.0-erosion)*.40*detail,0.0);
}
#endif

#endif
