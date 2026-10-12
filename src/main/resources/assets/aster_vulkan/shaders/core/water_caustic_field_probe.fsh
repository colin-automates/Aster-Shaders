#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water_caustic_source.glsl>
layout(std140) uniform CausticParameters { vec4 CausticRender; };
uniform sampler2D CausticField;
layout(location=0) out vec4 fragColor;
void main() {
    int sampleIndex=int(floor(gl_FragCoord.x)),grid=int(CausticRender.x);
    ivec2 node=ivec2((sampleIndex*83)%(grid+1),(sampleIndex*317)%(grid+1));
    if(sampleIndex<4) node=ivec2((sampleIndex%2)*grid,(sampleIndex/2)*grid);
    bool far=CausticRender.w>=2.0;
    vec3 direct=causticSourceField(causticSourcePosition(node,CausticRender.x,far),CausticRender.z,far);
    vec4 cached=texelFetch(CausticField,node,0);
    vec3 error=abs(cached.xyz-direct);
    bool finite=all(lessThan(abs(cached.xyz),vec3(100)))&&cached.w==1.0;
    fragColor=vec4(error.x<=.00001?1:0,max(error.y,error.z)<=.00001?1:0,finite?1:0,1);
}
