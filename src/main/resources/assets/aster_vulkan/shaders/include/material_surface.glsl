// Shared by forward lighting and the EQUAL-depth reflection replay.
float materialFilteredRoughness(float roughness,vec4 normalSample) {
    float variance=normalSample.a>0.0?max(0.0,1.0-length(normalSample.rgb*2.0-1.0)):0.0;
    variance*=normalSample.a*normalSample.a*16.0;
    return min(1.0,sqrt(max(roughness,.045)*max(roughness,.045)+2.0*variance));
}
void materialRainSurface(inout vec3 albedo,inout vec3 material,vec3 p,vec3 geometry,float skyAccess,float transmission) {
    float wetness=materialWetness(CameraWorldTime.xyz+p,geometry,skyAccess,transmission);
    float puddle=materialPuddle(CameraWorldTime.xyz+p,wetness)*(1.0-material.g);
    albedo*=mix(1.0,.88,wetness)*mix(1.0,.90,puddle);
    material.r=mix(material.r,max(.12,material.r*.65),wetness*(1.0-material.g));
    material.r=mix(material.r,.075,puddle);
}
