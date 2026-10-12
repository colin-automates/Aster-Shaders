#ifndef ASTER_CLOUD_SHADOW_GLSL
#define ASTER_CLOUD_SHADOW_GLSL
uniform sampler2D CloudShadow;
float cloudVisibility(vec3 relativePosition) {
    if(SceneInfo.w<.5 || AsterSettings.x<.5 || SkySun.y<.08 || verificationFlag(2)
        || verificationFlag(134217728) || verificationFlag(2097152))return 1.0;
    // The map integrates the sky's density field above the camera's height.
    // Translate elevated receivers along the light ray onto that plane.
    vec2 projected=relativePosition.xz-SkySun.xz*(relativePosition.y/max(SkySun.y,.08));
    vec2 uv=projected/768.0+.5;
    float edge=max(abs(uv.x-.5),abs(uv.y-.5));
    float transmittance=texture(CloudShadow,clamp(uv,0.0,1.0)).r;
    float minimum=mix(.72,.55,smoothstep(.20,.55,SkySun.y));
    float attenuation=mix(1.0,minimum+(1.0-minimum)*transmittance,smoothstep(.08,.20,SkySun.y));
    return mix(attenuation,1.0,smoothstep(.44,.50,edge));
}
#endif
