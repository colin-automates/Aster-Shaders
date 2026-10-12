#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D CausticInput;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
#ifdef CAUSTIC_SHALLOW
    fragColor=vec4(vec3(clamp(texture(CausticInput,texCoord).r/8.0,0.0,1.0)),1);
#else
    vec4 density=texture(CausticInput,vec2(texCoord.x,fract(texCoord.y*2.0)))/8.0;
    fragColor=vec4(clamp(texCoord.y<.5?density.rgb:vec3(density.a),0.0,1.0),1);
#endif
}
