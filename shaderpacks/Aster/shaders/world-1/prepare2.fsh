#version 330 compatibility
#define ASTER_NETHER
#define ASTER_FRAGMENT

#include "/lib/common.glsl"
#include "/lib/original/water_caustic_source.glsl"
varying vec2 texcoord;
/* RENDER12S: 12 */
void main(){
    if(SkySun.y<.02){gl_FragData[0]=vec4(0);return;}
    vec2 center=floor(cameraPosition.xz/16.0)*16.0;
    vec2 p=center+(texcoord-.5)*512.0;
    float e=.5;
    vec2 dx=(causticSourceField(p+vec2(e,0),0.0,true).yz-causticSourceField(p-vec2(e,0),0.0,true).yz)/(2.0*e);
    vec2 dz=(causticSourceField(p+vec2(0,e),0.0,true).yz-causticSourceField(p-vec2(0,e),0.0,true).yz)/(2.0*e);
    gl_FragData[0]=vec4(dx,dz);
}
