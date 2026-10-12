#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:fog_volume.glsl>
uniform sampler2D FogVolume;
uniform sampler2D FogCoefficients;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    bool energy=true,bounded=true,identity=true;
    for(int y=0;y<4;y++) for(int x=0;x<4;x++) {
        vec2 uv=(vec2(x,y)+.5)/4.0;
        vec3 scattering=vec3(0);float tau=0.0,previousT=1.0;
        for(int z=0;z<64;z++) {
            vec4 c=fogSliceSample(FogCoefficients,uv,z);
            float ds=fogSliceDistance(float(z+1))-fogSliceDistance(float(z));
            float segmentT=exp(-max(c.a,0.0)*ds);
            scattering+=exp(-tau)*c.rgb*(1.0-segmentT)/max(c.a,.000001);
            tau+=c.a*ds;
            vec4 integral=fogAtDepth(FogVolume,uv,fogSliceDistance(float(z+1)));
            energy=energy && length(integral.rgb-scattering) < .002 && abs(integral.a-exp(-tau)) < .002;
            bounded=bounded && !any(isnan(integral)) && !any(isinf(integral)) &&
                all(greaterThanEqual(integral,vec4(0))) && integral.a <= previousT+.0002;
            previousT=integral.a;
            if(SceneInfo.w < .5 || SceneInfo.z > .5)
                identity=identity && length(integral.rgb) < .00001 && abs(integral.a-1.0) < .00001;
        }
        vec4 near=fogAtDepth(FogVolume,uv,0.0);
        identity=identity && length(near.rgb) < .00001 && abs(near.a-1.0) < .00001;
    }
    fragColor=vec4(energy ? 1 : 0,bounded ? 1 : 0,identity ? 1 : 0,1);
}
