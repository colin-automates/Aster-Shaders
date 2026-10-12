layout(std140) uniform FogTemporal {
    mat4 FogCurrentViewProjection;
    mat4 FogPreviousViewProjection;
    mat4 FogPreviousInvViewProjection;
    vec4 FogCameraDeltaFrame;
    vec4 FogSettings; // volume grid (legacy half-res) xy, reset z, range w
    vec4 FogReaction; // history attenuation x, integration scan stride y
    vec4 FogEnvironmentOrigin; // world cache center xz, bottom y
};
