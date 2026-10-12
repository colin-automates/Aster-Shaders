#ifndef ASTER_ORIGINAL_WATER_RIPPLES
#define ASTER_ORIGINAL_WATER_RIPPLES
#ifndef ASTER_WATER_RIPPLES
#define ASTER_WATER_RIPPLES
vec3 waterShortNoiseOctave(vec2 p,float scale,mat2 coordinateTransform,vec2 drift,uint seed,float weight,float footprint,vec3 envelope,float envelopeFrequency,vec2 warpOffset,mat2 warpJacobian,float warpFrequencyScale) {
    // Keep the hash's integer address separate from its small interpolation
    // coordinates. Large-world floats must not erase sub-block derivatives.
    vec2 anchor=floor(p/16.0)*16.0;
    vec2 projected=coordinateTransform*(anchor*scale);
    vec2 skewed=projected+(projected.x+projected.y)*.3660254037844386;
    ivec2 baseCell=ivec2(floor(skewed));
    vec2 remainder=fract(skewed);
    vec2 local=remainder-(remainder.x+remainder.y)*.2113248654051871
        +coordinateTransform*(((p-anchor)+warpOffset)*scale)+drift;
    vec3 field=waterRippleNoise(local,seed,baseCell);
    vec2 gradient=transpose(warpJacobian)*(transpose(coordinateTransform)*field.yz*scale);
    // The product derivative keeps this varying height field consistent with
    // its normals and refracted light. Use a constant filter-frequency allowance
    // so the Gaussian attenuation introduces no omitted spatial derivative.
    float frequency=6.28318530718*scale*warpFrequencyScale+envelopeFrequency;
    float attenuation=exp(-.5*footprint*footprint*frequency*frequency);
    return vec3(field.x*envelope.x,gradient*envelope.x+field.x*envelope.yz)*weight*attenuation;
}
vec3 waterShortNoiseOctave(vec2 p,float scale,mat2 coordinateTransform,vec2 drift,uint seed,float weight,float footprint) {
    return waterShortNoiseOctave(p,scale,coordinateTransform,drift,seed,weight,footprint,vec3(1,0,0),0.0,vec2(0),mat2(1),1.0);
}
vec3 waterRippleField(vec2 p,float t,float rain,float footprint) {
    t=waterPeriodicTime(t);
    // Normal-only waves add smaller scales without stretching sparse fluid
    // triangles. Filter each fundamental AND its sharper crest harmonic.
    if(verificationFlag(67108864)) return (waterWaveComponent(p,t,vec2(1.74,.67),.013,.8,.15,footprint,.62)
           +waterWaveComponent(p,t,vec2(3.62,.18),.009,3.7,.22,footprint,.62)
           +waterWaveComponent(p,t,vec2(5.05,3.09),.006,1.9,.18,footprint,.62)
           +waterWaveComponent(p,t,vec2(8.7,-2.34),.003,5.2,.12,footprint,.62)
           +waterWaveComponent(p,t,vec2(-6.6,9.9),.0015,.2,.10,footprint,.62))*(1.0+1.1*rain);
    vec3 a,b;waterWaveVariation(p,t,a,b);
    // Rotated continuous random height bands broaden the short-wave spectrum.
    // Their exact gradients drive surface normals and the same refracted light
    // field, avoiding an independent decorative caustic texture.
    float angle=t*(6.28318530718/1200.0);
    vec2 driftA=vec2(cos(angle*29.0),sin(angle*29.0))*5.0;
    vec2 driftB=vec2(sin(angle*41.0),cos(angle*41.0))*6.0;
    vec2 driftMidA=vec2(cos(angle*11.0),sin(angle*11.0))*3.0;
    vec2 driftMidB=vec2(sin(angle*17.0),cos(angle*17.0))*3.5;
    // Mix curved wind fronts with random lenses across several scales. Shift
    // some broad/intermediate random strength into the existing three broadest
    // directional bands so rounded cells do not dominate the whole field.
    // Stronger connected fronts share the existing averaged slope budget with
    // weaker random lenses; unresolved roughness follows the identical mix.
    // Smooth world-space patches change the relative dominance of broad and
    // intermediate lenses, instead of applying one constant spectrum everywhere.
    // Reuse the existing C2 group fields; no additional noise evaluations.
    vec3 broadEnvelope=vec3(1.0+.35*a.x,.35*a.yz);
    vec3 midEnvelope=vec3(.85+.40*b.x,.40*b.yz);
    // Deform the lens coordinates as well as their strength. The same smooth
    // group fields vary cell shape/spacing without evaluating more noise. Keep
    // the offset inside the small anchor-relative coordinates above, and apply
    // its exact chain derivative before the envelope product derivative.
    vec2 warpOffset=vec2(a.x,b.x);
    mat2 warpJacobian=mat2(1.0+a.y,b.y,a.z,1.0+b.z);
    // Broaden both principal irregular lens layers together. Matching
    // their authored unfiltered slope moments retains material strength
    // while reducing fine-cell curvature; it is not physical wave energy.
    // Broad/intermediate Gaussian frequency allowances remain 1.54/1.20.
    // These estimates do not bound the deformed coordinate Jacobian or
    // establish an exact bandlimit.
    // Never fade the deformation with camera footprint: that would move phase.
    // Non-orthogonal wind bases elongate irregular lenses in two different
    // directions. Smooth coordinate warp and variable knots retain irregular
    // spacing. The full basis transpose above carries their exact gradients;
    // matching unresolved roughness uses the weighted covariance of each basis.
    // Transfer some authored slope trace from the first three curved fronts
    // to these two existing fine random lenses. Broad/mid random lenses and
    // geometry remain; normals and Snell transport share the refined field.
    // The subset's independent-band unfiltered trace is retained, not exact
    // physical energy, all filtered covariance or pointwise slopes.
    vec3 shortNoise=waterShortNoiseOctave(p,0.75,mat2(0.6967067100,0.2725953142,-0.7173560900,0.2647485498),driftMidA,32452843u,0.0242897935,footprint,broadEnvelope,1.51,warpOffset,warpJacobian,1.54)
        +waterShortNoiseOctave(p,0.96875,mat2(0.3623577500,-0.5405826722,0.9320390900,0.2101674950),driftMidB,49979687u,0.0191189033,footprint,midEnvelope,1.51,warpOffset,warpJacobian,1.20)
        // Retain the85 elongated bases and exact transformed gradients.
        // Both fine amplitudes now include the shared subset redistribution.
        // Existing Gaussian frequency allowances filter distant detail; the
        // same covariance/amplitude pairs feed unresolved material roughness.
        +waterShortNoiseOctave(p,2.3,mat2(.9323273456,.1627269444,-.3616154320,.4195473055),driftA,75619u,.007267879086,footprint)
        +waterShortNoiseOctave(p,4.2,mat2(.4975710479,-.5204539354,.8674232256,.2985426287),driftB,105943u,.004454523303,footprint);
    return (waterWaveComponent(p,t,vec2(1.74,.67),.020200259073,.8,.15,footprint,.62,a,b)
           +waterWaveComponent(p,t,vec2(3.62,.18),.008049844719,3.7,.22,footprint,.62,a,b)
           +waterWaveComponent(p,t,vec2(5.05,3.09),.003018691770,1.9,.18,footprint,.62,a,b)
           +waterWaveComponent(p,t,vec2(8.7,-2.34),.00045,5.2,.12,footprint,.62,a,b)
           +waterWaveComponent(p,t,vec2(-6.6,9.9),.000225,.2,.10,footprint,.62,a,b)
           +shortNoise)*(1.0+1.1*rain)*WATER_RIPPLE_AMPLITUDE;
}
#endif

#endif
