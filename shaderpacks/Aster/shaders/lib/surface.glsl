#ifndef ASTER_SURFACE
#define ASTER_SURFACE
#include "/lib/shadow.glsl"
// Inverse refracted wave mapping reuses Aster's actual wave/ripple field.
float causticGain(vec3 p,float depth,float footprint) {
#ifdef WATER_CAUSTICS
    if(depth<.05||SkySun.y<.02)return 1.0;
    vec2 receiver=p.xz+cameraPosition.xz;
    vec3 flatRay=refract(-SkySun.xyz,vec3(0,1,0),1.0/1.333);
    vec2 source=receiver-flatRay.xz/max(-flatRay.y,.2)*depth;
    vec2 center=floor(cameraPosition.xz/16.0)*16.0;
    vec2 uv=(source-center)/128.0+.5;
    vec4 derivatives;
    if(all(greaterThan(uv,vec2(.02)))&&all(lessThan(uv,vec2(.98))))
        derivatives=texture(colortex11,uv);
    else {
        uv=(source-center)/512.0+.5;
        if(any(lessThan(uv,vec2(.01)))||any(greaterThan(uv,vec2(.99))))return 1.0;
        derivatives=texture(colortex12,uv);
    }
    vec2 sx=derivatives.xy,sz=derivatives.zw;
    float refraction=.25*depth;
    float determinant=(1.0-refraction*sx.x)*(1.0-refraction*sz.y)-refraction*refraction*sx.y*sz.x;
    float gain=clamp(1.0/max(abs(determinant),.20),.30,4.0);
    return mix(1.0,gain,exp(-footprint*10.0)*exp(-depth*.04));
#else
    return 1.0;
#endif
}
vec3 surfaceLight(vec3 albedo,vec3 n,vec3 p,vec4 material,vec2 lm,vec3 nativeLight,float ao) {
    float leaf;
    vec3 visibility=shadowVisibility(p,n,leaf)*cloudVisibilityIris(p);
    float wet=wetness*lm.y*lm.y*smoothstep(.55,.90,n.y)*(1.0-step(.01,material.w));
    material.x=mix(material.x,min(material.x,.19),wet*.55);
    albedo*=1.0-wet*.13;
    vec3 view=normalize(-p);
    float depth=waterDepthAlongSun(p);
    vec3 extinction=exp(-vec3(.12,.065,.025)*depth/max(SkySun.y,.2));
    vec3 bakedLight=displayToLinear(nativeLight);
    vec3 ambient=terrainAmbient(bakedLight,n,lm,extinction)*ao;
    vec3 f0=mix(vec3(.04),albedo,material.y);
    vec3 color=albedo*ambient*(1.0-material.y)+f0*ambient*mix(.08,.50,material.y);
    vec3 brdf=material.w>.01?leafBrdf(albedo,material.x,n,view,SunDirection.xyz,material.w):directBrdf(albedo,material.x,material.y,n,view,SunDirection.xyz);
    float footprint=max(length(dFdx(p)),length(dFdy(p)));
    color+=brdf*terrainSunRadiance()*SunDirection.w*lm.y*visibility*extinction*causticGain(p,depth,footprint);
    color+=materialEnvironment(albedo,material.x,material.y,n,view,lm.y)*extinction;
    color+=albedo/ASTER_PI*terrainSunRadiance()*SunDirection.w*leaf*smoothstep(.65,.95,n.y)*lm.y*.20;
    color+=albedo*material.z*8.0;
    float held=float(max(heldBlockLightValue,heldBlockLightValue2))/15.0;
    color+=albedo*vec3(1.0,.65,.32)*held*held*max(dot(n,view),0.0)/(1.0+dot(p,p)*.12);
    return color;
}
#endif
