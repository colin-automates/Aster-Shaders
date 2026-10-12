#ifndef ASTER_WATER_REFRACTION_GLSL
#define ASTER_WATER_REFRACTION_GLSL
vec2 waterRefractionOffset(vec2 uv,vec2 offset,vec2 viewport,bool previous) {
    if(previous) {
        float edge=min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y));
        return offset*smoothstep(.005,.05,edge);
    }
    // Each component only approaches the border toward which it travels.
    // Fading both components at the nearest border curls parallel features
    // across a fixed viewport band, even when their samples stay on screen.
    vec2 padding=.5/viewport;
    vec2 margin=mix(uv-padding,1.0-padding-uv,greaterThanEqual(offset,vec2(0)));
    vec2 width=max(2.0*abs(offset),1.0/viewport);
    // Twice the displacement bounds the maximum warp slope below one.
    // Samples stay within the image and constant-offset rays cannot fold.
    return offset*smoothstep(vec2(0),width,max(margin,vec2(0)));
}
#endif
