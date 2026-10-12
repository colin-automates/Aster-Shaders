#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform CausticParameters { vec4 CausticRender; };
uniform sampler2D CausticInput;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    // The beam width belongs to world space. One far-map texel spans four
    // times the near-map width; blurring by whole texels erased far contrast.
    // Raw supersampling/reduction still integrates each receiver pixel before
    // this beam filter. Bilinear taps cannot restore unresolved frequencies.
    float extent=CausticRender.w>=2.0 ? 256.0:64.0;
    float beamStep=.04/extent;
    vec2 stepUV=CausticRender.y>.5 ? vec2(0,beamStep):vec2(beamStep,0);
    // Each channel is a different receiver depth (.5, 1.5, 3.5, 5.5 blocks).
    // Retain pixel-integrated shallow detail instead of giving it the deep
    // layer's beam width. Variance grows with squared travel distance. A small
    // baseline support still softens raster folds in the shallowest layer.
    // Both kernels and their convex mixture sum to one, preserving mean flux.
    vec4 center=texture(CausticInput,texCoord);
    vec4 beam=center*.375
        +(texture(CausticInput,texCoord-stepUV)+texture(CausticInput,texCoord+stepUV))*.25
        +(texture(CausticInput,texCoord-stepUV*2.0)+texture(CausticInput,texCoord+stepUV*2.0))*.0625;
    vec4 depth=vec4(.5,1.5,3.5,5.5);
#ifdef CAUSTIC_SHALLOW
    depth=vec4(CausticRender.z);
#endif
    vec4 support=.18+.82*(depth*depth)/(5.5*5.5);
    fragColor=mix(center,beam,support);
}
