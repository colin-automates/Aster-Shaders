#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:water_temporal.glsl>
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 surface=texture(WaterSurface,texCoord);
    if(surface.a < .5) {fragColor=vec4(0,0,0,1);return;}
    vec3 p=waterPosition(texCoord,texture(WaterDepth,texCoord).r,InvViewProjection);
    // This isolated fixture has a source-water plane at 64+8/9-.001.
    // Verify the actual native replay depth, not the displacement function.
    float error=abs(p.y+CameraWorldTime.y-(64.0+8.0/9.0-.001));
    float anchored=surface.a < 1.02 && abs(surface.y) > .85 ? 1.0 : 0.0;
    fragColor=vec4(anchored,error < .004 ? 1.0 : 0.0,1,1);
}
