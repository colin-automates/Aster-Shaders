#ifndef ASTER_FOG_VOLUME
#define ASTER_FOG_VOLUME
// The atlas holds 64 camera-aligned depth slices. No surface endpoint is
// involved in its lighting, history, or reconstruction.
const int FOG_SLICES=64;
const float FOG_RANGE=160.0;
float fogSliceDistance(float boundary) {
    float t=clamp(boundary/float(FOG_SLICES),0.0,1.0);
    return FOG_RANGE*t*t;
}
ivec3 fogAtlasCell(ivec2 pixel,ivec2 atlasSize) {
    ivec2 tile=atlasSize/8;
    return ivec3(pixel%tile,(pixel.y/tile.y)*8+pixel.x/tile.x);
}
vec4 fogSliceSample(sampler2D atlas,vec2 uv,int slice) {
    if(slice < 0) return vec4(0);
    vec2 tileSize=vec2(textureSize(atlas,0)/8);
    vec2 local=clamp(uv*tileSize,vec2(.5),tileSize-vec2(.5));
    ivec2 tile=ivec2(slice%8,slice/8);
    return texture(atlas,(vec2(tile)+local/tileSize)/8.0);
}
vec4 fogCoefficientSample(sampler2D atlas,vec2 uv,float distance) {
    float z=clamp(sqrt(max(clamp(distance/FOG_RANGE,0.0,1.0)*4096.0-.25,0.0))-.5,0.0,63.0);
    int lo=int(floor(z));
    return mix(fogSliceSample(atlas,uv,lo),fogSliceSample(atlas,uv,min(lo+1,63)),fract(z));
}
vec4 fogAtDepth(sampler2D atlas,vec2 uv,float distance) {
    if(distance <= 0.0) return vec4(0,0,0,1);
    if(distance >= FOG_RANGE) {
        vec4 value=fogSliceSample(atlas,uv,63);
        return vec4(value.rgb,exp(-max(value.a,0.0)));
    }
    int slice=min(63,int(sqrt(distance/FOG_RANGE)*64.0));
    vec4 a=fogSliceSample(atlas,uv,slice-1),b=fogSliceSample(atlas,uv,slice);
    float fraction=clamp((distance-fogSliceDistance(float(slice)))/
        max(fogSliceDistance(float(slice+1))-fogSliceDistance(float(slice)),.00001),0.0,1.0);
    // Alpha stores optical depth. Adding optical depth avoids losing weak
    // near-camera extinction when half-float transmittance rounds to one.
    float segmentT=exp(-max(b.a-a.a,0.0));
    float partialT=pow(segmentT,fraction);
    float share=segmentT < .99999 ? (1.0-partialT)/(1.0-segmentT) : fraction;
    return vec4(a.rgb+max(b.rgb-a.rgb,vec3(0))*share,exp(-max(a.a,0.0))*partialT);
}
#endif
