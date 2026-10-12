#version 330 compatibility
#define ASTER_END
#define ASTER_FRAGMENT

#include "/lib/settings.glsl"
uniform sampler2D colortex0;
varying vec2 texcoord;
/* RENDERTARGETS: 9 */
void main(){
    vec2 pixel=1.0/vec2(textureSize(colortex0,0));vec3 sum=vec3(0);
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec3 color=texture(colortex0,texcoord+vec2(x,y)*pixel*2.0).rgb;
        float l=dot(color,vec3(.2126,.7152,.0722));sum+=color*smoothstep(.7,2.0,l);
    }
    gl_FragData[0]=vec4(sum/9.0,1);
}
