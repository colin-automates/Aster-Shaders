#version 330 compatibility
#define ASTER_NETHER
varying vec4 tint;
void main(){gl_Position=ftransform();tint=gl_Color;}
