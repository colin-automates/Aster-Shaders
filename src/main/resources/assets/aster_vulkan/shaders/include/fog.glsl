// Front-to-back single scattering, in scene-linear radiance.
float fogSegmentTransmittance(float extinction,float distance) {
    return exp(-max(extinction,0.0)*max(distance,0.0));
}
float fogDensity(vec3 p,float skyAccess) {
    float height=p.y+CameraWorldTime.y;
    return AsterSettings.z*exp(-max(height-62.0,0.0)*.025)*skyAccess*(1.0+2.0*(1.0-AtmosphereColor.a));
}
float fogPhase(float cosine) {
    const float g=.45;
    // Henyey-Greenstein relative to isotropic scattering (4*pi absorbed
    // into the authored light radiance). Bounded for all ray directions.
    return (1.0-g*g)/pow(max(1.0+g*g-2.0*g*cosine,.001),1.5);
}
vec3 fogRay(vec2 uv,mat4 inverseProjection) {
    return cameraRay(uv,inverseProjection);
}
