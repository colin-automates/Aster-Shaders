#ifndef ASTER_WATER_VOLUME_SCATTERING
#define ASTER_WATER_VOLUME_SCATTERING
// Include after water_caustics.glsl. Reuse its accumulated multi-patch density
// and the existing cached source fields; no wave reevaluation or new atlas.
uniform sampler2D WaterCausticField;
uniform sampler2D WaterCausticFieldFar;
vec3 cachedCausticSource(vec2 entry,bool far) {
    float extent=far?256.0:64.0;
    vec2 origin=WaterVolumeInfo.xz-(far?vec2(96):vec2(0));
    vec3 flatRay=refract(-normalize(SkySun.xyz),vec3(0,1,0),1.0/1.333);
    vec2 local=(entry-origin+flatRay.xz/max(-flatRay.y,.2)*3.0)/extent;
    // Source texels are grid nodes including both endpoints, rather than
    // cell centres. Correct the normalized lookup for the extra edge node.
    if(far) {
        vec2 size=vec2(textureSize(WaterCausticFieldFar,0));
        return textureLod(WaterCausticFieldFar,(local*(size-1.0)+.5)/size,0.0).xyz;
    }
    vec2 size=vec2(textureSize(WaterCausticField,0));
    return textureLod(WaterCausticField,(local*(size-1.0)+.5)/size,0.0).xyz;
}
vec2 cachedCausticBeam(vec2 world,float depth,bool far) {
    vec3 incident=-normalize(SkySun.xyz);
    vec3 ray=refract(incident,vec3(0,1,0),1.0/1.333);
    vec2 beam=ray.xz/max(-ray.y,.2),entry=world-beam*depth;
    // Two bounded fixed-point iterations identify a local refracting patch.
    // Density still comes from forward multi-patch accumulation, not a
    // one-root inverse Jacobian. This is an approximate phase reconstruction.
    for(int i=0;i<2;i++) {
        vec3 field=cachedCausticSource(entry,far);
        ray=refract(incident,normalize(vec3(-field.y,1,-field.z)),1.0/1.333);
        beam=ray.xz/max(-ray.y,.2);
        entry=world-beam*max(depth+field.x,.05);
    }
    return beam;
}
float cachedVolumeDepthDensity(sampler2D layers,sampler2D middle,vec2 world,
                              vec2 origin,float extent,float depth,float footprint,vec2 beam) {
    vec4 planes=vec4(.5,1.5,3.5,5.5);
    int lower=depth<1.5?0:depth<3.5?1:2;
    float low=planes[lower],high=planes[lower+1];
    vec4 a=causticPixelDensity(layers,(world+beam*(low-depth)-origin)/extent,footprint,extent);
    vec4 b=causticPixelDensity(layers,(world+beam*(high-depth)-origin)/extent,footprint,extent);
    float value=mix(a[lower],b[lower+1],clamp((depth-low)/(high-low),0.0,1.0));
    if(depth<1.5) {
        float m=causticPixelDensity(middle,(world+beam*(.875-depth)-origin)/extent,footprint,extent).r;
        value=depth<.875?mix(a.r,m,clamp((depth-.5)/.375,0.0,1.0))
            :mix(m,b.g,clamp((depth-.875)/.625,0.0,1.0));
    }
    return value;
}
float waterVolumeDensity(vec2 world,float depth,float footprint) {
    float nearWeight=waterReceiverCoverage(world),nearValue=1.0;
    if(nearWeight>0.0) nearValue=cachedVolumeDepthDensity(WaterCaustics,WaterCausticsShallow,
        world,WaterVolumeInfo.xz,64.0,depth,footprint,cachedCausticBeam(world,depth,false));
    if(nearWeight>=.99999) return clamp(nearValue,.35,3.5);
    float farValue=cachedVolumeDepthDensity(WaterCausticsFar,WaterCausticsShallowFar,
        world,WaterVolumeInfo.xz-vec2(96),256.0,depth,footprint,cachedCausticBeam(world,depth,true));
    return clamp(mix(farValue,nearValue,nearWeight),.35,3.5);
}
float waterVolumePhase(float cosine) {
    // HG relative to isotropic scattering. Authored g=.55, bounded over all
    // directions; 4*pi is included in the artistic sunlight coefficient.
    const float g=.55;
    return (1.0-g*g)/pow(1.0+g*g-2.0*g*clamp(cosine,-1.0,1.0),1.5);
}
vec3 waterVolumeSegmentWeight(float distance,int segment) {
    float stepDistance=min(max(distance,0.0),16.0)/4.0;
    return underwaterAbsorption(float(segment)*stepDistance)
        -underwaterAbsorption(float(segment+1)*stepDistance);
}
vec3 waterVolumeScatteringAlbedo() {
    // The view extinction includes absorption. Only sigma_s/sigma_t of its
    // integrated loss can feed focused scattering. This authored neutral
    // sigma_s=.015 per block is below each unchanged extinction component;
    // red absorption therefore does not turn into a warm scattering veil.
    return vec3(.015)/underwaterExtinctionCoefficient();
}
vec3 underwaterCausticScatter(vec3 origin,vec3 delta,float endpointFootprint) {
    float distance=length(delta);
    if(distance<1e-6 || WaterVolumeInfo.w<.5 || SceneInfo.w<.5) return vec3(0);
    float solar=smoothstep(.04,.35,SkySun.y)*pow(AtmosphereColor.a,3.0);
    if(solar<.001) return vec3(0);
    vec3 sunlight=normalize(SkySun.xyz),direction=delta/distance;
    vec3 flatRay=refract(-sunlight,vec3(0,1,0),1.0/1.333);
    vec2 cloudUV=vec2(atan(sunlight.z,sunlight.x)/(2.0*SKY_PI)+.5,.5-asin(clamp(sunlight.y,-1.0,1.0))/SKY_PI);
    float cloud=textureLod(SkyClouds,cloudUV,0.0).a;
    solar*=cloud;
    if(solar<.001) return vec3(0);
    vec3 source=terrainSunRadiance()*.06*waterVolumePhase(dot(direction,-flatRay))
        *waterVolumeScatteringAlbedo();
    vec3 scatter=vec3(0);
    float travel=min(distance,16.0),stepDistance=travel/4.0;
    for(int i=0;i<4;i++) {
        float along=(float(i)+.5)*stepDistance;
        vec3 world=origin+direction*along;
        float surfaceY,depth=receiverWaterDepth(world,vec3(0),surfaceY);
        float coverage=max(waterReceiverCoverage(world.xz),farWaterReceiverCoverage(world.xz));
        float mask=causticVisibility(depth,.24,SkySun.y,AtmosphereColor.a,1.0,1.0)*coverage;
        if(mask<.001) continue;
        vec2 beam=cachedCausticBeam(world.xz,depth,waterReceiverCoverage(world.xz)<.5);
        vec3 entry=vec3(world.x-beam.x*depth,surfaceY,world.z-beam.y*depth);
        float visibility=surfaceShadowAt(entry-CameraWorldTime.xyz+sunlight*.035,.0003)
            *surfaceShadowAt(world-CameraWorldTime.xyz+sunlight*.035,.0003);
        float footprint=max(.24,endpointFootprint*along/max(distance,.001));
        float density=waterVolumeDensity(world.xz,depth,footprint);
        vec3 incident=waterIncidentTransmission(depth/max(abs(flatRay.y),.35));
        // Positive extinction-integrated segment weights telescope to the
        // covered Beer loss, scaled by sigma_s/sigma_t above. Add scattering
        // on this view segment only; the
        // endpoint colour already retains its one original attenuation.
        scatter+=waterVolumeSegmentWeight(distance,i)*source*incident*density*mask*visibility*cloud;
    }
    return scatter;
}

