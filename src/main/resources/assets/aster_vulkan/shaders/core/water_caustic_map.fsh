#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform CausticParameters { vec4 CausticRender; };
layout(location=0) in vec2 sourcePosition;
layout(location=1) flat in int depthLayer;
layout(location=0) out vec4 fragColor;
void main() {
    // Source area carried by one receiver texel. Folded surface triangles
    // accumulate independently; a single inverse-map root misses this light.
    vec2 dx=dFdx(sourcePosition),dy=dFdy(sourcePosition);
    float extent=CausticRender.w>=2.0?256.0:64.0;
    float pixel=extent/CausticRender.y;
    float density=min(abs(dx.x*dy.y-dx.y*dy.x)/(pixel*pixel),32.0);
    fragColor=vec4(depthLayer==0 ? density:0.0,depthLayer==1 ? density:0.0,
                   depthLayer==2 ? density:0.0,depthLayer==3 ? density:0.0);
}
