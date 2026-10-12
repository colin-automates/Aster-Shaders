#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water_caustic_source.glsl>
layout(std140) uniform CausticParameters { vec4 CausticRender; };
uniform sampler2D CausticField;
layout(location=0) out vec2 sourcePosition;
layout(location=1) flat out int depthLayer;
void main() {
    int grid=int(CausticRender.x);
    // A single 128-square UINT16 tile is shared by both source resolutions.
    // Depth and tile addresses come from the instance; adjoining tiles fetch
    // identical cached boundary nodes, retaining a connected ray map.
    int tiles=grid/128,tileAddress=gl_InstanceIndex%(tiles*tiles);
    ivec2 node=ivec2(gl_VertexIndex%129,gl_VertexIndex/129)
        +ivec2(tileAddress%tiles,tileAddress/tiles)*128;
    depthLayer=gl_InstanceIndex/(tiles*tiles);
    float depths[4]=float[4](.5,1.5,3.5,5.5);
#ifdef CAUSTIC_SHALLOW
    // Normal rendering supplies .875. Hidden diagnostics reuse this R16 path
    // at intermediate depths, without allocating another persistent atlas.
    float depth=CausticRender.z;
#else
    float depth=depths[depthLayer];
#endif
    bool far=CausticRender.w>=2.0;
    float extent=far?256.0:64.0;
    vec2 origin=WaterVolumeInfo.xz-(far?vec2(96):vec2(0));
    vec3 incident=-normalize(SkySun.xyz);
    sourcePosition=causticSourcePosition(node,float(grid),far);
    // Mesh density is independent of source-field precision. Shared cached
    // boundary nodes retain exact source values at each selected mesh vertex.
    int sourceGrid=textureSize(CausticField,0).x-1;
    ivec2 fieldNode=node*(sourceGrid/grid);
    vec3 field=texelFetch(CausticField,fieldNode,0).xyz;
    vec2 slope=field.yz;
    float height=field.x;
    if(mod(CausticRender.w,2.0)>.5) { slope=vec2(0);height=0.0; }
    vec3 ray=refract(incident,normalize(vec3(-slope.x,1,-slope.y)),1.0/1.333);
    vec2 receiver=sourcePosition+ray.xz/max(-ray.y,.2)*max(depth+height,.05);
    vec2 targetUV=(receiver-origin)/extent;
    gl_Position=vec4(targetUV*2.0-1.0,0,1);
}
