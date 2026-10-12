#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#include <minecraft:projection.glsl>
#include <aster_vulkan:scene.glsl>
#ifdef ASTER_LINEAR_WORLD
#include <aster_vulkan:fog_forward.glsl>
#endif
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:water_reflection.glsl>
#include <aster_vulkan:water_refraction.glsl>
#include <aster_vulkan:pbr.glsl>
#include <aster_vulkan:environment.glsl>
#include <aster_vulkan:material_surface.glsl>
#include <aster_vulkan:lighting.glsl>
#include <aster_vulkan:cloud_shadow.glsl>
#ifndef OIT_ALPHA_ONLY
#include <aster_vulkan:local_lighting.glsl>
#endif
#include <aster_vulkan:normals.glsl>
#include <aster_vulkan:shadows.glsl>
#include <aster_vulkan:entity_shadows.glsl>
#include <aster_vulkan:water_caustics.glsl>
#include <aster_vulkan:water_lighting.glsl>
uniform sampler2D Sampler0;
uniform sampler2D OpaqueColor;
uniform sampler2D OpaqueDepth;
uniform sampler2D WaterReflection;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
uniform sampler2D MaterialAtlas;
uniform sampler2D NormalAtlas;
layout(location=0) in float sphericalVertexDistance;
layout(location=1) in float cylindricalVertexDistance;
layout(location=2) in vec4 vertexColor;
layout(location=3) in vec2 texCoord0;
layout(location=4) in float chunkVisibility;
layout(location=5) in vec3 relativePosition;
layout(location=6) in float waterMaterial;
layout(location=7) in vec2 materialLight;
layout(location=8) in vec3 albedoTint;
layout(location=9) in vec3 nativeLight;
layout(location=10) in float waterShoreWeight;
layout(location=11) in vec2 windMetadata;
layout(location=12) in float faceShade;
#ifndef OIT_ALPHA_ONLY
layout(location=0) out vec4 fragColor;
#endif
vec4 calculateFinalColor(vec4 color) {
#ifdef ASTER_LINEAR_WORLD
    vec4 nativeFog = vec4(lightingFogColor(displayToLinear(FogColor.rgb)),FogColor.a);
#else
    vec4 nativeFog = FogColor;
#endif
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(nativeFog.rgb * color.a, nativeFog.a);
#else
    vec4 fogColor = nativeFog;
#endif
    bool customUnderwater=AsterSettings.x > .5 && SceneInfo.z > .5 && !verificationFlag(512);
    if(customUnderwater) fogColor.rgb=underwaterBodyRadiance()*color.a;
    // Custom underwater absorption is applied once in the post pass. Retain
    // native render-distance fog but avoid a second blue environmental fog.
    float environmentalStart=customUnderwater ? FogRenderDistanceEnd : FogEnvironmentalStart;
    float environmentalEnd=customUnderwater ? FogRenderDistanceEnd+1.0 : FogEnvironmentalEnd;
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, environmentalStart, environmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#ifndef OIT_ALPHA_ONLY
// Private GPU diagnostics use the actual refracted sample, depth derivatives
// and native occupancy rather than a hypothetical white bed probe.
vec3 waterReceiverDiagnostic=vec3(1.0,0.0,1.0);
vec3 opaquePosition(vec2 uv, float depth) {
    vec4 p = InvViewProjection * vec4(uv*2.0-1.0,depth,1.0);
    return p.xyz/p.w;
}
vec3 filteredWaterReflection(vec3 position,vec3 normal,vec3 fallback) {
    vec2 size=vec2(textureSize(WaterReflection,0));
    vec2 grid=gl_FragCoord.xy/SceneInfo.xy*size-.5;
    ivec2 base=ivec2(floor(grid));vec2 blend=fract(grid);
    vec3 sum=vec3(0);float total=0.0;
    vec3 positiveSum=vec3(0);float positiveTotal=0.0;
    vec3 low=vec3(1e20),high=vec3(-1e20);
    bool previous=verificationFlag(512) || verificationFlag(4096);
    // Depth/normal-aware upsampling prevents history from bleeding across
    // shoreline silhouettes or between separate layers of water.
    for(int y=-1;y<=2;y++) for(int x=-1;x<=2;x++) {
        if(previous && (x < 0 || x > 1 || y < 0 || y > 1)) continue;
        ivec2 pixel=clamp(base+ivec2(x,y),ivec2(0),ivec2(size)-1);
        vec4 surface=texelFetch(WaterSurface,pixel,0);
        if(surface.a < .5 || dot(normalize(surface.xyz),normal) < .8) continue;
        vec3 p=opaquePosition((vec2(pixel)+.5)/size,texelFetch(WaterDepth,pixel,0).r);
        float error=abs(length(p)-length(position));
        float limit=.2+length(position)*.025;
        if(error > limit*3.0 || abs(p.y-position.y) > .15) continue;
        float w=previous ? (x == 0 ? 1.0-blend.x : blend.x)*(y == 0 ? 1.0-blend.y : blend.y)
            : waterCubicWeight(float(x)-blend.x)*waterCubicWeight(float(y)-blend.y);
        w*=exp(-error/limit);
        vec3 value=texelFetch(WaterReflection,pixel,0).rgb;
        low=min(low,value);high=max(high,value);
        sum+=value*w;total+=w;
        positiveSum+=value*max(w,0.0);positiveTotal+=max(w,0.0);
    }
    // Cubic negative lobes preserve silhouettes but must not create ringing,
    // negative light or highlights beyond the validated local radiance range.
    if(previous || verificationFlag(8192)) return total > (previous ? .0001 : .05) ? clamp(sum/total,low,high) : fallback;
    if(positiveTotal <= .0001) return fallback;
    vec3 safe=positiveSum/positiveTotal;
    // A bank can reject positive and negative cubic taps asymmetrically.
    // Use the remaining positive support instead of abruptly switching to sky.
    return total > .0001 ? mix(safe,clamp(sum/total,low,high),smoothstep(.05,.25,total)) : safe;
}
vec3 refractWater(vec3 p, vec3 normal, vec3 tint) {
    vec2 uv = gl_FragCoord.xy/SceneInfo.xy;
    vec3 viewNormal = mat3(ModelViewMat)*normal;
    vec2 offset = viewNormal.xy*0.028/(1.0+length(p)*0.035);
    vec2 sampleUv = clamp(uv+offset,vec2(0.001),vec2(0.999));
    float depth = texture(OpaqueDepth,sampleUv).r;
    vec3 behind = depth > 0.000001 ? opaquePosition(sampleUv,depth) : p-normal*12.0;
    // Reject refraction samples that would pull a foreground silhouette into water.
    if (depth > 0.000001 && length(behind) < length(p)-0.10) {
        sampleUv = uv;
        depth = texture(OpaqueDepth,sampleUv).r;
        behind = depth > 0.000001 ? opaquePosition(sampleUv,depth) : p-normal*12.0;
    }
    float thickness = clamp(length(behind-p),0.0,32.0);
    vec3 transmission = exp(-vec3(0.13,0.055,0.026)*thickness);
    vec3 bottom = texture(OpaqueColor,sampleUv).rgb;
    return bottom*transmission + tint*(1.0-transmission)*0.65;
}
vec3 refractWaterModern(vec3 p,vec3 normal,vec3 tint,float skyAccess,out float thickness,out float contactCoverage) {
    bool submerged=SceneInfo.z > .5;
    vec3 direction=refract(normalize(p),normal,submerged ? 1.333 : 1.0/1.333);
    thickness=0.0;
    contactCoverage=1.0;
    if(dot(direction,direction) < .001) return vec3(0);
    vec2 uv=gl_FragCoord.xy/SceneInfo.xy;
    float originalDepth=texture(OpaqueDepth,uv).r;
    vec3 original=originalDepth > .000001 ? opaquePosition(uv,originalDepth) : p+direction*32.0;
    if(!verificationFlag(131072) && originalDepth > .000001 && !submerged) {
        // A vanishing water path at a solid contact must approach the opaque
        // bank radiance. Full reflection at zero thickness draws a bright rim.
        float gap=length(original-p);
        contactCoverage=mix(smoothstep(.006,.18,gap),1.0,smoothstep(.2,.7,waterShoreWeight));
    }
    // Project a short segment along Snell's refracted direction. This is a
    // bounded screen-space approximation, not off-screen refraction tracing.
    vec4 probe=ProjMat*ModelViewMat*vec4(p+direction*clamp(length(original-p),.25,6.0),1);
    vec2 offset=probe.xy/max(probe.w,.001)*.5+.5-uv;
    // Off-screen refraction has no captured background. Bound the offset and
    // fade it near viewport edges instead of stretching a clamped edge texel.
    offset*=min(1.0,.025/max(length(offset),.0001));
    if(!verificationFlag(8192)) {
        // Displacement is pinned at solid banks. Refraction must also return
        // smoothly to the undisplaced background at the contact, otherwise
        // normal ripples drag the adjoining block face into a moving seam.
        float contact=smoothstep(.04,.50,length(original-p));
        offset*=contact*smoothstep(0.0,.65,waterShoreWeight);
    }
    if(verificationFlag(268435456)) offset=vec2(0);
    vec2 sampleUv=uv+waterRefractionOffset(uv,offset,SceneInfo.xy,verificationFlag(33554432));
    float depth=texture(OpaqueDepth,sampleUv).r;
    vec3 behind=depth > .000001 ? opaquePosition(sampleUv,depth) : p+direction*32.0;
    if(probe.w <= 0.0 || (depth > .000001 && length(behind) < length(p)-.05)) {
        sampleUv=uv;depth=originalDepth;behind=original;
    }
    vec3 bottom=texture(OpaqueColor,sampleUv).rgb;
    if(submerged) {
        // The outside portion contains no water. View-to-surface attenuation
        // is handled once by the underwater post pass.
        return depth > .000001 ? bottom : mix(bottom,waterSky(direction),.85);
    }
    thickness=clamp(length(behind-p),0.0,48.0);
    if(depth > .000001 && WaterVolumeInfo.w > 1.5) {
        vec2 stepUV=1.0/SceneInfo.xy;
        float depthX=texture(OpaqueDepth,sampleUv+vec2(stepUV.x,0)).r;
        float depthY=texture(OpaqueDepth,sampleUv+vec2(0,stepUV.y)).r;
        vec3 dx=opaquePosition(sampleUv+vec2(stepUV.x,0),depthX)-behind;
        vec3 dy=opaquePosition(sampleUv+vec2(0,stepUV.y),depthY)-behind;
        vec3 crossNormal=cross(dx,dy);
        float size=max(length(dx),length(dy));
        if(depthX > .000001 && depthY > .000001 && dot(crossNormal,crossNormal) > 1e-14 && size < .20+length(behind)*.015) {
            vec3 receiverNormal=normalize(crossNormal);
            if(dot(receiverNormal,-behind) < 0.0) receiverNormal=-receiverNormal;
            // White-light diagnostic preserves the old transfer measurement.
            // Ordinary opaque radiance is already lit by its component paths.
            vec3 received=waterCausticReceiver(vec3(1),behind,receiverNormal,size);
            if(WaterVolumeInfo.w>1.5) {
                vec3 world=behind+CameraWorldTime.xyz;
                float surface,waterDepth=receiverWaterDepth(world,receiverNormal,surface);
                if(WaterVolumeInfo.w<2.5) waterReceiverDiagnostic=vec3(waterDepth/8.0,waterReceiverCoverage(world.xz),max(receiverNormal.y,0.0));
                else if(WaterVolumeInfo.w<3.5) waterReceiverDiagnostic=received/4.0;
                else {
                    float path=waterDepth/max(abs(refract(-normalize(SkySun.xyz),vec3(0,1,0),1.0/1.333).y),.35);
                    waterReceiverDiagnostic=vec3(received.b/max(waterIncidentTransmission(path).b,.0001)/4.0);
                }
            }
        }
    }
    vec3 attenuation=waterAbsorption(thickness,tint);
    return bottom*attenuation+waterBodyRadiance(tint,skyAccess)*(1.0-attenuation);
}
vec3 waterSunSpecular(vec3 normal,vec3 view,float footprint,float pixelVariance,float visibility) {
    float nv=max(dot(normal,view),0.0),nl=max(dot(normal,SunDirection.xyz),0.0);
    if(nv <= 0.0 || nl <= 0.0) return vec3(0);
    vec3 halfVector=normalize(view+SunDirection.xyz);
    float roughness=waterSurfaceRoughness(footprint);
    float alpha=roughness*roughness;
    // Distance filtering already accounts for removed wave bands. The resolved
    // normal can still turn within one pixel; integrate that local variation
    // into the sun lobe rather than point-sampling a narrow bright glint.
    // This isotropic GGX approximation uses a half-pixel Gaussian footprint.
    // A bounded kernel avoids widening discontinuities at water silhouettes.
    float a2=min(1.0,alpha*alpha+min(max(pixelVariance,0.0),.02));
    float nh=max(dot(normal,halfVector),0.0),denominator=nh*nh*(a2-1.0)+1.0;
    float distribution=a2/(ASTER_PI*denominator*denominator);
    float masking=2.0*nv/(nv+sqrt(a2+(1.0-a2)*nv*nv));
    float shadowing=2.0*nl/(nl+sqrt(a2+(1.0-a2)*nl*nl));
    float fresnel=waterDielectricFresnel(max(dot(view,halfVector),0.0),false);
    return terrainSunRadiance()*SunDirection.w*visibility*cloudVisibility(relativePosition)*distribution*masking*shadowing*fresnel/max(4.0*nv,.001);
}
#endif
#if defined(ASTER_LINEAR_WORLD) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D ShadowCaster0;
uniform sampler2D ShadowCaster1;
uniform sampler2D ShadowCaster2;
float canopyCaster(int cascade,vec2 uv) {
    if(cascade==0) return texture(ShadowCaster0,uv).r;
    if(cascade==1) return texture(ShadowCaster1,uv).r;
    return texture(ShadowCaster2,uv).r;
}
float canopyTransmissionCascade(int cascade,vec3 q,float bias,vec3 dpdx,vec3 dpdy,float radius) {
    if(max(abs(q.x),abs(q.y))>=1.0 || q.z<=0.0 || q.z>=1.0) return 0.0;
    float extent=cascade==0 ? 24.0 : cascade==1 ? 72.0 : 160.0;
    vec2 center=q.xy*.5+.5,slope=shadowReceiverSlope(cascade,dpdx,dpdy);
    float sum=0.0;
    // A stable world-space footprint softens thin-leaf illumination. It
    // follows native caster geometry and wind, without a random spot mask.
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        vec2 uv=center+vec2(x,y)*radius/(2.0*extent);
        float receiver=q.z+clamp(dot(slope,uv-center),-.002,.002);
        float depth=readShadow(cascade,uv);
        if(depth>receiver+bias) {
            float path=max(0.0,(depth-receiver)*600.0);
            float weight=(x==0 ? .5 : .25)*(y==0 ? .5 : .25);
            sum+=canopyCaster(cascade,uv)*exp(-path*.035)*weight;
        }
    }
    return sum;
}
float canopyTransmissionAt(vec3 p,float bias,vec3 dpdx,vec3 dpdy) {
    float radius=.45+min(length(p)*.003,.20);
    for(int cascade=0;cascade<3;cascade++) {
        vec4 c=ShadowMatrices[cascade]*vec4(p,1);vec3 q=c.xyz/c.w;
        float edge=max(abs(q.x),abs(q.y));
        if(edge>=.98 || q.z<=0.0 || q.z>=1.0) continue;
        float amount=canopyTransmissionCascade(cascade,q,bias,dpdx,dpdy,radius);
        float blend=smoothstep(.84,.98,edge);
        if(blend<=0.0) return amount;
        float next=0.0;
        if(cascade<2) {
            c=ShadowMatrices[cascade+1]*vec4(p,1);
            next=canopyTransmissionCascade(cascade+1,c.xyz/c.w,bias,dpdx,dpdy,radius);
        }
        return mix(amount,next,blend);
    }
    return 0.0;
}
#endif
void main() {
    float leafVisibility=0.0,solidVisibility=1.0;
    float waterFootprint=max(length(dFdx(relativePosition).xz),length(dFdy(relativePosition).xz));
#ifdef ASTER_LINEAR_WORLD
    vec4 materialSample=texture(MaterialAtlas,texCoord0);
    vec3 material=materialSample.rgb;
    float transmission=materialSample.a;
    if(windMetadata.x > .5 && windMetadata.x < 1.5) transmission=max(transmission,.65);
    if(verificationFlag(32) && transmission > .01) { transmission=0.0;material.r=.8; }
#endif
#if defined(ASTER_LINEAR_WORLD) && !defined(OIT_ALPHA_ONLY)
    // Evaluate gradients and map samples before alpha discard/divergent paths.
    vec3 dpdx=dFdx(relativePosition),dpdy=dFdy(relativePosition);
    vec2 duvdx=dFdx(texCoord0),duvdy=dFdy(texCoord0);
    vec3 geometricNormal=normalize(cross(dpdx,dpdy));
    if(dot(geometricNormal,-relativePosition) < 0.0) geometricNormal=-geometricNormal;
    vec4 normalSample=texture(NormalAtlas,texCoord0);
    vec3 shadingNormal=mappedTerrainNormal(geometricNormal,dpdx,dpdy,duvdx,duvdy,normalSample);
#endif
    vec4 texel = UseRgss == 1 ? sampleRGSS(Sampler0,texCoord0,1.0/TextureSize) : sampleNearest(Sampler0,texCoord0,1.0/TextureSize);
    vec4 color = texel * vertexColor;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) discard;
