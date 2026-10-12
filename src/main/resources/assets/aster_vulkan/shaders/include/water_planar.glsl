layout(std140) uniform WaterPlanar {
    mat4 PlanarViewProjection;
    mat4 PlanarInvViewProjection;
    vec4 PlanarSettings;
    vec4 PlanarRange; // horizontal fade start/end, ray guard, hidden legacy comparison
};
