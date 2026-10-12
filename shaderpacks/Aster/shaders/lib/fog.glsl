#ifndef ASTER_IRIS_FOG
#define ASTER_IRIS_FOG
#include "/lib/shadow.glsl"
vec3 atmosphericFog(vec3 color,vec3 p) {
    float distance=length(p);
    vec3 direction=p/max(distance,.0001);
    if(SceneInfo.w<.5) {
        float optical=1.0-exp(-distance/(far*.40));
        return mix(color,displayToLinear(fogColor),optical);
    }
    if(isEyeInWater==1) {
        float path=min(distance,48.0);
        vec3 transmission=underwaterAbsorption(path);
        float forward=pow(max(dot(direction,SunDirection.xyz),0.0),4.0);
        vec3 scattering=underwaterBodyRadiance()*(1.0+forward*.8);
        return color*transmission+scattering*(1.0-transmission);
    }
    float transmittance=1.0;vec3 scattering=vec3(0);
    float stepSize=min(distance,far*1.25)/float(FOG_STEPS);
    for(int i=0;i<FOG_STEPS;i++) {
        vec3 samplePos=direction*(float(i)+.5)*stepSize;
        vec3 world=samplePos+cameraPosition;
        float twilight=1.0-smoothstep(.04,.35,abs(SkySun.y));
        float low=exp(-max(world.y-62.0,0.0)/6.0);
        float drift=skyNoise(world*.035+vec3(frameTimeCounter*.004,0,0));
        float density=.00085*exp(-max(world.y-62.0,0.0)*.012)*(1.0+rainStrength*1.5)+.0018*low*(.18+.65*twilight)*(.25+drift*1.5);
        float leaves;vec3 shadow=shadowVisibility(samplePos,SunDirection.xyz,leaves);
        float phase=.08+.22*pow(max(dot(direction,SunDirection.xyz),0.0),6.0);
        vec3 ambient=fogAmbientRadiance(skyAtmosphere(direction),direction);
        vec3 source=ambient+terrainSunRadiance()*SunDirection.w*shadow*phase*.14;
        float absorbed=1.0-exp(-density*stepSize);
        scattering+=transmittance*source*absorbed;transmittance*=1.0-absorbed;
    }
    float distant=1.0-exp(-pow(distance/max(far*.90,32.0),4.0));
    return mix(color*transmittance+scattering,skyAtmosphere(direction),distant);
}
#endif
