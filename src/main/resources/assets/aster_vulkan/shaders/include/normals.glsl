// UV-gradient tangent frame preserves mirrored sprite UVs without adding a
// tangent attribute or rebuilding Minecraft's native chunk vertex format.
vec3 mappedTerrainNormal(vec3 geometric,vec3 dpdx,vec3 dpdy,vec2 duvdx,vec2 duvdy,vec4 encoded) {
    float determinant=duvdx.x*duvdy.y-duvdx.y*duvdy.x;
    if(encoded.a <= 0.0 || abs(determinant) < 1e-16) return geometric;
    vec3 t=(dpdx*duvdy.y-dpdy*duvdx.y)/determinant;
    vec3 b=(dpdy*duvdx.x-dpdx*duvdy.x)/determinant;
    t-=geometric*dot(geometric,t);
    if(dot(t,t) < 1e-12) return geometric;
    t=normalize(t);
    float handedness=dot(cross(geometric,t),b) < 0.0 ? -1.0 : 1.0;
    b=cross(geometric,t)*handedness;
    vec3 tangent=encoded.rgb*2.0-1.0;
    tangent.xy*=encoded.a*4.0;
    if(dot(tangent,tangent) < 1e-6) return geometric;
    return normalize(mat3(t,b,geometric)*normalize(tangent));
}
