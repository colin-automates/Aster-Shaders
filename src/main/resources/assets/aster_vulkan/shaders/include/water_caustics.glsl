#ifndef ASTER_WATER_CAUSTICS
#define ASTER_WATER_CAUSTICS
uniform sampler2D WaterVolume;
uniform sampler2D WaterCaustics;
uniform sampler2D WaterCausticsFar;
uniform sampler2D WaterCausticsShallow;
uniform sampler2D WaterCausticsShallowFar;
vec2 causticBeamOffset(vec2 world,float depth,float footprint);
vec4 causticPixelDensity(sampler2D density,vec2 uv,float footprint,float extent) {
    // The stored density already integrates a map texel. Add only the missing
    // receiver-pixel variance instead of fading every spatial frequency equally.
    // Positive centre/cross weights sum to one; each axis has variance support/12.
    float texel=extent/float(textureSize(density,0).x);
    float support=max(footprint*footprint-texel*texel,0.0);
    vec4 center=textureLod(density,uv,0.0);
    if(support<=1e-8) return center;
    vec2 offset=vec2(.5*sqrt(support)/extent,0.0);
    return center/3.0+(textureLod(density,uv+offset,0.0)
        +textureLod(density,uv-offset,0.0)
        +textureLod(density,uv+offset.yx,0.0)
        +textureLod(density,uv-offset.yx,0.0))/6.0;
}
float waterReceiverCoverage(vec2 world) {
    vec2 local=world-WaterVolumeInfo.xz;
    vec2 border=min(local,vec2(64.0)-local);
    return smoothstep(2.0,6.0,min(border.x,border.y));
}
float farWaterReceiverCoverage(vec2 world) {
    vec2 local=world-WaterVolumeInfo.xz+vec2(96);
    vec2 border=min(local,vec2(256.0)-local);
    return smoothstep(4.0,12.0,min(border.x,border.y));
}
float causticReceiverFocus(vec2 world,float depth,vec2 beamOffset,float footprint) {
    // Reconstruct along the same refracted beam across depth planes. Blending
    // different ray phases at one horizontal coordinate erased fine contrast.
    vec4 planes=vec4(.5,1.5,3.5,5.5);
    int lower=depth<1.5 ? 0 : depth<3.5 ? 1 : 2;
    float low=planes[lower],high=planes[lower+1];
    float blend=clamp((depth-low)/(high-low),0.0,1.0);
    vec2 nearWorld=world+beamOffset*(low-depth);
    vec2 farWorld=world+beamOffset*(high-depth);
    float nearWeight=waterReceiverCoverage(world),nearValue=1.0;
    if(nearWeight>0.0) {
        vec4 nearDensity=causticPixelDensity(WaterCaustics,(nearWorld-WaterVolumeInfo.xz)/64.0,footprint,64.0);
        vec4 upperDensity=causticPixelDensity(WaterCaustics,(farWorld-WaterVolumeInfo.xz)/64.0,footprint,64.0);
        nearValue=mix(nearDensity[lower],upperDensity[lower+1],blend);
        if(depth<1.5) {
            float middle=causticPixelDensity(WaterCausticsShallow,
                (world+beamOffset*(.875-depth)-WaterVolumeInfo.xz)/64.0,footprint,64.0).r;
            nearValue=depth<.875 ? mix(nearDensity.r,middle,clamp((depth-.5)/.375,0.0,1.0))
                : mix(middle,upperDensity.g,clamp((depth-.875)/.625,0.0,1.0));
        }
    }
    if(nearWeight>=.99999) return clamp(nearValue-1.0,-.65,2.5);
    // The wider level uses the same wave field, filtered for its source grid.
    // Follow that filtered beam when interpolating its receiver-depth planes.
    vec2 farBeam=causticBeamOffset(world,depth,.0625/1.2);
    vec2 origin=WaterVolumeInfo.xz-vec2(96);
    vec4 farLow=causticPixelDensity(WaterCausticsFar,(world+farBeam*(low-depth)-origin)/256.0,footprint,256.0);
    vec4 farHigh=causticPixelDensity(WaterCausticsFar,(world+farBeam*(high-depth)-origin)/256.0,footprint,256.0);
    float farValue=mix(farLow[lower],farHigh[lower+1],blend);
    if(depth<1.5) {
        float middle=causticPixelDensity(WaterCausticsShallowFar,
            (world+farBeam*(.875-depth)-origin)/256.0,footprint,256.0).r;
        farValue=depth<.875 ? mix(farLow.r,middle,clamp((depth-.5)/.375,0.0,1.0))
            : mix(middle,farHigh.g,clamp((depth-.875)/.625,0.0,1.0));
    }
    float value=mix(farValue,nearValue,nearWeight);
    return clamp(value-1.0,-.65,2.5);
}
float causticReceiverFocus(vec2 world,float depth) {
    return causticReceiverFocus(world,depth,causticBeamOffset(world,depth,.035),.035);
}
float waterVoxel(ivec3 p) {
    if(any(lessThan(p,ivec3(0))) || any(greaterThanEqual(p,ivec3(64,16,64)))) return 0.0;
    return texelFetch(WaterVolume,ivec2(p.x+p.y*64,p.z),0).a;
}
float farReceiverWaterDepth(vec3 world,vec3 normal,out float surfaceY) {
    surfaceY=world.y;
    vec3 wet=world+normal*.045;
    ivec2 cell=ivec2(floor((wet.xz-WaterVolumeInfo.xz+vec2(96))/4.0));
    if(any(lessThan(cell,ivec2(0))) || any(greaterThanEqual(cell,ivec2(64)))) return 0.0;
    vec3 packed=round(texelFetch(WaterVolume,ivec2(960+cell.x,cell.y),0).rgb*255.0);
    if(packed.r<.5) return 0.0;
    float surface=(packed.g*256.0+packed.b)/128.0-64.0;
    // The scan records the bottom wet cell, which already bounds the wet band.
    float floorY=floor(surface)-(packed.r-1.0);
    if(wet.y<floorY || wet.y>surface+.004) return 0.0;
    surfaceY=surface;
    return max(surface-world.y,0.0);
}
// Resolve the actual water immediately outside the receiver, then walk that
// connected column to its native surface. Dry banks and stacked pools differ.
float receiverWaterDepth(vec3 world,vec3 normal,out float surfaceY) {
    surfaceY=world.y;
    vec3 wet=world+normal*.045;
    ivec3 cell=ivec3(floor(wet)-WaterVolumeInfo.xyz);
    if(any(lessThan(cell.xz,ivec2(0))) || any(greaterThanEqual(cell.xz,ivec2(64))))
        return farReceiverWaterDepth(world,normal,surfaceY);
    float height=waterVoxel(cell);
    if(height <= 0.0 || wet.y > floor(wet.y)+(height*255.0-1.0)/254.0+.002) return 0.0;
    for(int i=0;i<8;i++) {
        float above=waterVoxel(cell+ivec3(0,1,0));
        if(above <= 0.0) {
            if(cell.y >= 15) return 0.0; // cache boundary is not a water surface
            surfaceY=WaterVolumeInfo.y+float(cell.y)+(height*255.0-1.0)/254.0;
            return max(surfaceY-world.y,0.0);
        }
        cell.y++;height=above;
    }
    return 0.0;
}
vec2 causticWaveSlope(vec2 p,float t,float footprint) {
    // Match the cached source, geometry and visible normal's weather strength.
    float rain=1.0-AtmosphereColor.a;
    return waterWaveField(p,t,rain).yz*AsterSettings.y+waterRippleField(p,t,rain,footprint).yz;
}
vec2 causticBeamOffset(vec2 world,float depth,float footprint) {
    vec3 incident=-normalize(SkySun.xyz);
    vec3 flatRay=refract(incident,vec3(0,1,0),1.0/1.333);
    vec2 entry=world-flatRay.xz/max(-flatRay.y,.2)*depth;
    vec2 slope=causticWaveSlope(entry,CameraWorldTime.w,max(.012,footprint*1.2));
    vec3 ray=refract(incident,normalize(vec3(-slope.x,1,-slope.y)),1.0/1.333);
    return ray.xz/max(-ray.y,.2);
}
float causticFocus(vec2 p,float t,float depth,float footprint) {
    t=waterPeriodicTime(t);
    // Differentiate Snell's actual refracted ray map, including oblique sun.
    // Finite pixel support and a density cap bound focusing near fold caustics.
    // The previous fixed 4 cm support erased resolved short waves in the
    // shallows. Filter by receiver pixel size, and differentiate at a binary
    // representable step small enough for the shortest retained wavelength.
    float e=.0078125;
    float waveFootprint=max(.012,footprint*1.2);
    vec2 center=causticWaveSlope(p,t,waveFootprint);
    vec2 sx=causticWaveSlope(p+vec2(e,0),t,waveFootprint);
    vec2 sz=causticWaveSlope(p+vec2(0,e),t,waveFootprint);
    vec3 incident=-normalize(SkySun.xyz);
    vec3 ray=refract(incident,normalize(vec3(-center.x,1,-center.y)),1.0/1.333);
    vec3 rayX=refract(incident,normalize(vec3(-sx.x,1,-sx.y)),1.0/1.333);
    vec3 rayZ=refract(incident,normalize(vec3(-sz.x,1,-sz.y)),1.0/1.333);
    vec2 offset=ray.xz/max(-ray.y,.2);
    vec2 dx=(rayX.xz/max(-rayX.y,.2)-offset)/e;
    vec2 dz=(rayZ.xz/max(-rayZ.y,.2)-offset)/e;
    float travel=min(depth,5.0);
    float determinant=(1.0+travel*dx.x)*(1.0+travel*dz.y)-travel*travel*dx.y*dz.x;
    return clamp(1.0/max(abs(determinant),.24)-1.0,-.65,2.5);
}
float causticVisibility(float depth,float footprint,float elevation,float clear,float shadow,float upward) {
    // Retain contrast through the covered deep layer before the unsupported
    // six-block boundary. Incident and return-path absorption already dim it.
    return smoothstep(.06,.4,depth)*(1.0-smoothstep(4.5,6.0,depth))
        // Keep focused-light definition deeper in the covered water column.
        // Incident and view paths already have their own spectral extinction;
        // this is a gentler authored visibility fade, with the six-block,
        // solar, weather, cloud and actual-wet-receiver gates retained.
        *exp(-depth*.04)
        *smoothstep(.04,.35,elevation)*clear*clear*clear*shadow*max(upward,0.0);
}
vec3 waterIncidentTransmission(float distance) {
    // Authored wavelength-dependent loss on the actual incident water path.
    // Red attenuates fastest, with gentler green/blue loss to retain a cool
    // water material over sand. Constant ordered coefficients keep identity,
    // monotonicity and path composition; these are not measured calibration.
    return exp(-vec3(.26,.125,.055)*max(distance,0.0));
}
vec3 waterCausticReceiver(vec3 color,vec3 p,vec3 normal,float footprint) {
    if(WaterVolumeInfo.w < .5 || SceneInfo.w < .5) return color;
    vec3 world=p+CameraWorldTime.xyz;
    float surfaceY;
    float depth=receiverWaterDepth(world,normal,surfaceY);
    float coverage=max(waterReceiverCoverage(world.xz),farWaterReceiverCoverage(world.xz));
    // Incident-light absorption belongs to every wet receiver orientation.
    // Upward-facing geometry gates the bed caustic, not water's colour loss.
    float wetReceiver=smoothstep(.06,.4,depth);
    if(wetReceiver < .001) return color;
    vec3 sunlight=normalize(SkySun.xyz);
    vec3 ray=refract(-sunlight,vec3(0,1,0),1.0/1.333);
    // Incident sky/sun light crosses water before reaching the bed, as well
    // as on the return path to the eye. Previously only the view path lost
    // red wavelengths, leaving close shallow sand looking dry and yellow.
    float incidentPath=depth/max(abs(ray.y),.35);
    // Colour loss follows verified wet geometry rather than the smaller
    // caustic-density atlas. Its border must not turn distant water dry/yellow.
    color*=mix(vec3(1.0),waterIncidentTransmission(incidentPath),wetReceiver);
    float mask=causticVisibility(depth,footprint,SkySun.y,AtmosphereColor.a,1.0,normal.y)*coverage;
    if(mask < .001) return color;
    vec2 entry=world.xz-ray.xz*depth/max(-ray.y,.2);
    // Invert the first-order surface-to-bottom ray map once. Density and phase
    // now refer to the refracting patch that reaches this receiver.
    vec2 slope=causticWaveSlope(entry,CameraWorldTime.w,max(.012,footprint*1.2));
    vec3 bent=refract(-sunlight,normalize(vec3(-slope.x,1,-slope.y)),1.0/1.333);
    entry-=((bent.xz/max(-bent.y,.2))-(ray.xz/max(-ray.y,.2)))*min(depth,5.0);
    vec3 surface=vec3(entry.x,surfaceY,entry.y)-CameraWorldTime.xyz;
    float visibility=surfaceShadowAt(surface+sunlight*.035,.0003)*surfaceShadowAt(p+normal*.035,.0003);
    vec2 cloudUV=vec2(atan(sunlight.z,sunlight.x)/(2.0*SKY_PI)+.5,.5-asin(clamp(sunlight.y,-1.0,1.0))/SKY_PI);
    // Reuse the native sky cloud atlas's solar transmittance; one filtered fetch.
    visibility*=texture(SkyClouds,cloudUV).a;
    float focus=causticReceiverFocus(world.xz,depth,bent.xz/max(-bent.y,.2),footprint);
    // Keep the shared refracted-light field, but give it a softer authored
    // radiance response. A positive highlight knee and gentler dark lobe
    // retain visible focusing without the broad blotchy contrast of 69.
    // Raw cached flux is unchanged; this is not energy-exact light transport.
    // Original wet/depth/solar/cloud/shadow gates and outer safety bounds remain.
    // Broader lenses need definition at depth after the retained visibility
    // and incident/view losses. Keep the original shallow response through
    // 1.5 blocks. This authored contrast curve does not conserve radiance;
    // raw transport density and all wet/solar/shadow/coverage gates remain.
    float deepResponse=smoothstep(1.5,4.0,depth);
    float contrastGain=mix(2.3,1.6,smoothstep(.7,4.0,depth))+deepResponse;
    float focused=max(focus,0.0)*contrastGain;
    float knee=mix(.5,.4,deepResponse),darkGain=mix(.65,.85,deepResponse);
    float contrast=clamp(focused/(1.0+knee*focused)+min(focus,0.0)*darkGain,-.85,3.0);
    return color*(1.0+contrast*mask*visibility);
}
#endif
