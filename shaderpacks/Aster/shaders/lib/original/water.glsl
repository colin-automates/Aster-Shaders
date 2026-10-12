#ifndef ASTER_ORIGINAL_WATER
#define ASTER_ORIGINAL_WATER
#include "/lib/original/lighting.glsl"
vec3 waterAbsorption(float distance) { return exp(-vec3(.28,.10,.055)*max(distance,0.0)); }
vec3 waterAbsorption(float distance,vec3 tint) {
    if(WaterVolumeInfo.w < .5) return waterAbsorption(distance);
    vec3 hue=tint/max(max(tint.r,tint.g),max(tint.b,.001));
    // Independently calibrated clear-water return path. The submerged block
    // already owns incident extinction; retain more red/green here so equal-
    // depth materials remain distinguishable without multiplying local emitters.
    vec3 coefficient=mix(vec3(.15,.075,.035),vec3(.19,.095,.045),clamp(distance/18.0,0.0,1.0));
    coefficient*=mix(vec3(1.0),vec3(1.20)-hue*.30,.30);
    return exp(-coefficient*max(distance,0.0));
}
vec3 underwaterExtinctionCoefficient() { return vec3(.12,.065,.025); }
vec3 underwaterAbsorption(float distance) {
    // Authored underwater extinction separates depth colour from surface
    // glare. Apply it once on each actual underwater segment; constant ordered
    // coefficients retain zero-distance identity and path composition.
    return exp(-underwaterExtinctionCoefficient()*max(distance,0.0));
}
vec3 underwaterPlanarReturnDelta(vec3 receiver,float plane) {
    // Relative to the actual eye; its mirror lies at (0,2*plane,0).
    // Divide by the unnormalised vertical separation. A grazing direction
    // must retain its full length rather than moving the origin underwater.
    vec3 towardMirror=vec3(0,2.0*plane,0)-receiver;
    float depth=max(0.0,plane-receiver.y);
    return -towardMirror*(depth/max(towardMirror.y,.000001));
}
vec3 underwaterPlanarReturnTransmission(vec3 delta) {
    return underwaterAbsorption(length(delta));
}
vec3 underwaterBodyRadiance() {
    // Directional daylight fills the volume; absorption keeps red wavelengths
    // from surviving longer than blue. Night retains the same bounded optics.
    return vec3(.017,.053,.125)*(.18+.82*SunDirection.w);
}
vec3 waterShadingNormal(vec3 position) {
    if(verificationFlag(536870912)) return position.y < 0.0 ? vec3(0,1,0) : vec3(0,-1,0);
    vec2 p=position.xz+CameraWorldTime.xz;
    float e=.10;
    vec3 n=normalize(vec3(waterWave(p-vec2(e,0))-waterWave(p+vec2(e,0)),.8,waterWave(p-vec2(0,e))-waterWave(p+vec2(0,e))));
    return dot(n,-position) < 0.0 ? -n : n;
}
vec3 waterBodyRadiance(vec3 tint,float skyAccess) {
    // Biome tint affects scattering rather than painting the surface texture.
    vec3 hue=tint/max(max(tint.r,tint.g),max(tint.b,.001));
    vec3 body=WaterVolumeInfo.w < .5 ? mix(vec3(.016,.065,.075),hue*.07,.20)
        : mix(vec3(.017,.065,.120),hue*.07,.18);
    return body*(.22+.78*SunDirection.w)*mix(.18,1.0,skyAccess);
}
#include "/lib/original/water_ripples.glsl"
vec3 waterShadingNormal(vec3 position,vec3 geometry,float footprint,float shoreWeight) {
    if(verificationFlag(536870912)) return position.y < 0.0 ? vec3(0,1,0) : vec3(0,-1,0);
    if(verificationFlag(512)) return waterShadingNormal(position);
    if(abs(geometry.y) < .45) return geometry;
    vec2 p=position.xz+CameraWorldTime.xz;
    float t=CameraWorldTime.w,rain=1.0-AtmosphereColor.a;
    vec2 slope=waterWaveGradient(p)*AsterSettings.y;
    // At anchored shores the actual triangle includes the amplitude taper.
    // Its macro slope keeps the replay and visible surface attached to banks.
    slope=mix(-geometry.xz/geometry.y,slope,shoreWeight);
    // Short ripples affect normals only. Attenuate frequencies before they
    // become subpixel, independently of the block-grid geometric waves.
    if(verificationFlag(2048)) {
        slope+=vec2(5.1,2.4)*cos(dot(p,vec2(5.1,2.4))+t*2.3)*.007*exp(-footprint*5.6);
        slope+=vec2(-3.7,6.8)*cos(dot(p,vec2(-3.7,6.8))-t*2.8)*.004*exp(-footprint*7.7);
        slope+=vec2(10.4,-4.1)*cos(dot(p,vec2(10.4,-4.1))+t*3.7)*(.0015+.003*rain)*exp(-footprint*11.2);
    } else slope+=waterRippleField(p,t,rain,footprint).yz*smoothstep(0.0,.65,shoreWeight);
    vec3 n=normalize(vec3(-slope.x,1,-slope.y));
    return dot(n,-position) < 0.0 ? -n : n;
}
float waterDielectricFresnel(float cosine,bool submerged) {
    float incident=submerged ? 1.333 : 1.0,transmitted=submerged ? 1.0 : 1.333;
    float sinSquared=incident*incident/(transmitted*transmitted)*(1.0-cosine*cosine);
    if(sinSquared >= 1.0) return 1.0;
    float other=sqrt(max(0.0,1.0-sinSquared));
    float s=(incident*cosine-transmitted*other)/max(incident*cosine+transmitted*other,.0001);
    float p=(transmitted*cosine-incident*other)/max(transmitted*cosine+incident*other,.0001);
    return .5*(s*s+p*p);
}
#include "/lib/original/sky.glsl"
vec3 waterSky(vec3 direction) {
    if(SceneInfo.w > .5 && !verificationFlag(16384)) return asterSkyRadiance(direction);
    vec3 sky=displayToLinear(AtmosphereColor.rgb);
    if(!verificationFlag(512)) {
        if(SceneInfo.z > .5) sky=vec3(.36,.55,.83)*(.15+.85*SunDirection.w);
        sky=mix(sky,sky*vec3(.65,.83,1.08),smoothstep(.05,.8,direction.y));
        if(SceneInfo.w > .5) sky=cinematicAtmosphere(sky,direction);
        return sky*mix(.65,1.18,smoothstep(-.02,.7,direction.y));
    }
    return SceneInfo.w > .5 ? cinematicAtmosphere(sky,direction)*mix(.55,1.15,max(direction.y,0.0)) : sky;
}

#endif
