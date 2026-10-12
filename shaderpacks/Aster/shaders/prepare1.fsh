#version 330 compatibility
#define ASTER_FRAGMENT

#include "/lib/common.glsl"
#include "/lib/original/water_caustic_source.glsl"
varying vec2 texcoord;
/* RENDER11S: 11 */
void main(){
    if(SkySun.y<.02){gl_FragData[0]=vec4(0);return;}
    vec2 center=floor(cameraPosition.xz/16.0)*16.0;
    vec2 p=center+(texcoord-.5)*128.0;
    float e=.125;
    vec2 dx=(causticSourceField(p+vec2(e,0),0.0,false).yz-causticSourceField(p-vec2(e,0),0.0,false).yz)/(2.0*e);
    vec2 dz=(causticSourceField(p+vec2(0,e),0.0,false).yz-causticSourceField(p-vec2(0,e),0.0,false).yz)/(2.0*e);
    gl_FragData[0]=vec4(dx,dz);
}
