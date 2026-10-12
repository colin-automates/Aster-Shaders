#include "/lib/common.glsl"
#include "/lib/original/foliage.glsl"
attribute vec4 mc_Entity;
attribute vec4 at_midBlock;
attribute vec4 at_tangent;
attribute vec2 mc_midTexCoord;
varying vec2 texcoord,lmcoord;
varying vec4 tint;
varying vec3 playerPos,worldNormal;
varying vec3 previousOffset;
flat varying int blockId;
void main() {
    texcoord=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;
    lmcoord=clamp((gl_TextureMatrix[1]*gl_MultiTexCoord1).xy,vec2(0),vec2(1));
    tint=gl_Color;
    vec4 view=gl_ModelViewMatrix*gl_Vertex;
    vec3 p=(gbufferModelViewInverse*view).xyz;
    worldNormal=normalize(mat3(gbufferModelViewInverse)*gl_NormalMatrix*gl_Normal);
    blockId=int(mc_Entity.x+.5);previousOffset=vec3(0);
#if defined(ASTER_ENTITY) || defined(ASTER_HAND) || defined(ASTER_PARTICLE)
    blockId=0;
#endif
    if(blockId>=2&&blockId<=5) {
        float modelHeight=clamp(.5-at_midBlock.y/64.0,0,1);
        float height=blockId==2?1.0:blockId==4?modelHeight:modelHeight+(blockId==5?1.0:0.0);
        vec3 offset=foliageWindOffset(p+cameraPosition,height,blockId==2,CameraWorldTime.w,rainStrength*.5);
        previousOffset=foliageWindOffset(p+cameraPosition,height,blockId==2,CameraWorldTime.w-frameTime,rainStrength*.5)-offset;
        p+=offset;
    }
    if(blockId==1) {
        float top=smoothstep(.15,.85,fract(p.y+cameraPosition.y+.001));
        // Side/bottom vertices remain fixed; vertex footprint filtering is
        // shared by the fragment normal. Bank adjacency requires validation.
        float height=waterWaveField((p+cameraPosition).xz,CameraWorldTime.w,rainStrength*.5).x*WAVE_HEIGHT*top;
        p.y+=height;
    }
    playerPos=p;
    gl_Position=gl_ProjectionMatrix*gbufferModelView*vec4(p,1);
#ifdef ASTER_HAND
    gl_Position=gl_ProjectionMatrix*gl_ModelViewMatrix*gl_Vertex;
#else
    gl_Position.xy+=frameJitter(frameCounter)*2.0*gl_Position.w;
#endif
}
