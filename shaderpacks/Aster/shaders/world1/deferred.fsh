#version 330 compatibility
#define ASTER_END
#define ASTER_FRAGMENT

#include "/lib/common.glsl"
#include "/lib/surface.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: 0 */
void main(){
    float depth=texture(depthtex0,texcoord).r;
    if(depth>=.99999){gl_FragData[0]=vec4(SceneInfo.w>.5?asterSkyRadiance(worldRay(texcoord)):displayToLinear(fogColor),1);return;}
    vec4 encoded=texture(colortex1,texcoord),material=texture(colortex2,texcoord),base=texture(colortex3,texcoord);
    // Iris draws opaque hands before deferred lighting, with a compressed
    // depth range. Their forward lighting is already complete.
    if(encoded.a>2.5){gl_FragData[0]=vec4(texture(colortex0,texcoord).rgb,1);return;}
    vec3 n=normalize(encoded.xyz*2.0-1.0);
    vec3 p=relativePosition(texcoord,depth);
    vec4 properties=vec4(material.xy,texture(colortex4,texcoord).a,material.z);
    float ao=clamp(texture(colortex0,texcoord).a,.20,1.0);
    vec3 color=surfaceLight(base.rgb,n,p,properties,vec2(base.a,material.a),texture(colortex14,texcoord).rgb,ao);
    gl_FragData[0]=vec4(color,1);
}
