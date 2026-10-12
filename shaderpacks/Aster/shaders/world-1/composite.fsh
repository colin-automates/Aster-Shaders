#version 330 compatibility
#define ASTER_NETHER
#define ASTER_FRAGMENT

#include "/lib/common.glsl"
#include "/lib/reflection.glsl"
#include "/lib/fog.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: 0 */
void main(){
    vec3 color=texture(colortex0,texcoord).rgb;
    float depth=texture(depthtex0,texcoord).r;
    if(depth<.99999){
        vec4 encoded=texture(colortex1,texcoord),material=texture(colortex2,texcoord);
        vec3 p=relativePosition(texcoord,depth),n=normalize(encoded.xyz*2.0-1.0);
        if(encoded.a<2.5&&(material.x<.70||material.y>.05)){
            vec4 reflection=screenReflection(p,n,material.x);
            vec3 fallback=encoded.a>.5&&encoded.a<1.5?waterSky(reflect(normalize(p),n)):materialSkyRadiance(material.x,n,normalize(-p));
            vec3 albedo=texture(colortex3,texcoord).rgb;
            vec3 weight=encoded.a>.5&&encoded.a<1.5?vec3(waterDielectricFresnel(max(dot(n,normalize(-p)),0),isEyeInWater==1)):materialReflectionWeight(albedo,material.x,material.y,n,normalize(-p))*material.a*material.a;
            color+=(reflection.rgb-fallback)*reflection.a*weight;
        }
        if(encoded.a<2.5)color=atmosphericFog(max(color,vec3(0)),p);
    }else if(isEyeInWater==1)color=atmosphericFog(color,worldRay(texcoord)*48.0);
    color*=1.0-clamp(blindness+darknessFactor*.75,0.0,1.0);
    gl_FragData[0]=vec4(max(color,vec3(0)),1);
}
