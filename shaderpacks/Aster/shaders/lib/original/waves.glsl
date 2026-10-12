#ifndef ASTER_WAVES
#define ASTER_WAVES
#include "/lib/original/water_variation.glsl"

// Wind-driven height waves stay on the native fluid XZ grid. Horizontal
// Gerstner displacement would open gaps at block banks without a shore mask.
// Quantized temporal frequencies close the native 1200-second clock loop.
float waterFrequency(float k,float speed) {
    const float cycle=6.28318530718/1200.0;
    return floor(sqrt(9.81*k)*speed/cycle+.5)*cycle;
}
float waterPeriodicTime(float seconds) {
    // Reduce before trigonometry. A common 100 Hz phase avoids cancellation
    // after a 1200-second wrap, amplified by high-curvature focusing. Geometry,
    // normal detail and receiver light use this same clock representation.
    return mod(floor(seconds*100.0+.5),120000.0)*.01;
}
float waterSpatialPhase(vec2 p,vec2 k) {
    // Keep sub-block wave detail out of a large world-coordinate product.
    // The bounded anchor phase is common to the whole local patch, while
    // the small remainder preserves the analytic spatial derivative.
    vec2 anchor=floor(p/16.0)*16.0;
    // Reduce the per-cell advance before the large integer-cell product.
    // Adjacent patches then agree without subtracting two huge wave phases.
    vec2 cellPhase=mod(k*16.0,vec2(6.28318530718));
    return mod(dot(anchor/16.0,cellPhase),6.28318530718)+dot(p-anchor,k);
}
vec3 waterWaveComponent(vec2 p,float t,vec2 k,float weight,float phase,float crest,float footprint,float speed) {
    float frequency=length(k);
    float theta=waterSpatialPhase(p,k)-mod(t*waterFrequency(frequency,speed),6.28318530718)+phase;
    // Slow wave groups break continuous parallel bands. Include their spatial
    // derivative so replay, lighting and displaced geometry describe one field.
    vec2 group=vec2(-.083,.127);
    float groupPhase=dot(p,group)-t*(6.28318530718/1200.0)*17.0+phase*2.7;
    float groupSine=sin(groupPhase),groupCosine=cos(groupPhase);
    float envelope=.82+.18*groupSine;
    vec2 envelopeGradient=.18*groupCosine*group;
    // Bend fronts smoothly across the wind instead of keeping long ruler-
    // straight bands. Derive the bent phase and filter its upper frequency.
    theta+=.65*groupSine;
    vec2 phaseGradient=k+.65*groupCosine*group;
    frequency+=.65*length(group);
    float attenuation=exp(-.5*footprint*footprint*frequency*frequency);
    float harmonicFilter=attenuation*attenuation*attenuation*attenuation;
    float c=cos(theta),s=sin(theta);
    float shape=c*attenuation+crest*(2.0*c*c-1.0)*harmonicFilter;
    float derivative=-s*attenuation-4.0*crest*s*c*harmonicFilter;
    return vec3(envelope*shape,envelope*derivative*phaseGradient+shape*envelopeGradient)*weight/(1.0+crest);
}
vec3 waterWaveComponent(vec2 p,float t,vec2 k,float weight,float phase,float crest,float footprint,float speed,vec3 groupA,vec3 groupB,float variationFrequencyBound) {
    // Distinct mixtures give each wave its own patches and curved fronts.
    // Both derivatives are carried into every geometry and shading pass.
    float blend=.5+.5*sin(phase*1.731);
    vec3 group=mix(groupA,groupB,blend);
    vec2 bendWeights=vec2(cos(phase*.93),sin(phase*.93))*.70710678;
    vec3 bend=groupA*bendWeights.x+groupB*bendWeights.y;
    float g=clamp((group.x+.55)/1.1,0.0,1.0);
    float envelope=.38+.62*g*g*(3.0-2.0*g);
    vec2 envelopeGradient=(.62*6.0*g*(1.0-g)/1.1)*group.yz;
    float theta=waterSpatialPhase(p,k)-mod(t*waterFrequency(length(k),speed),6.28318530718)+phase+2.8*bend.x;
    vec2 phaseGradient=k+2.8*bend.yz;
    // Conservative constant frequency bound keeps filtering independent of
    // position, so its analytic gradient remains consistent with the height.
    float frequency=length(k)+variationFrequencyBound;
    float attenuation=exp(-.5*footprint*footprint*frequency*frequency);
    float harmonicFilter=attenuation*attenuation*attenuation*attenuation;
    float c=cos(theta),s=sin(theta);
    float shape=c*attenuation+crest*(2.0*c*c-1.0)*harmonicFilter;
    float derivative=-s*attenuation-4.0*crest*s*c*harmonicFilter;
    return vec3(envelope*shape,envelope*derivative*phaseGradient+shape*envelopeGradient)*weight/(1.0+crest);
}
vec3 waterWaveComponent(vec2 p,float t,vec2 k,float weight,float phase,float crest,float footprint,float speed,vec3 groupA,vec3 groupB) {
    return waterWaveComponent(p,t,k,weight,phase,crest,footprint,speed,groupA,groupB,1.51);
}
vec3 waterWaveField(vec2 p,float t,float rain) {
    t=waterPeriodicTime(t);
    // Large waves span roughly 4.4 to 14.4 blocks in a common wind sector; speeds
    // follow sqrt(g*k), with a common artistic time scale for calm water.
    if(verificationFlag(67108864)) return (waterWaveComponent(p,t,vec2(.454,.163),.44,.4,.18,0.0,.55)
           +waterWaveComponent(p,t,vec2(.751,.083),.29,2.1,.22,0.0,.55)
           +waterWaveComponent(p,t,vec2(.883,.536),.17,4.5,.16,0.0,.55)
           +waterWaveComponent(p,t,vec2(1.415,-.218),.10,1.3,.20,0.0,.55))*(1.0+.4*rain);
    vec3 a,b;waterWaveVariation(p,t,a,b);
    return (waterWaveComponent(p,t,vec2(.454,.163),.28,.4,.18,0.0,.55,a,b)
           +waterWaveComponent(p,t,vec2(.342,.271),.19,3.35,.15,0.0,.55,a,b)
           +waterWaveComponent(p,t,vec2(.751,.083),.21,2.1,.22,0.0,.55,a,b)
           +waterWaveComponent(p,t,vec2(.583,.433),.14,5.7,.18,0.0,.55,a,b)
           +waterWaveComponent(p,t,vec2(.883,.536),.11,4.5,.16,0.0,.55,a,b)
           +waterWaveComponent(p,t,vec2(1.415,-.218),.07,1.3,.20,0.0,.55,a,b))*(1.0+.4*rain);
}
float waterWave(vec2 p) {
    if(verificationFlag(1073741824)) return 0.0;
    float t = CameraWorldTime.w;
    if(!verificationFlag(512) && !verificationFlag(2048)) return waterWaveField(p,t,1.0-AtmosphereColor.a).x;
    if(!verificationFlag(512)) {
        float wind=1.0+.45*(1.0-AtmosphereColor.a);
        return (sin(dot(p,vec2(.44,.20))+t*.95)*.55
              + sin(dot(p,vec2(-.23,.61))-t*1.18)*.30
              + sin(dot(p,vec2(.94,-.37))+t*1.46)*.15)*wind;
    }
    return sin(dot(p, vec2(0.72, 0.41)) + t * 1.3) * 0.55
         + sin(dot(p, vec2(-0.34, 1.16)) - t * 1.7) * 0.30
         + sin(dot(p, vec2(1.72, -0.63)) + t * 2.2) * 0.15;
}
vec2 waterWaveGradient(vec2 p) {
    float t=CameraWorldTime.w;
    if(!verificationFlag(512) && !verificationFlag(2048)) return waterWaveField(p,t,1.0-AtmosphereColor.a).yz;
    return (vec2(.44,.20)*cos(dot(p,vec2(.44,.20))+t*.95)*.55
           +vec2(-.23,.61)*cos(dot(p,vec2(-.23,.61))-t*1.18)*.30
           +vec2(.94,-.37)*cos(dot(p,vec2(.94,-.37))+t*1.46)*.15)*(1.0+.45*(1.0-AtmosphereColor.a));
}

#endif
