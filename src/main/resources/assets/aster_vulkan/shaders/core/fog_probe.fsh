#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:fog.glsl>
uniform sampler2D RawFog;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    bool attenuation=true,energy=true,bounded=true;
    for(int j=0;j<4;j++) {
        float sigma=.0045*float(j+1),distance=20.0*float(j+1);
        vec3 source=vec3(.2,.3,.4),scattering=vec3(0);float t=1.0;
        for(int i=0;i<16;i++) {
            float segment=fogSegmentTransmittance(sigma,distance/16.0);
            scattering+=t*(1.0-segment)*source;t*=segment;
        }
        float expected=exp(-sigma*distance);
        attenuation=attenuation && abs(t-expected) < .00001;
        energy=energy && length(scattering-source*(1.0-expected)) < .00001;
        bounded=bounded && t >= 0.0 && t <= 1.0 && all(greaterThanEqual(scattering,vec3(0))) && fogPhase(-1.0) > 0.0 && fogPhase(1.0) < 10.0;
    }
    for(int y=0;y<32;y++) for(int x=0;x<32;x++) {
        vec4 fog=texture(RawFog,(vec2(x,y)+.5)/32.0);
        bounded=bounded && !any(isnan(fog)) && !any(isinf(fog)) && all(greaterThanEqual(fog,vec4(0))) && fog.a <= 1.0;
        if(SceneInfo.w < .5 || SceneInfo.z > .5) bounded=bounded && length(fog.rgb) < .00001 && abs(fog.a-1.0) < .00001;
    }
    fragColor=vec4(attenuation ? 1 : 0,energy ? 1 : 0,bounded ? 1 : 0,1);
}