#endif
#ifdef ASTER_LINEAR_WORLD
    vec3 tint=albedoTint;
#ifndef OIT_ALPHA_ONLY
    if(AsterSettings.x > .5 && SceneInfo.w > .5 && waterMaterial < .5)
        tint=terrainAlbedoTint(tint,faceShade);
#endif
    // Texture and biome tint are authored together in display colour. Decode
    // their native combination once, matching the entity albedo boundary.
    vec3 albedo = displayToLinear(texel.rgb*tint);
    vec3 light = displayToLinear(nativeLight);
    material.r=max(material.r,.045);
#ifndef OIT_ALPHA_ONLY
    // Averaged normal mip length carries subpixel variance. Broaden the GGX
    // lobe as detail minifies rather than retaining sharp distant highlights.
    material.r=materialFilteredRoughness(material.r,normalSample);
    WaterLightTransport waterLight=waterDryLight();
    if(AsterSettings.x > .5 && waterMaterial < .5) {
        waterLight=waterLightAt(relativePosition,geometricNormal,max(length(dpdx),length(dpdy)));
        light=localAmbientAt(terrainAmbient(light,shadingNormal,materialLight,waterLight.skyTransmission),light,materialLight,
            CameraWorldTime.xyz+relativePosition+geometricNormal*.51,shadingNormal);
        // Exposed upward-facing surfaces become subtly darker and smoother
        // in rain. Enclosed blocks retain their material/lightmap behavior.
        materialRainSurface(albedo,material,relativePosition,geometricNormal,materialLight.y,transmission);
    }
