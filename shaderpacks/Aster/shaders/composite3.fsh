#version 330 compatibility
#define ASTER_FRAGMENT

#include "/lib/settings.glsl"
uniform sampler2D colortex9;
varying vec2 texcoord;
/* RENDERTARGETS: 9 */
void main(){
    vec2 delta=vec2(1,0)/vec2(textureSize(colortex9,0));
    vec3 sum=texture(colortex9,texcoord).rgb*.227027;
    sum+=(texture(colortex9,texcoord+delta*1.384615).rgb+texture(colortex9,texcoord-delta*1.384615).rgb)*.316216;
    sum+=(texture(colortex9,texcoord+delta*3.230769).rgb+texture(colortex9,texcoord-delta*3.230769).rgb)*.070270;
    gl_FragData[0]=vec4(sum,1);
}
