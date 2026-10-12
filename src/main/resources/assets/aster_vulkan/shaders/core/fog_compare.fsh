#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D FilteredFog;
uniform sampler2D ReferenceFog;
uniform sampler2D RawFog;
uniform sampler2D ReferenceRawFog;
layout(location=0) out vec4 fragColor;
void main() {
    float sum=0.0,largest=0.0;bool equalExtinction=true;
    for(int y=0;y<64;y++) for(int x=0;x<64;x++) {
        vec2 uv=(vec2(x,y)+.5)/64.0;
        vec3 difference=abs(texture(FilteredFog,uv).rgb-texture(ReferenceFog,uv).rgb);
        sum+=dot(difference,vec3(1.0/3.0));
        largest=max(largest,max(difference.r,max(difference.g,difference.b)));
        equalExtinction=equalExtinction && abs(texture(RawFog,uv).a-texture(ReferenceRawFog,uv).a) < .000001;
    }
    // Readback encoding, independent of SDR clipping/exposure. Saturation
    // implies an error above the acceptance bound rather than a false pass.
    fragColor=vec4(sum/4096.0*512.0,largest*32.0,equalExtinction ? 1 : 0,1);
}
