#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water.glsl>
#include <aster_vulkan:shadows.glsl>
#include <aster_vulkan:water_caustics.glsl>
#include <aster_vulkan:water_volume_scattering.glsl>
uniform sampler2D SceneDepth;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    if(SceneInfo.z<.5 || SceneInfo.w<.5 || WaterVolumeInfo.w<.5) {
        fragColor=vec4(0);return;
    }
    ivec2 size=textureSize(SceneDepth,0),base=ivec2(gl_FragCoord.xy)*2;
    float depth=0.0;ivec2 nearest=clamp(base,ivec2(0),size-1);
    // Conservative nearest endpoint for the native 2x2 pixel footprint.
    for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        ivec2 pixel=clamp(base+ivec2(x,y),ivec2(0),size-1);
        float candidate=texelFetch(SceneDepth,pixel,0).r;
        // Native water replay uses its own 75% resolution. Full-size pixel
        // addresses cannot index that texture; sample the matching view UV.
        vec2 waterUV=(vec2(pixel)+.5)/vec2(size);
        if(textureLod(WaterSurface,waterUV,0.0).a>.5)
            candidate=max(candidate,textureLod(WaterDepth,waterUV,0.0).r);
        if(candidate>depth) { depth=candidate;nearest=pixel; }
    }
    vec2 uv=(vec2(nearest)+.5)/vec2(size);
    vec4 point=InvViewProjection*vec4(uv*2.0-1.0,depth,1);
    vec3 delta=depth>.000001 ? point.xyz/point.w : cameraRay(uv,InvViewProjection)*48.0;
    fragColor=vec4(underwaterSunShafts(CameraWorldTime.xyz,delta),length(delta));
}
