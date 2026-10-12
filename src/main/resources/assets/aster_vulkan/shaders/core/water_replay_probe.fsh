#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D ActualHdr;
uniform sampler2D ClearedReplayHdr;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec3 actual=texture(ActualHdr,texCoord).rgb;
    vec3 difference=abs(actual-texture(ClearedReplayHdr,texCoord).rgb);
    float error=max(difference.r,max(difference.g,difference.b));
    fragColor=vec4(error>.0001 ? 1:0,clamp(error*32.0,0.0,1.0),
                   max(actual.r,max(actual.g,actual.b))>1.0 ? 1:0,1);
}
