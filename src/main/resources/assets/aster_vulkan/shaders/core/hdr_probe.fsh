#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D HdrInput;
uniform sampler2D Exposure;
uniform sampler2D MaterialData;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    // Diagnostic: GPU reduction across the actual image, independent of tone mapping.
    float maxRadiance = 0.0;
    float emissivePixels = 0.0;
    ivec2 size = textureSize(HdrInput,0);
    for (int y=0; y<64; y++) for (int x=0; x<64; x++) {
        vec2 uv = (vec2(x,y)+0.5)/64.0;
        vec3 radiance = texture(HdrInput,uv).rgb;
        maxRadiance = max(maxRadiance,max(radiance.r,max(radiance.g,radiance.b)));
        emissivePixels += texture(MaterialData,uv).b > 0.5 ? 1.0 : 0.0;
    }
    float exposure = texture(Exposure,vec2(0.5)).r;
    fragColor = vec4(maxRadiance > 1.05 ? 1.0 : 0.0,emissivePixels > 0.0 ? 1.0 : 0.0,clamp(exposure/4.0,0.0,1.0),1.0);
}
