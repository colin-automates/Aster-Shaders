#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:fog_volume.glsl>
uniform sampler2D FogVolume;
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
#include <aster_vulkan:fog.glsl>
#include <aster_vulkan:lighting.glsl>
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:shadows.glsl>
#include <aster_vulkan:water_caustics.glsl>
#include <aster_vulkan:water_volume_scattering.glsl>
#include <aster_vulkan:underwater_reconstruct.glsl>
uniform sampler2D MaterialData;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;

vec3 positionAt(vec2 uv, float depth) {
    vec4 p = InvViewProjection * vec4(uv*2.0-1.0,depth,1.0);
    return p.xyz / p.w;
}
void main() {
    vec2 uv = texCoord;
    vec3 color = texture(SceneColor,uv).rgb;
    if(verificationFlag(128)) { fragColor=vec4(color,0);return; }
    float depth = texture(SceneDepth,uv).r;
    vec2 pixel = 1.0/SceneInfo.xy;
    if (depth > 0.000001) {
        vec3 p = positionAt(uv,depth);
        float distanceToCamera = length(p);
        vec3 n = normalize(cross(dFdx(p),dFdy(p)));
        if (dot(n,-p) < 0) n = -n;
        // Small screen-space occlusion kernel, with distance rejection at silhouettes.
        float occlusion = 0;
        for (int i=0; i<8; i++) {
            float angle = float(i)*2.399963;
            vec2 offset = vec2(cos(angle),sin(angle))*pixel*(3.0+float(i)*1.8);
            float otherDepth = texture(SceneDepth,uv+offset).r;
            if (otherDepth <= 0.000001) continue;
            vec3 delta = positionAt(uv+offset,otherDepth)-p;
            float d = length(delta);
            occlusion += max(dot(n,delta/max(d,0.001))-0.16,0.0)*exp(-d*1.5);
        }
        float amount=clamp(occlusion*0.18,0.0,0.25);
        // AO belongs to surface radiance. Preserve atmospheric scattering
        // after moving fog into per-fragment world shading.
        vec3 air=vec3(0);
        if(SceneInfo.w > .5 && SceneInfo.z < .5 && !verificationFlag(1) && !verificationFlag(134217728))
            air=fogAtDepth(FogVolume,uv,distanceToCamera).rgb;
        color=color*(1.0-amount)+air*amount;
        // Incident water lighting is assembled in the terrain material.
        // This pass owns AO and the separate eye-to-surface/receiver path.
    }
    if (SceneInfo.z > .5) {
        if(verificationFlag(512)) {
            if(depth > .000001) color=mix(color,displayToLinear(AtmosphereColor.rgb),1.0-exp(-length(positionAt(uv,depth))*.075));
        } else {
            vec3 waterEnd=depth > .000001 ? positionAt(uv,depth) : cameraRay(uv,InvViewProjection)*48.0;
            if(texture(WaterSurface,uv).a > .5 && texture(WaterDepth,uv).r > depth)
                waterEnd=positionAt(uv,texture(WaterDepth,uv).r);
            float waterDistance=length(waterEnd);
            vec3 attenuation=underwaterAbsorption(waterDistance);
            color=color*attenuation+underwaterBodyRadiance()*(1.0-attenuation);
            float endpointFootprint=max(length(dFdx(waterEnd)),length(dFdy(waterEnd)));
            color+=underwaterCausticScatter(CameraWorldTime.xyz,waterEnd,endpointFootprint)
                +underwaterShaftsAt(uv,CameraWorldTime.xyz,waterEnd);
        }
    }
    float luminance = max(dot(color,vec3(0.2126,0.7152,0.0722)),0.0001);
    fragColor = vec4(max(color,vec3(0.0)),log2(luminance));
}