#endif
    vec3 f0 = mix(vec3(.04),albedo,material.g);
    // Native lightmap supplies approximate indirect/block illumination.
    color.rgb = albedo*light*(1.0-material.g)+f0*light*mix(.08,.5,material.g);
#endif
    vec3 waterNormal = vec3(0,1,0);
    float waterFresnel = 0.02;
    if (AsterSettings.x > 0.5 && waterMaterial > 0.5 && (SceneInfo.z < 0.5 || !verificationFlag(512))) {
#if defined(ASTER_LINEAR_WORLD) && !defined(OIT_ALPHA_ONLY)
        waterNormal=waterShadingNormal(relativePosition,geometricNormal,waterFootprint,waterShoreWeight);
#else
        waterNormal=waterShadingNormal(relativePosition);
#endif
        float cosine=max(dot(normalize(-relativePosition),waterNormal),0.0);
        waterFresnel=verificationFlag(512) ? .02+.98*pow(1.0-cosine,5.0) : waterDielectricFresnel(cosine,SceneInfo.z > .5);
        // Reflection/transmission already contain the background. Coverage is
        // independent of optical transmission, including OIT alpha phases.
        color.a=verificationFlag(512) ? clamp(.86+waterFresnel*.10,.86,.96) : 1.0;
    }
