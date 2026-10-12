#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#define SKY_NO_CLOUD_TEXTURE
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:water_reflection.glsl>
layout(location=0) out vec4 fragColor;
vec2 rippleNumericalGradient(vec2 p,float t,float rain,float e) {
    return vec2(waterRippleField(p+vec2(e,0),t,rain,.03).x-waterRippleField(p-vec2(e,0),t,rain,.03).x,
                waterRippleField(p+vec2(0,e),t,rain,.03).x-waterRippleField(p-vec2(0,e),t,rain,.03).x)
                /vec2((p.x+e)-(p.x-e),(p.y+e)-(p.y-e));
}
void main() {
    int sampleIndex=int(gl_FragCoord.x),i=sampleIndex%256;
    vec2 p=vec2(float(i%16)*7.37-55.1,float(i/16)*9.13-71.7);
    if(sampleIndex>=256) p+=vec2(-1531.6124,119.0645);
    // Include both sides of the bounded spatial-phase anchor boundaries.
    // Short-wave precision must also hold where adjacent 16-block patches meet.
    if(i%16==0) p.x=round(p.x/16.0)*16.0;
    if(i%16==8) p.y=round(p.y/16.0)*16.0;
    float t=12.0+float(i)*.37,rain=float(i%2),e=.01;
    vec3 wave=waterWaveField(p,t,rain);
    vec2 numerical=vec2(waterWaveField(p+vec2(e,0),t,rain).x-waterWaveField(p-vec2(e,0),t,rain).x,
                         waterWaveField(p+vec2(0,e),t,rain).x-waterWaveField(p-vec2(0,e),t,rain).x)/(2.0*e);
    bool macro=length(numerical-wave.yz)*AsterSettings.y < .0005;
    // Large coordinates lose significant digits with tiny offsets. Use a
    // six-order estimate to cancel central-difference curvature error while
    // keeping each offset well above the float world-coordinate spacing.
    vec3 ripple=waterRippleField(p,t,rain,.03);
    e=sampleIndex>=256 ? .015625 : .002;
    numerical=rippleNumericalGradient(p,t,rain,e);
    if(sampleIndex>=256) numerical=1.5*numerical-.6*rippleNumericalGradient(p,t,rain,2.0*e)
        +.1*rippleNumericalGradient(p,t,rain,3.0*e);
    float detailError=length(numerical-ripple.yz);
    bool detail=detailError < .001;
    float combinedSlope=length(wave.yz*AsterSettings.y+ripple.yz);
    bool bounded=abs(wave.x)*AsterSettings.y <= .1261 && combinedSlope < .5;
    vec3 a=waterWaveField(p,1199.9999,rain),b=waterWaveField(p,.0001,rain);
    vec3 ra=waterRippleField(p,1199.9999,rain,.03),rb=waterRippleField(p,.0001,rain,.03);
    bool loop=abs(a.x-b.x)*AsterSettings.y < .0003 && length((a.yz-b.yz)*AsterSettings.y+ra.yz-rb.yz) < .003;
    vec2 rainError=vec2(length(waterWaveField(p,t,1.0)-waterWaveField(p,t,0.0)*1.4),
        length(waterRippleField(p,t,1.0,.03)-waterRippleField(p,t,0.0,.03)*2.1));
    bool rainResponse=all(lessThan(rainError,vec2(.00001)));
    bool minified=length(waterRippleField(p,t,rain,2.0).yz) < .001;
    float support=.001+float(sampleIndex)*.002;
    float roughNear=waterSurfaceRoughness(support),roughFar=waterSurfaceRoughness(support+.002);
    bool roughnessEnergy=roughNear >= .17999 && roughFar >= roughNear-.000001 && roughFar <= .40001;
    // Keep all original acceptance rows unchanged. RGB (not screenshot alpha)
    // adds numeric component magnitudes to identify remaining slope headroom.
    if(gl_FragCoord.y > 3.0) {
        fragColor=vec4(combinedSlope*.5,length(wave.yz*AsterSettings.y)*.5,length(ripple.yz)*.5,1);
        return;
    }
    if(gl_FragCoord.y > 2.0) {fragColor=vec4(detailError*100.0,roughnessEnergy ? 1:0,max(rainError.x,rainError.y)*10000.0,1);return;}
    fragColor=gl_FragCoord.y < 1.0
        ? vec4(macro ? 1.0 : 0.0,detail ? 1.0 : 0.0,bounded ? 1.0 : 0.0,1)
        : vec4(loop ? 1.0 : 0.0,rainResponse ? 1.0 : 0.0,minified ? 1.0 : 0.0,1);
}
