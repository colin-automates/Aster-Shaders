#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:color.glsl>
uniform sampler2D HdrInput;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() { fragColor=vec4(linearToDisplay(texture(HdrInput,texCoord).rgb),1); }
