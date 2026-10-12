#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#include <aster_vulkan:solid_temporal.glsl>
uniform sampler2D SolidScene;
uniform sampler2D SolidGeometry;
uniform sampler2D SolidResponse;
uniform sampler2D SolidDepth;
uniform sampler2D SolidResolved;
uniform sampler2D SolidResolvedGeometry;
uniform sampler2D WaterSurface;
uniform sampler2D WaterDepth;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec3 color=texture(SolidScene,texCoord).rgb;
    vec4 geometry=texture(SolidGeometry,texCoord);
    float depth=texture(SolidDepth,texCoord).r;
    if(dot(geometry.xyz,geometry.xyz)>.5 && depth>.000001
        && !(texture(WaterSurface,texCoord).a>.5 && texture(WaterDepth,texCoord).r>=depth)) {
        float distance=length(solidPosition(texCoord,depth));
        vec2 grid=texCoord*SolidSizeFrame.xy-.5,blend=fract(grid);
        ivec2 base=ivec2(floor(grid));
        vec3 sum=vec3(0);float total=0.0;
        for(int y=0;y<=1;y++)for(int x=0;x<=1;x++) {
            ivec2 pixel=clamp(base+ivec2(x,y),ivec2(0),ivec2(SolidSizeFrame.xy)-1);
            vec4 sampleColor=texelFetch(SolidResolved,pixel,0),sampleGeometry=texelFetch(SolidResolvedGeometry,pixel,0);
            float error=abs(sampleColor.a-distance),limit=.05+distance*.008;
            if(error>limit*2.0 || dot(geometry.xyz,sampleGeometry.xyz)<.95 || abs(geometry.w-sampleGeometry.w)>.06)continue;
            float w=(x==0?1.0-blend.x:blend.x)*(y==0?1.0-blend.y:blend.y)*exp(-error/limit);
            sum+=sampleColor.rgb*w;total+=w;
        }
        if(total>.001)color+=sum/total*texture(SolidResponse,texCoord).rgb;
    }
    fragColor=vec4(max(color,vec3(0)),1);
}
