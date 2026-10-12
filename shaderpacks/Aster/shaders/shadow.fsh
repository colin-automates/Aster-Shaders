#version 330 compatibility
uniform sampler2D gtexture;
varying vec2 texcoord;
varying vec4 tint;
varying vec3 playerPos;
flat varying int blockId;
/* DRAWBUFFERS:01 */
void main(){
    vec4 texel=texture2D(gtexture,texcoord)*tint;
    if(texel.a<.10)discard;
    vec3 transmission=vec3(0);
    float type=0;
    if(blockId==1){transmission=vec3(1);type=1.0;}
    else if(blockId==2){transmission=mix(vec3(.12),sqrt(max(texel.rgb,vec3(0)))*.45,.75);type=.65;}
    else if(blockId==14){transmission=mix(vec3(1),texel.rgb,texel.a);type=.25;}
    gl_FragData[0]=vec4(transmission,type);
    gl_FragData[1]=vec4(playerPos.y,0,0,type);
}
