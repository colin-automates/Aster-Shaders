// Shared material roughness and reconstruction kernels. Radiance may be
// interpolated, but coverage/depth/normal validation always uses actual texels.
float waterUnresolvedBand(float footprint,float frequency,float filterFrequency,float amplitude,float crest) {
    float support=footprint*footprint*filterFrequency*filterFrequency;
    // Height-band derivative energy, including the sharper second harmonic.
    // 0.42 estimates the squared smooth group envelope over the random field.
    float energy=.21*amplitude*amplitude*frequency*frequency/((1.0+crest)*(1.0+crest));
    return energy*((1.0-exp(-support))+4.0*crest*crest*(1.0-exp(-4.0*support)));
}
float waterUnresolvedNoise(float footprint,float scale,float amplitude,float envelopeMeanSquare,float envelopeGradientEnergy,float envelopeFrequency,float warpFrequencyScale) {
    float frequency=6.28318530718*scale*warpFrequencyScale+envelopeFrequency;
    // The radial simplex basis is normalized to the previous quintic noise's
    // integrated two-axis gradient energy, retaining the unresolved slope budget.
    // Include the mean energy of both terms in the product derivative. Moments
    // assume independently hashed bands, as the existing spectrum estimator does;
    // they are an averaged material model, not a pointwise bound. For the
    // deformed bands envelopeMeanSquare also includes the Jacobian-weighted
    // gradient energy; the frequency allowance matches the resolved field.
    float energy=1.49248*scale*scale*envelopeMeanSquare+.0217653333*envelopeGradientEnergy;
    return energy*amplitude*amplitude*(1.0-exp(-footprint*footprint*frequency*frequency));
}
float waterUnresolvedNoise(float footprint,float scale,float amplitude,float envelopeMeanSquare,float envelopeGradientEnergy,float envelopeFrequency) {
    return waterUnresolvedNoise(footprint,scale,amplitude,envelopeMeanSquare,envelopeGradientEnergy,envelopeFrequency,1.0);
}
float waterUnresolvedNoise(float footprint,float scale,float amplitude) {
    return waterUnresolvedNoise(footprint,scale,amplitude,1.0,0.0,0.0);
}
float waterSurfaceRoughness(float footprint) {
    float rain=clamp(1.0-AtmosphereColor.a,0.0,1.0);
    float base=.18+.12*rain;
    if(verificationFlag(512) || verificationFlag(67108864)) return clamp(base+footprint*.035,.18,.4);
    float variance=waterUnresolvedBand(footprint,1.864,3.374,.020200259073,.15)
        +waterUnresolvedBand(footprint,3.624,5.134,.008049844719,.22)
        +waterUnresolvedBand(footprint,5.920,7.430,.003018691770,.18)
        +waterUnresolvedBand(footprint,9.009,10.519,.00045,.12)
        +waterUnresolvedBand(footprint,11.898,13.408,.000225,.10)
        +waterUnresolvedNoise(footprint,0.75,0.0242897935,0.5827789162,.000325883195,1.51,1.54)
        +waterUnresolvedNoise(footprint,0.96875,0.0191189033,0.4979613479,.000812018011,1.51,1.20)
        // Match the fine bands' non-orthogonal basis covariance, including
        // their compensating amplitude. Its longest axis remains unit length.
        +waterUnresolvedNoise(footprint,2.3,.007267879086,.60125,0.0,0.0)
        +waterUnresolvedNoise(footprint,4.2,.004454523303,.68,0.0,0.0);
    float weather=(1.0+1.1*rain)*WATER_RIPPLE_AMPLITUDE;
    variance*=weather*weather;
    // GGX alpha is perceptual roughness squared. Unresolved normal variance
    // broadens alpha squared instead of erasing the ripple scattering energy.
    return clamp(pow(base*base*base*base+variance,.25),.18,.4);
}
float waterCubicWeight(float distance) {
    float x=abs(distance);
    if(x < 1.0) return (1.5*x-2.5)*x*x+1.0;
    if(x < 2.0) return ((-.5*x+2.5)*x-4.0)*x+2.0;
    return 0.0;
}
bool waterHistoryCompatible(vec3 oldPosition,vec4 oldSurface,vec3 position,vec3 normal) {
    return oldSurface.a > .5
        && abs(length(oldPosition)-length(position)) < .12+length(position)*.008
        && abs(oldPosition.y-position.y) < .12
        && dot(normalize(oldSurface.xyz),normal) > .97;
}
