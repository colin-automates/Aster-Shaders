#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:fog_temporal.glsl>
#include <aster_vulkan:fog_volume.glsl>
uniform sampler2D FogScanInput;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    ivec2 size=textureSize(FogScanInput,0),tileSize=size/8,pixel=ivec2(gl_FragCoord.xy);
    ivec3 cell=fogAtlasCell(pixel,size);
    vec4 value=texelFetch(FogScanInput,pixel,0);
    int stride=int(FogReaction.y);
    if(stride == 0) {
        float length=fogSliceDistance(float(cell.z+1))-fogSliceDistance(float(cell.z));
        float t=exp(-max(value.a,0.0)*length);
        fragColor=vec4(value.rgb*(1.0-t)/max(value.a,.000001),max(value.a,0.0)*length);
    } else if(cell.z >= stride) {
        int z=cell.z-stride;
        vec4 front=texelFetch(FogScanInput,cell.xy+ivec2(z%8,z/8)*tileSize,0);
        fragColor=vec4(front.rgb+exp(-max(front.a,0.0))*value.rgb,front.a+value.a);
    } else fragColor=value;
}
