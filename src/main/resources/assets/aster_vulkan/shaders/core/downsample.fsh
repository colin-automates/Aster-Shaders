#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D HdrInput;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 pixel = 1.0/vec2(textureSize(HdrInput,0));
    vec4 total = vec4(0.0);
    for (int x=-1; x<=1; x++) for (int y=-1; y<=1; y++) {
        float weight = (x == 0 ? 2.0 : 1.0)*(y == 0 ? 2.0 : 1.0)/16.0;
        vec4 value = texture(HdrInput,texCoord+vec2(x,y)*pixel);
#ifdef FIRST_DOWNSAMPLE
        float luminance = dot(value.rgb,vec3(0.2126,0.7152,0.0722));
#ifdef TAA_INPUT
        value.a=log2(max(luminance,.0001));
#endif
        value.rgb *= max(luminance-0.65,0.0)/max(luminance,0.0001);
#endif
        total += value*weight;
    }
    fragColor = total;
}
