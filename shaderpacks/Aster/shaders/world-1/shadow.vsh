#version 330 compatibility
#define ASTER_NETHER
#include "/lib/common.glsl"
#include "/lib/original/foliage.glsl"
attribute vec4 mc_Entity;
attribute vec4 at_midBlock;
varying vec2 texcoord;
varying vec4 tint;
varying vec3 playerPos;
flat varying int blockId;
void main(){
    texcoord=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;tint=gl_Color;
    blockId=int(mc_Entity.x+.5);
    vec3 p=(shadowModelViewInverse*gl_ModelViewMatrix*gl_Vertex).xyz;
    if(blockId>=2&&blockId<=5){
        float height=clamp(.5-at_midBlock.y/64.0,0,1)+(blockId==5?1.0:0.0);
        p+=foliageWindOffset(p+cameraPosition,height,blockId==2,CameraWorldTime.w,rainStrength*.5);
    }
    if(blockId==1)p.y+=waterWaveField((p+cameraPosition).xz,CameraWorldTime.w,rainStrength*.5).x*WAVE_HEIGHT*smoothstep(.15,.85,fract(p.y+cameraPosition.y+.001));
    playerPos=p;
    gl_Position=shadowProjection*shadowModelView*vec4(p,1);
    gl_Position.xy/=.18+.82*length(gl_Position.xy);
    gl_Position.z*=.25;
}
