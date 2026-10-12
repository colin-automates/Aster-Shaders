#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:fog.glsl>
#include <aster_vulkan:fog_temporal.glsl>
#include <aster_vulkan:fog_volume.glsl>
uniform sampler2D FogRawVolume;
uniform sampler2D FogPreviousVolume;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    ivec2 size=textureSize(FogRawVolume,0),tileSize=size/8,pixel=ivec2(gl_FragCoord.xy);
    ivec3 cell=fogAtlasCell(pixel,size);
    vec4 current=texelFetch(FogRawVolume,pixel,0),history=current;
    vec2 uv=(vec2(cell.xy)+.5)/vec2(tileSize);
    float distance=(fogSliceDistance(float(cell.z))+fogSliceDistance(float(cell.z+1)))*.5;
    vec3 p=fogRay(uv,InvViewProjection)*distance+FogCameraDeltaFrame.xyz;
    vec4 clip=FogPreviousViewProjection*vec4(p,1);
    vec2 oldUV=clip.xy/max(clip.w,.00001)*.5+.5;
    float reuse=0.0;
    if(FogSettings.z < .5 && clip.w > 0 && length(p) < FOG_RANGE &&
        all(greaterThan(oldUV,vec2(0))) && all(lessThan(oldUV,vec2(1)))) {
        history=fogCoefficientSample(FogPreviousVolume,oldUV,length(p));
        float densityChange=abs(history.a-current.a)/max(current.a,.00015);
        float lightingChange=length(history.rgb-current.rgb)/max(length(current.rgb),.00008);
        reuse=pow(.82,clamp(PostSettings.x,.001,.1)*30.0)*FogReaction.x*
            (1.0-smoothstep(.08,.35,densityChange))*(1.0-smoothstep(.15,.65,lightingChange));
        // Tight local bounds prevent old shafts surviving a new occluder.
        vec4 low=current,high=current;
        for(int axis=0;axis<2;axis++) for(int sign=-1;sign<=1;sign+=2) {
            ivec2 offset=axis == 0 ? ivec2(sign,0) : ivec2(0,sign);
            ivec2 local=clamp(cell.xy+offset,ivec2(0),tileSize-1);
            ivec2 at=pixel-cell.xy+local;
            vec4 v=texelFetch(FogRawVolume,at,0);low=min(low,v);high=max(high,v);
        }
        history=clamp(history,low,high);
    }
#ifdef FOG_VOLUME_DEBUG
    fragColor=vec4(clamp(current.a*80.0,0.0,1.0),reuse,clamp(distance/FOG_RANGE,0.0,1.0),1);
#else
    fragColor=max(mix(current,history,reuse),vec4(0));
#endif
}
