layout(std140) uniform SolidTemporal {
    mat4 SolidCurrentProjection;
    mat4 SolidPreviousProjection;
    vec4 SolidCameraDeltaReset;
    vec4 SolidSizeFrame;
};
vec3 solidPosition(vec2 uv,float depth) {
    vec4 p=InvViewProjection*vec4(uv*2.0-1.0,depth,1);
    return p.xyz/p.w;
}
