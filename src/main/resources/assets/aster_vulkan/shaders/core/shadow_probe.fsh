#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
uniform sampler2D Shadow0;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    // Known fixture: a column at x=6.5,z=-4.5 reaches y=77.
    // Follow the ground away from the light; its ray crosses that column at y=73.
    vec3 world = vec3(6.5,65.03,texCoord.x < 0.5 ? -4.5 : 20.0);
    world.xz-=SunDirection.xz*8.0/SunDirection.y;
    vec4 clip = ShadowMatrices[1]*vec4(world-CameraWorldTime.xyz,1);
    vec3 q = clip.xyz/clip.w;
    float occluder = texture(Shadow0,q.xy*0.5+0.5).r;
    float lit = q.z+0.00015 >= occluder ? 1.0 : 0.0;
    fragColor = vec4(vec3(lit),1);
}
