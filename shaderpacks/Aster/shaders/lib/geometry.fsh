#include "/lib/common.glsl"
#include "/lib/material.glsl"
#include "/lib/surface.glsl"
uniform sampler2D gtexture,normals,specular,lightmap;
uniform float alphaTestRef;
uniform vec4 entityColor;
varying vec2 texcoord,lmcoord;
varying vec4 tint;
varying vec3 playerPos,worldNormal,previousOffset;
flat varying int blockId;
#ifdef ASTER_FORWARD
/* RENDERTARGETS: 0,1,2,3,4,14 */
#else
/* RENDERTARGETS: 0,1,2,3,4,14 */
#endif
void main() {
    vec4 texel=texture2D(gtexture,texcoord);
    if(texel.a<max(alphaTestRef,.10))discard;
    vec3 albedo=displayToLinear(texel.rgb*tint.rgb);
#ifdef ASTER_ENTITY
    albedo=mix(albedo,displayToLinear(entityColor.rgb),entityColor.a);
#endif
    vec3 n=normalize(worldNormal);
    if(!gl_FrontFacing)n=-n;
    vec4 material=blockMaterial(blockId);
#ifdef ASTER_EMISSIVE
    material.z=max(material.z,4.0);
#endif
    vec4 spec=texture2D(specular,texcoord);
#ifdef MC_TEXTURE_FORMAT_LAB_PBR
    material.x=max(.045,(1.0-spec.r)*(1.0-spec.r));
    material.y=spec.g>.90?1.0:0.0;
    material.z=max(material.z,spec.a<.995?spec.a*4.0:0.0);
#endif
#ifdef NORMAL_MAPS
#ifndef ASTER_PARTICLE
    vec3 normalMap=texture2D(normals,texcoord).xyz;
    if(normalMap.b>.05 && dot(normalMap.xy*2.0-1.0,normalMap.xy*2.0-1.0)<=1.02) {
        vec3 dpdx=dFdx(playerPos),dpdy=dFdy(playerPos);
        vec2 du=dFdx(texcoord),dv=dFdy(texcoord);
        float determinant=du.x*dv.y-du.y*dv.x;
        if(abs(determinant)>1e-10) {
            vec3 tangent=normalize((dpdx*dv.y-dpdy*du.y)/determinant);
            tangent=normalize(tangent-n*dot(n,tangent));
            vec3 bitangent=normalize(cross(n,tangent))*sign(determinant);
            vec2 xy=normalMap.xy*2.0-1.0;
            n=normalize(mat3(tangent,bitangent,n)*vec3(xy,sqrt(max(1.0-dot(xy,xy),.01))));
        }
    }
#endif
#endif
    float type=blockId==1?1.0:material.w>.01?2.0:0.0;
#ifdef ASTER_HAND
    type=3.0;
#endif
    vec3 color=albedo;
    float alpha=texel.a*clamp(tint.a,0.0,1.0);
#ifndef ASTER_FORWARD
    // Accepted cutout texels are opaque. Only vertex AO belongs in this
    // attachment; mipmapped texture coverage must not dim distant foliage.
    alpha=clamp(tint.a,0.0,1.0);
#endif
#ifdef ASTER_FORWARD
    color=surfaceLight(albedo,n,playerPos,material,lmcoord,texture(lightmap,lmcoord).rgb,1.0);
#ifdef ASTER_PARTICLE
    color=albedo*displayToLinear(texture(lightmap,lmcoord).rgb);
#endif
    if(blockId==1) {
        float footprint=max(length(dFdx(playerPos)),length(dFdy(playerPos)));
        n=waterShadingNormal(playerPos,n,footprint,1.0);
        vec2 uv=gl_FragCoord.xy/vec2(viewWidth,viewHeight);
        vec2 offset=n.xz*.012/clamp(length(playerPos)*.025,1.0,4.0);
        vec2 refractUV=clamp(uv+offset,vec2(.001),vec2(.999));
        float depth=texture(depthtex1,refractUV).r;
        vec3 behind=relativePosition(refractUV,depth);
        if(depth<1.0&&length(behind)<length(playerPos)-.10){refractUV=uv;depth=texture(depthtex1,uv).r;behind=relativePosition(uv,depth);}
        float thickness=depth<.99999?clamp(length(behind-playerPos),0.0,48.0):48.0;
        vec3 attenuation=isEyeInWater==1?vec3(1):waterAbsorption(thickness,displayToLinear(tint.rgb));
        vec3 transmitted=texture(colortex5,refractUV).rgb*attenuation+waterBodyRadiance(displayToLinear(tint.rgb),lmcoord.y)*(1.0-attenuation);
        float cosine=max(dot(n,normalize(-playerPos)),0.0);
        float fresnel=waterDielectricFresnel(cosine,isEyeInWater==1);
        vec3 sky=waterSky(reflect(normalize(playerPos),n));
        float leaves;vec3 visibility=shadowVisibility(playerPos,vec3(0,1,0),leaves);
        color=mix(transmitted,sky,fresnel)+directBrdf(vec3(.02),max(.08,.10+footprint*.015),0,n,normalize(-playerPos),SunDirection.xyz)*terrainSunRadiance()*SunDirection.w*visibility*lmcoord.y;
        alpha=1.0;
        material=vec4(.10,0,0,0);
    }
#endif
    gl_FragData[0]=vec4(color,alpha);
    gl_FragData[1]=vec4(n*.5+.5,type);
    gl_FragData[2]=vec4(material.xy,material.w,lmcoord.y);
    gl_FragData[3]=vec4(albedo,lmcoord.x);
    gl_FragData[4]=vec4(previousOffset,material.z);
    gl_FragData[5]=vec4(texture(lightmap,lmcoord).rgb,1.0);
}
