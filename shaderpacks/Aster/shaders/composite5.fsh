#version 330 compatibility
#define ASTER_FRAGMENT

#include "/lib/settings.glsl"
uniform sampler2D colortex0,colortex10;
uniform float frameTime;
uniform int frameCounter;
varying vec2 texcoord;
/* RENDERTARGETS: 10 */
void main(){
    float sum=0;
    for(int y=0;y<16;y++)for(int x=0;x<24;x++){
        vec3 color=texture(colortex0,(vec2(x,y)+.5)/vec2(24,16)).rgb;
        sum+=log2(max(dot(color,vec3(.2126,.7152,.0722)),.0001));
    }
    float luminance=exp2(sum/384.0);
    float target=clamp(.14/max(luminance,.004),EXPOSURE_MIN,EXPOSURE_MAX);
    float previous=texture(colortex10,vec2(.5)).r;
    float rate=target<previous?2.5:.8;
    float weight=frameCounter<3||previous<=0?1.0:1.0-exp(-frameTime*rate);
    gl_FragData[0]=vec4(mix(previous,target,weight),luminance,0,1);
}
