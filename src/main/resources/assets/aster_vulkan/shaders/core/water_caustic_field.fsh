#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:color.glsl>
#include <aster_vulkan:water_caustic_source.glsl>
layout(std140) uniform CausticParameters { vec4 CausticRender; };
layout(location=0) out vec4 fragColor;
void main() {
    bool far=CausticRender.w>=2.0;
    ivec2 node=ivec2(floor(gl_FragCoord.xy));
    vec2 position=causticSourcePosition(node,CausticRender.x,far);
    vec3 field=mod(CausticRender.w,2.0)>.5?vec3(0):causticSourceField(position,CausticRender.z,far);
    fragColor=vec4(field,1);
}
