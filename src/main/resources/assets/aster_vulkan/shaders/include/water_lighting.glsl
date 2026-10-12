#ifndef ASTER_WATER_LIGHTING_GLSL
#define ASTER_WATER_LIGHTING_GLSL
// Incident transfer is owned by each light contribution before composition.
// Camera/return-path extinction remains in the water material and composite.
struct WaterLightTransport {
    vec3 direction;
    vec3 interfaceNormal;
    vec3 incidentTransmission;
    vec3 skyTransmission;
    float focusGain;
    float wetWeight;
    float depth;
};
WaterLightTransport waterDryLight() {
    WaterLightTransport light;
    light.direction=normalize(SunDirection.xyz);
    light.interfaceNormal=vec3(0,1,0);
    light.incidentTransmission=vec3(1);
    light.skyTransmission=vec3(1);
    light.focusGain=1.0;
    light.wetWeight=0.0;
    light.depth=0.0;
    return light;
}
WaterLightTransport waterLightAt(vec3 p,vec3 geometry,float footprint) {
    WaterLightTransport light=waterDryLight();
    if(AsterSettings.x < .5 || WaterVolumeInfo.w < .5 || SceneInfo.w < .5 || verificationFlag(512)) return light;
    vec3 world=p+CameraWorldTime.xyz;
    float surfaceY;
    light.depth=receiverWaterDepth(world,geometry,surfaceY);
    light.wetWeight=smoothstep(.06,.4,light.depth);
    if(light.wetWeight < .001) return light;
    vec3 airDirection=light.direction;
    vec3 planeRay=refract(-airDirection,vec3(0,1,0),1.0/1.333);
    vec2 entry=world.xz-planeRay.xz*light.depth/max(-planeRay.y,.2);
    vec2 slope=causticWaveSlope(entry,CameraWorldTime.w,max(.012,footprint*1.2));
    light.interfaceNormal=normalize(vec3(-slope.x,1,-slope.y));
    vec3 ray=refract(-airDirection,light.interfaceNormal,1.0/1.333);
    light.direction=normalize(mix(airDirection,-ray,light.wetWeight));
    float incidentPath=light.depth/max(-ray.y,.35);
    light.incidentTransmission=mix(vec3(1),waterIncidentTransmission(incidentPath),light.wetWeight);
    // One effective diffuse-sky path. 1.204 approximates the cosine-weighted
    // mean secant after air-to-water refraction at IOR1.333. Beer on that mean
    // is an approximation, not an angular integral or measured calibration.
    light.skyTransmission=mix(vec3(1),waterIncidentTransmission(light.depth*1.204),light.wetWeight);
    float coverage=max(waterReceiverCoverage(world.xz),farWaterReceiverCoverage(world.xz));
    float mask=causticVisibility(light.depth,footprint,SkySun.y,AtmosphereColor.a,1.0,geometry.y)*coverage;
    if(mask < .001) return light;
    // Retain the existing first-order entry inversion and original gates.
    // This is a bounded shared-field model, not exact curved-interface tracing.
    entry-=((ray.xz/max(-ray.y,.2))-(planeRay.xz/max(-planeRay.y,.2)))*min(light.depth,5.0);
    vec3 surface=vec3(entry.x,surfaceY,entry.y)-CameraWorldTime.xyz;
    vec3 sunlight=normalize(SkySun.xyz);
    float visibility=surfaceShadowAt(surface+sunlight*.035,.0003)*surfaceShadowAt(p+geometry*.035,.0003);
    vec2 cloudUV=vec2(atan(sunlight.z,sunlight.x)/(2.0*SKY_PI)+.5,.5-asin(clamp(sunlight.y,-1.0,1.0))/SKY_PI);
    visibility*=texture(SkyClouds,cloudUV).a;
    float focus=causticReceiverFocus(world.xz,light.depth,ray.xz/max(-ray.y,.2),footprint);
    float deepResponse=smoothstep(1.5,4.0,light.depth);
    float contrastGain=mix(2.3,1.6,smoothstep(.7,4.0,light.depth))+deepResponse;
    float focused=max(focus,0.0)*contrastGain;
    float knee=mix(.5,.4,deepResponse),darkGain=mix(.65,.85,deepResponse);
    float contrast=clamp(focused/(1.0+knee*focused)+min(focus,0.0)*darkGain,-.85,3.0);
    light.focusGain=1.0+contrast*mask*visibility;
    return light;
}
vec3 waterDirectRadiance(vec3 direct,WaterLightTransport light) {
    return direct*light.incidentTransmission*light.focusGain;
}
#endif
