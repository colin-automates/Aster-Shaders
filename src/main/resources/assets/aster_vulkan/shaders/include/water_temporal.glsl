layout(std140) uniform WaterTemporal {
    mat4 CurrentViewProjection;
    mat4 PreviousViewProjection;
    mat4 PreviousInvViewProjection;
    vec4 CameraDeltaFrame;
    vec4 WaterSettings; // reflection resolution xy, reset z, temporal enabled w
};
vec3 waterPosition(vec2 uv,float depth,mat4 inverseProjection) {
    vec4 p=inverseProjection*vec4(uv*2.0-1.0,depth,1.0);
    return p.xyz/p.w;
}
