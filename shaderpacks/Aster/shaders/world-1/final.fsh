#version 330 compatibility
#define ASTER_NETHER
#define ASTER_FRAGMENT

#include "/lib/common.glsl"
#define HdrInput colortex0
#define Exposure colortex10
#define Bloom0 colortex9
#define Bloom1 colortex9
#define Bloom2 colortex9
#define Bloom3 colortex9
#define TAA_INPUT
varying vec2 texcoord;
vec3 filmic(vec3 c) {
    return (c*(2.51*c+.03))/(c*(2.43*c+.59)+.14);
}
float edgeLuma(vec3 hdr,float exposure) {
    return dot(linearToDisplay(clamp(filmic(hdr*exposure),0.0,1.0)),vec3(.299,.587,.114));
}
vec3 filteredScene(vec2 uv,float exposure) {
    vec2 pixel=1.0/vec2(textureSize(HdrInput,0));
    vec3 center=texture(HdrInput,uv).rgb;
    float m=edgeLuma(center,exposure);
    float nw=edgeLuma(texture(HdrInput,uv+pixel*vec2(-1,-1)).rgb,exposure);
    float ne=edgeLuma(texture(HdrInput,uv+pixel*vec2(1,-1)).rgb,exposure);
    float sw=edgeLuma(texture(HdrInput,uv+pixel*vec2(-1,1)).rgb,exposure);
    float se=edgeLuma(texture(HdrInput,uv+pixel*vec2(1,1)).rgb,exposure);
    float lo=min(m,min(min(nw,ne),min(sw,se))),hi=max(m,max(max(nw,ne),max(sw,se)));
    // Ignore low-contrast texture detail. Filter across coherent pixel edges
    // in scene-linear colour before the final shoulder and display encoding.
    if(hi-lo<max(.045,hi*.15))return center;
    vec2 direction=vec2(-(nw+ne-sw-se),nw+sw-ne-se);
    float reduce=max((nw+ne+sw+se)*(.25*.125),.0078125);
    direction=clamp(direction/(min(abs(direction.x),abs(direction.y))+reduce),vec2(-8),vec2(8))*pixel;
    vec3 a=.5*(texture(HdrInput,uv+direction*(-1.0/6.0)).rgb+texture(HdrInput,uv+direction*(1.0/6.0)).rgb);
    vec3 b=a*.5+.25*(texture(HdrInput,uv-direction*.5).rgb+texture(HdrInput,uv+direction*.5).rgb);
    float l=edgeLuma(b,exposure);
    return l<lo||l>hi ? a : b;
}
void main() {
    float exposure=texture(Exposure,vec2(0.5)).r;
#ifdef TAA_INPUT
    vec3 color = texture(HdrInput,texcoord).rgb;
#else
    vec3 color = verificationFlag(128) ? texture(HdrInput,texcoord).rgb : filteredScene(texcoord,exposure);
#endif
    if(verificationFlag(128)) { gl_FragData[0]=vec4(clamp(color,0.0,1.0),1);return; }
    vec3 bloom = texture(Bloom0,texcoord).rgb*0.35 + texture(Bloom1,texcoord).rgb*0.30
        + texture(Bloom2,texcoord).rgb*0.22 + texture(Bloom3,texcoord).rgb*0.13;
    // AsterSettings.w carries hidden comparison flags, never bloom strength.
    color += bloom*BLOOM_STRENGTH;
    color *= exposure;
    // Blend luminance and per-channel compression to keep foliage/earth hue
    // consistent as exposure changes. The highlight shoulder stays bounded.
    float luminance=max(dot(color,vec3(.2126,.7152,.0722)),.000001);
    vec3 huePreserved=color*(filmic(vec3(luminance)).r/luminance);
    vec3 mapped=mix(filmic(color),huePreserved,.42);
    float peak=max(mapped.r,max(mapped.g,mapped.b));
    mapped=mix(mapped,vec3(dot(mapped,vec3(.2126,.7152,.0722))),smoothstep(.90,1.35,peak)*.45);
    // A gentle toe recovers dim texture without adding illumination to black.
    vec3 toe=pow(max(color,vec3(0)),vec3(.88));
    mapped=mix(mapped,toe,smoothstep(.12,0.0,luminance)*.20);
    mapped=clamp(mapped,0.0,1.0);
    // Authored luminance contrast: retain black/white endpoints and hue while
    // reducing the lifted midtone wash. Ease out in dim detail so caves and
    // night retain the existing toe. This curve adds no illumination.
    float displayLuma=dot(mapped,vec3(.2126,.7152,.0722));
    float contrastStrength=.25*smoothstep(.015,.12,displayLuma);
    float contrasted=displayLuma*(1.0-contrastStrength*(1.0-displayLuma)*(1.0-displayLuma));
    mapped*=contrasted/max(displayLuma,.000001);
    gl_FragData[0] = vec4(clamp(linearToDisplay(mapped),0.0,1.0),1.0);
}