#ifndef OIT_ALPHA_ONLY
    // Take screen derivatives before the lighting branches. Coverage/shore
    // handling remains independent, including native OIT alpha phases.
    vec3 waterNormalDx=dFdx(waterNormal),waterNormalDy=dFdy(waterNormal);
    float waterPixelVariance=.25*(dot(waterNormalDx,waterNormalDx)+dot(waterNormalDy,waterNormalDy));
    if (AsterSettings.x > 0.5) {
#ifdef ASTER_LINEAR_WORLD
        vec3 normal=shadingNormal;
#else
        vec3 normal = normalize(cross(dFdx(relativePosition), dFdy(relativePosition)));
        if (dot(normal,-relativePosition) < 0.0) normal = -normal;
#endif
        float sun = max(dot(normal,SunDirection.xyz),0.0);
#ifdef ASTER_LINEAR_WORLD
        if (SceneInfo.w > .5 && !verificationFlag(4) && (waterMaterial < .5 || verificationFlag(512))) {
            // Use geometry for shadow offset: a normal map adds shading detail,
            // not caster geometry, and must not move the receiver into a wall.
            float geometrySun=max(dot(geometricNormal,SunDirection.xyz),0.0);
            bool previousShadow=verificationFlag(67108864);
            vec3 receiver=relativePosition+geometricNormal*(previousShadow || transmission>.01 ? .025 : .006);
            float bias=previousShadow || transmission>.01 ? .00012+.0005*(1.0-geometrySun) : .000025+.000035*(1.0-geometrySun);
            float visibility=1.0;
            if(!verificationFlag(2)) {
                if(transmission > .01 && !verificationFlag(8) && !verificationFlag(256)) {
                    receiver=relativePosition+SunDirection.xyz*.035;
                    visibility=leafShadowAt(receiver,bias,dpdx,dpdy);
                } else visibility=verificationFlag(8) ? shadowAt(receiver,bias) : previousShadow ? surfaceShadowAt(receiver,bias)
                    : softSurfaceShadowAt(receiver,bias,dpdx,dpdy);
            }
            visibility*=cloudVisibility(relativePosition);
            solidVisibility=visibility;
            if(transmission > .01) leafVisibility=visibility;
            vec3 brdf=transmission > .01 ? leafBrdf(albedo,material.r,normal,normalize(-relativePosition),waterLight.direction,transmission)
                : directBrdf(albedo,material.r,material.g,normal,normalize(-relativePosition),waterLight.direction);
            color.rgb += waterDirectRadiance(brdf*terrainSunRadiance()*SunDirection.w*materialLight.y*visibility,waterLight);
            if(waterMaterial<.5 && transmission<.01)
                color.rgb+=materialEnvironment(albedo,material.r,material.g,normal,normalize(-relativePosition),materialLight.y)*waterLight.skyTransmission;
            // Restricted understory transmission, not full multilayer GI.
            // Native sky access estimates canopy thickness and rejects closed
            // rooms; opaque nearest blockers carry no leaf marker. Existing
            // direct shadows, ambient fill, foliage and water remain intact.
            float upward=(waterMaterial<.5 && transmission<.01)
                ? smoothstep(.65,.95,geometricNormal.y) : 0.0;
            float access=smoothstep(.4,.9,materialLight.y);
            float canopyWeather=lightingDay()*(1.0-lightingRain());
            if(upward*access*canopyWeather>.001 && visibility<.95 && !verificationFlag(2)) {
                float throughLeaves=canopyTransmissionAt(receiver,bias,dpdx,dpdy);
                float amount=throughLeaves*access*upward*canopyWeather
                    *cloudVisibility(relativePosition)*(1.0-visibility);
                color.rgb+=albedo/ASTER_PI*terrainSunRadiance()*SunDirection.w*amount*.20
                    *waterLight.incidentTransmission*waterLight.focusGain;
            }
        }
#else
        if (SceneInfo.w > 0.5) {
            float directFraction = materialLight.y/(materialLight.y+materialLight.x*1.5+0.35);
            directFraction *= 1.0-emissiveMaterial(texCoord0);
            vec3 tint = mix(vec3(0.82,0.91,1.03),vec3(1.15,1.10,0.98),sun * SunDirection.w);
            color.rgb *= mix(vec3(1.0),tint,directFraction);
        }
#endif
        // The hidden legacy comparison preserves the previous submerged path.
        if (waterMaterial > 0.5 && (SceneInfo.z < 0.5 || !verificationFlag(512))) {
            normal = waterNormal;
            vec3 view = normalize(-relativePosition);
            float fresnel = waterFresnel;
            vec3 reflectedSky = waterSky(reflect(-view,normal));
            vec3 reflection = filteredWaterReflection(relativePosition,normal,reflectedSky);
            if(verificationFlag(512)) {
                float sparkle=pow(max(dot(normal,normalize(view+SunDirection.xyz)),0.0),128.0);
                vec3 transmission=refractWater(relativePosition,normal,color.rgb);
                color.rgb=mix(transmission,reflection,fresnel)+vec3(1.0,.86,.58)*sparkle*SunDirection.w*.9;
            } else {
                float thickness,contactCoverage;
                vec3 transmitted=refractWaterModern(relativePosition,normal,displayToLinear(albedoTint),materialLight.y,thickness,contactCoverage);
                color.rgb=mix(transmitted,reflection,fresnel*contactCoverage);
                if(SceneInfo.z < .5 && SceneInfo.w > .5) {
                    float visibility=verificationFlag(2) ? 1.0 : surfaceShadowAt(relativePosition+SunDirection.xyz*.025,.0003);
                    color.rgb+=waterSunSpecular(normal,view,waterFootprint,waterPixelVariance,visibility)*materialLight.y*contactCoverage;
                }
            }
        }
    }
#ifdef ASTER_LINEAR_WORLD
    color.rgb += albedo*material.b*8.0;
    if(AsterSettings.x>.5 && waterMaterial<.5)
        color.rgb+=albedo*heldRadianceAt(CameraWorldTime.xyz+relativePosition+geometricNormal*.51,shadingNormal);
    color = mix(vec4(displayToLinear(FogColor.rgb),color.a),color,chunkVisibility);
#else
    color = mix(FogColor*vec4(1,1,1,color.a),color,chunkVisibility);
#endif
#endif
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z,color.a);
#else
    if(WaterVolumeInfo.w>1.5 && waterMaterial>.5) {fragColor=vec4(waterReceiverDiagnostic,1);return;}
    // Hidden-only diagnostic: remove albedo, fog and tone mapping from the
    // comparison so actual shadow grain cannot be confused with leaf pixels.
    if(verificationFlag(134217728)) {fragColor=vec4(vec3(solidVisibility),1);return;}
    if(verificationFlag(128)) { fragColor=vec4(vec3(leafVisibility),1);return; }
    fragColor = calculateFinalColor(color);
    #ifdef ASTER_LINEAR_WORLD
    fragColor=fogSurface(fragColor);
    #endif
#endif
}