// Integrate separate sunlight shafts through the actual wet volume. The bed's
// five-plane focusing cache ends at six blocks; ordinary sunlit water does not.
// No screen-space beam mask, fixed sea height, or extra view absorption is used.
float waterShaftAvailability(float elevation,float clear) {
    return smoothstep(.02,.20,elevation)*pow(clamp(clear,0.0,1.0),3.0);
}
vec3 waterVolumeSegmentBetween(float start,float end) {
    return max(underwaterAbsorption(start)-underwaterAbsorption(end),vec3(0));
}
float waterShaftCascadeShadow(int cascade,vec3 q,float bias) {
    if(max(abs(q.x),abs(q.y))>=1.0 || q.z<=0.0 || q.z>=1.0) return 1.0;
    float resolution=cascade==2 ? 1024.0 : 2048.0;
    vec2 grid=(q.xy*.5+.5)*resolution-.5,base=floor(grid),f=fract(grid);
    float sum=0.0;
    // Compare four depths before interpolation. This small continuous kernel
    // avoids point-shadow sparkle without multiplying the full surface PCF.
    for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        float weight=(x==0 ? 1.0-f.x : f.x)*(y==0 ? 1.0-f.y : f.y);
        sum+=weight*(q.z+bias>=readShadow(cascade,(base+vec2(x,y)+.5)/resolution) ? 1.0 : 0.0);
    }
    return sum;
}
float waterShaftShadow(vec3 p) {
    for(int i=0;i<3;i++) {
        vec4 clip=ShadowMatrices[i]*vec4(p,1);
        vec3 q=clip.xyz/clip.w;
        float edge=max(abs(q.x),abs(q.y));
        if(edge>=.98 || q.z<=0.0 || q.z>=1.0) continue;
        float visibility=waterShaftCascadeShadow(i,q,.0003),blend=smoothstep(.84,.98,edge);
        if(blend<=0.0) return visibility;
        float next=1.0;
        if(i<2) {
            vec4 other=ShadowMatrices[i+1]*vec4(p,1);
            next=waterShaftCascadeShadow(i+1,other.xyz/other.w,.0003);
        }
        return mix(visibility,next,blend);
    }
    return 1.0;
}
vec3 underwaterSunShafts(vec3 origin,vec3 delta) {
    float distance=length(delta);
    if(distance<.000001 || WaterVolumeInfo.w<.5 || SceneInfo.w<.5) return vec3(0);
    float available=waterShaftAvailability(SkySun.y,AtmosphereColor.a);
    if(available<.001) return vec3(0);
    vec3 sun=normalize(SkySun.xyz),direction=delta/distance;
    vec3 incoming=refract(-sun,vec3(0,1,0),1.0/1.333);
    vec2 slope=incoming.xz/max(-incoming.y,.2);
    vec2 cloudUV=vec2(atan(sun.z,sun.x)/(2.0*SKY_PI)+.5,.5-asin(clamp(sun.y,-1.0,1.0))/SKY_PI);
    available*=textureLod(SkyClouds,cloudUV,0.0).a;
    if(available<.001) return vec3(0);
    // Independent authored radiance scale; not a measured water calibration.
    vec3 source=terrainSunRadiance()*.24*available*waterVolumeScatteringAlbedo()
        *waterVolumePhase(dot(direction,-incoming));
    float travel=min(distance,48.0);
    vec3 result=vec3(0);
    for(int i=0;i<16;i++) {
        float a=float(i)/16.0,b=float(i+1)/16.0;
        float start=travel*a*a,end=travel*b*b;
        vec3 world=origin+direction*(start+end)*.5;
        float surfaceY,depth=receiverWaterDepth(world,vec3(0),surfaceY);
        float wet=smoothstep(.02,.18,depth);
        if(wet<.001) continue;
        vec3 entry=vec3(world.x-slope.x*depth,surfaceY,world.z-slope.y*depth);
        float visibility=waterShaftShadow(entry-CameraWorldTime.xyz+sun*.035)
            *waterShaftShadow(world-CameraWorldTime.xyz+sun*.035);
        vec3 incident=waterIncidentTransmission(depth/max(-incoming.y,.35));
        result+=waterVolumeSegmentBetween(start,end)*source*incident*wet*visibility;
    }
    return result;
}
vec3 underwaterFocusedScatter(vec3 origin,vec3 delta,float endpointFootprint) {
    return underwaterCausticScatter(origin,delta,endpointFootprint)+underwaterSunShafts(origin,delta);
}
#endif
