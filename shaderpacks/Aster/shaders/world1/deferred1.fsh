#version 330 compatibility
#define ASTER_END
#define ASTER_FRAGMENT

#include "/lib/settings.glsl"
uniform sampler2D colortex0;
varying vec2 texcoord;
/* RENDERTARGETS: 5 */
const bool colortex5MipmapEnabled = true;
void main(){gl_FragData[0]=texture(colortex0,texcoord);}
