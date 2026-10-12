#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D MaterialAtlas;
uniform sampler2D NormalAtlas;
layout(std140) uniform MaterialProbe { vec4 ProbeUv; };
layout(location=0) out vec4 fragColor;
void main() {
    vec4 sampleData=ProbeUv.z > .5 ? textureLod(NormalAtlas,ProbeUv.xy,ProbeUv.w) : textureLod(MaterialAtlas,ProbeUv.xy,ProbeUv.w);
    fragColor=vec4(gl_FragCoord.x > 1.0 ? sampleData.aaa : sampleData.rgb,1);
}
