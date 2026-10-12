#version 330 compatibility
varying vec4 tint;
void main(){gl_Position=ftransform();tint=gl_Color;}
