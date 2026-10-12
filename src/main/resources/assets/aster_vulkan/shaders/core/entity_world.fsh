#version 330
#extension GL_ARB_separate_shader_objects : require

#ifdef GLINT
#include <minecraft:globals.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <aster_vulkan:scene.glsl>
#ifndef OIT_ALPHA_ONLY
#include <aster_vulkan:fog_forward.glsl>
#endif
#ifndef OIT_ALPHA_ONLY
#include <aster_vulkan:shadows.glsl>
#include <aster_vulkan:entity_shadows.glsl>
#include <aster_vulkan:lighting.glsl>
#include <aster_vulkan:cloud_shadow.glsl>
#include <aster_vulkan:local_lighting.glsl>
vec3 entityLinear(vec3 c) { return mix(c/12.92,pow(max((c+.055)/1.055,vec3(0)),vec3(2.4)),step(vec3(.04045),c)); }
#endif
layout(location=8) in vec3 entityPosition;
layout(location=9) in vec3 entityNormal;
layout(location=10) in vec2 entityLight;

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
#else
layout(location = 2) in vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) in vec4 overlayColor;
#endif

layout(location = 6) in vec2 texCoord0;
#ifdef GLINT
layout(location = 7) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color) {
    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif

    #ifndef EMISSIVE
    vec3 albedo = entityLinear(color.rgb);
    if (SceneInfo.w > .5 && !verificationFlag(524288)) {
        vec3 normal = normalize(entityNormal);
#ifdef PER_FACE_LIGHTING
        if (!gl_FrontFacing) normal = -normal;
#endif
        float cosine = max(dot(normal,SunDirection.xyz),0.0);
        // Small geometry offset keeps feet connected to the ground. Ambient
        // and block light remain independent of sun/moon visibility.
        bool previous=verificationFlag(67108864);
        vec3 receiver=entityPosition+normal*(previous ? .012 : .006);
        float visibility=previous ? surfaceShadowAt(receiver,.00005+.00016*(1.0-cosine))
            : softSurfaceShadowAt(receiver,.000018+.000035*(1.0-cosine),dFdx(entityPosition),dFdy(entityPosition));
        visibility*=cloudVisibility(entityPosition);
        if (verificationFlag(4194304)) visibility = 1.0;
        color.rgb = albedo*localAmbientAt(terrainAmbient(entityLinear(lightMapColor.rgb),normal,entityLight),entityLinear(lightMapColor.rgb),entityLight,
            CameraWorldTime.xyz+entityPosition+normal*.15,normal);
        color.rgb += albedo*heldRadianceAt(CameraWorldTime.xyz+entityPosition+normal*.15,normal);
        color.rgb += albedo*terrainSunRadiance()*SunDirection.w*entityLight.y*(cosine/3.14159265)*visibility;
        if (verificationFlag(2097152)) color.rgb = vec3(visibility);
    } else color.rgb = entityLinear(color.rgb * lightMapColor.rgb);
    color.a *= lightMapColor.a;
    #endif

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);
    // Matches BlendFuntion.GLINT
    color.rgb += entityLinear(glintColor.rgb * glintColor.rgb);
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(lightingFogColor(entityLinear(FogColor.rgb)) * color.a, FogColor.a);
    #else
    vec4 fogColor = vec4(lightingFogColor(entityLinear(FogColor.rgb)),FogColor.a);
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

#endif
void main() {
    vec4 color = texture(Sampler0, texCoord0);

    #ifdef OIT_ADDITIVE
    color.a = min(0.99, color.a);
    #endif

    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
    #else
    vec4 faceVertexColor = vertexColor;
    #endif

    #ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    // The dissolve effect entirely replaces translucency
    faceVertexColor.a = 1.0;
    #endif

    color *= faceVertexColor * ColorModulator;

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = fogSurface(calculateFinalColor(color));
    #endif
}
