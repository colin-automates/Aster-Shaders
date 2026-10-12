#version 330 compatibility
#define ASTER_NETHER
#define ASTER_FRAGMENT

#include "/lib/common.glsl"
#include "/lib/temporal.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: 0,6,7 */
void main(){
    float depth=texture(depthtex0,texcoord).r;
    vec3 current=texture(colortex0,texcoord).rgb;
    vec3 resolved=temporalResolve(texcoord,current,depth);
    gl_FragData[0]=vec4(resolved,1);
    gl_FragData[1]=vec4(resolved,1);
    gl_FragData[2]=vec4(depth,1,0,1);
}
