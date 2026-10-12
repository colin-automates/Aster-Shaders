#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif
#if defined(PER_FACE_LIGHTING) && !defined(ASTER_ITEM_CASTER)
layout(location=2) in vec4 vertexPerFaceColorBack;
layout(location=3) in vec4 vertexPerFaceColorFront;
#else
layout(location=2) in vec4 vertexColor;
#endif
#ifdef ASTER_ITEM_CASTER
layout(location=5) in vec2 texCoord0;
#else
layout(location=6) in vec2 texCoord0;
#endif
layout(location=0) out vec4 fragColor;
void main() {
    vec4 color=texture(Sampler0,texCoord0);
#ifdef ALPHA_CUTOUT
    if(color.a < ALPHA_CUTOUT) discard;
#else
    if(color.a < .1) discard;
#endif
#if defined(PER_FACE_LIGHTING) && !defined(ASTER_ITEM_CASTER)
    float coverage=gl_FrontFacing ? vertexPerFaceColorFront.a : vertexPerFaceColorBack.a;
#else
    float coverage=vertexColor.a;
#endif
#ifdef DISSOLVE
    if(coverage < texture(DissolveMaskSampler,texCoord0).a) discard;
    coverage=1.0;
#endif
    if(color.a*coverage*ColorModulator.a < .1) discard;
    fragColor=vec4(1);
}
