#ifndef ASTER_ORIGINAL_PBR
#define ASTER_ORIGINAL_PBR
const float ASTER_PI=3.14159265359;
vec3 fresnelSchlick(float cosine,vec3 f0) { return f0+(1.0-f0)*pow(1.0-cosine,5.0); }
vec3 directBrdf(vec3 albedo,float roughness,float metalness,vec3 n,vec3 v,vec3 l) {
    float nv=dot(n,v),nl=dot(n,l);
    if(nv <= 0.0 || nl <= 0.0) return vec3(0);
    nv=max(nv,0.001);
    vec3 h=normalize(v+l);
    float nh=max(dot(n,h),0.0),vh=max(dot(v,h),0.0);
    float alpha=max(roughness*roughness,0.002),a2=alpha*alpha;
    float denominator=nh*nh*(a2-1.0)+1.0;
    float distribution=a2/(ASTER_PI*denominator*denominator);
    // Smith GGX masking/shadowing, with perceptual roughness squared once.
    float gv=2.0*nv/(nv+sqrt(a2+(1.0-a2)*nv*nv));
    float gl=2.0*nl/(nl+sqrt(a2+(1.0-a2)*nl*nl));
    vec3 f=fresnelSchlick(vh,mix(vec3(0.04),albedo,metalness));
    vec3 specular=distribution*gv*gl*f/(4.0*nv*nl);
    vec3 diffuse=(1.0-f)*(1.0-metalness)*albedo/ASTER_PI;
    return (diffuse+specular)*nl;
}
vec3 leafBrdf(vec3 albedo,float roughness,vec3 n,vec3 v,vec3 l,float transmission) {
    float nl=dot(n,l);
    float sheet=clamp(transmission*1.65,0.0,.92);
    vec3 front=directBrdf(albedo,max(roughness,.85),0.0,n,v,l)*(1.0-sheet);
    float back=clamp((.2-nl)/1.2,0.0,1.0);
    float forward=pow(max(dot(-v,l),0.0),4.0);
    // A leaf block represents many differently oriented thin leaves. Spread
    // their illumination over the bundle instead of displaying six hard
    // Lambertian faces. The shadow map still describes canopy occlusion.
    // Keep the bundle softly wrapped, but let sun-facing leaves and the
    // backlit silhouette separate from its shaded interior. Transmission
    // receives the same canopy/cloud visibility as reflected sunlight.
    float bundle=.50+.40*smoothstep(-.35,.85,nl);
    float transmitted=back*(.22+1.35*forward)+.12*forward;
    // A shorter optical path through thin leaves absorbs less light than
    // their opaque reflected colour. This reveals warm light at exposed
    // edges without adding it to shadowed leaves or altering biome tint.
    vec3 thinColor=mix(albedo,sqrt(max(albedo,vec3(0))),.45);
    return front+thinColor/ASTER_PI*sheet*(bundle+transmitted);
}

#endif
