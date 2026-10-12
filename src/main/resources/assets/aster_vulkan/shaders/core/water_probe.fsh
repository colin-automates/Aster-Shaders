#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#define SKY_NO_CLOUD_TEXTURE
#include <aster_vulkan:water.glsl>
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    int pixel=int(gl_FragCoord.x);
    if(pixel == 0) fragColor=vec4(waterDielectricFresnel(1.0,false)*8.0,waterDielectricFresnel(1.0,true)*8.0,waterDielectricFresnel(.15,false),1);
    else if(pixel == 1) fragColor=vec4(waterDielectricFresnel(.5,true),waterDielectricFresnel(.9,true),waterDielectricFresnel(.5,false),1);
    else if(pixel == 2) fragColor=vec4(waterAbsorption(5.0),1);
    else if(pixel == 3) fragColor=vec4(waterAbsorption(0.0),1);
    else if(pixel == 4) {
        vec2 p=vec2(26.6,9.2);float epsilon=.01;
        vec2 numerical=vec2(waterWave(p+vec2(epsilon,0))-waterWave(p-vec2(epsilon,0)),waterWave(p+vec2(0,epsilon))-waterWave(p-vec2(0,epsilon)))/(2.0*epsilon);
        float error=length(numerical-waterWaveGradient(p))*AsterSettings.y;
        fragColor=vec4(error*1000.0,error < .0005 ? 1.0 : 0.0,0,1);
    }
    else if(pixel == 5) fragColor=vec4(underwaterAbsorption(8.0),1);
    else if(pixel == 6) fragColor=vec4(underwaterAbsorption(0.0),1);
    else {
        bool bounded=all(equal(underwaterAbsorption(-1.0),vec3(1)));
        bool monotonic=true,composition=true;
        for(int i=0;i<512;i++) {
            float d=float(i)*(96.0/511.0);
            vec3 a=underwaterAbsorption(d),b=underwaterAbsorption(d+.125);
            bounded=bounded && all(greaterThanEqual(a,vec3(0)))
                && all(lessThanEqual(a,vec3(1))) && a.r<=a.g && a.g<=a.b;
            monotonic=monotonic && all(greaterThanEqual(a,b));
            vec3 split=underwaterAbsorption(d*.37)*underwaterAbsorption(d*.63);
            composition=composition && all(lessThanEqual(abs(a-split),vec3(.000001)));
        }
        fragColor=vec4(bounded?1:0,monotonic?1:0,composition?1:0,1);
    }
}
