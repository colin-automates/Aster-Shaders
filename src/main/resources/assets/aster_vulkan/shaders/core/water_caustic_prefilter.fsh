#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D CausticInput;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 pixel=1.0/vec2(textureSize(CausticInput,0));
    vec4 sum=vec4(0);
    // Accumulate at twice the receiver resolution before reducing. Bilinear
    // fetches and the normalized kernel integrate a finite pixel footprint,
    // rather than letting one centre sample dominate a narrow folded patch.
    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) {
        float weight=(x==0?2.0:1.0)*(y==0?2.0:1.0)/16.0;
        sum+=texture(CausticInput,texCoord+vec2(x,y)*pixel)*weight;
    }
    fragColor=sum;
}
