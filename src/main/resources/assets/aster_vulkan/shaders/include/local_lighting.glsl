#ifndef ASTER_LOCAL_LIGHTING_GLSL
#define ASTER_LOCAL_LIGHTING_GLSL
layout(std140) uniform LocalLighting {
    vec4 LocalOriginReady;
    vec4 HeldPosition;
    vec4 HeldMain;
    vec4 HeldOff;
    vec4 SourcePosition[32];
    vec4 SourceColor[32];
};
uniform sampler2D LocalLightVolume;
vec4 localCell(ivec3 cell) {
    if(any(lessThan(cell,ivec3(0)))||any(greaterThanEqual(cell,ivec3(48,40,48))))return vec4(0);
    return texelFetch(LocalLightVolume,ivec2(cell.x+(cell.y%8)*48,cell.z+(cell.y/8)*48),0);
}
vec4 localLightAt(vec3 world) {
    if(LocalOriginReady.w<.5)return vec4(0);
    vec3 p=world-LocalOriginReady.xyz-.5;
    ivec3 cell=ivec3(floor(p));vec3 blend=fract(p);
    vec3 sum=vec3(0);float weight=0.0;
    for(int y=0;y<2;y++)for(int z=0;z<2;z++)for(int x=0;x<2;x++){
        vec3 factor=mix(vec3(1)-blend,blend,vec3(x,y,z));
        vec4 tap=localCell(cell+ivec3(x,y,z));
        float w=factor.x*factor.y*factor.z*step(.75,tap.a);
        sum+=tap.rgb*w;weight+=w;
    }
    vec3 edge=min(world-LocalOriginReady.xyz,LocalOriginReady.xyz+vec3(48,40,48)-world);
    float fade=smoothstep(.5,2.5,min(edge.x,min(edge.y,edge.z)));
    return vec4(sum/max(weight,.0001),fade*step(.05,weight));
}
float localVisibility(vec3 world,vec3 target) {
    vec3 toLight=target-world;float distance=length(toLight);
    vec3 origin=world-LocalOriginReady.xyz,ray=toLight/max(distance,.001);
    ivec3 cell=ivec3(floor(origin)),stepCell=ivec3(sign(ray));
    vec3 inverse=1.0/max(abs(ray),vec3(.00001));
    vec3 nextBoundary=mix(vec3(cell),vec3(cell)+1.0,step(vec3(0),ray));
    vec3 nextDistance=abs(nextBoundary-origin)*inverse;
    for(int i=0;i<48;i++) {
        if(localCell(cell).a<.75)return 0.0;
        float next=min(nextDistance.x,min(nextDistance.y,nextDistance.z));
        if(next>=distance)break;
        if(nextDistance.x<=nextDistance.y && nextDistance.x<=nextDistance.z){cell.x+=stepCell.x;nextDistance.x+=inverse.x;}
        else if(nextDistance.y<=nextDistance.z){cell.y+=stepCell.y;nextDistance.y+=inverse.y;}
        else {cell.z+=stepCell.z;nextDistance.z+=inverse.z;}
    }
    return 1.0;
}
vec3 sourceRadianceAt(vec3 world,vec3 normal) {
    vec3 sum=vec3(0);
    for(int i=0;i<32;i++) {
        if(i>=int(HeldPosition.w))break;
        vec3 position=SourcePosition[i].xyz;
        float radius=SourcePosition[i].w;
        vec3 toCenter=position-world;
        if(dot(toCenter,toCenter)>=radius*radius)continue;
        if(SourceColor[i].a>.5) {
            // Use a point on the receiver-facing emitter surface, so a floor
            // lantern does not cast its visibility ray inside the floor.
            position=clamp(world,position-vec3(.5),position+vec3(.5))
                +normalize(world-position)*.055;
        } else position.y+=.22;
        vec3 toLight=position-world;float distance=length(toLight);
        float falloff=pow(max(0.0,1.0-distance/radius),3.0);
        float diffuse=.18+.82*max(dot(normal,toLight/max(distance,.001)),0.0);
        sum+=SourceColor[i].rgb*falloff*diffuse*.85*localVisibility(world,position);
    }
    float peak=max(sum.r,max(sum.g,sum.b));
    return sum/(1.0+peak*.25);
}
vec3 localAmbientAt(vec3 nativeAmbient,vec3 nativeLight,vec2 levels,vec3 world,vec3 normal) {
    vec4 local=localLightAt(world);
    float power=max(local.r,max(local.g,local.b));
    vec3 hue=local.rgb/max(power,.001);
    float blockShare=levels.x/max(levels.x+levels.y*.70,.001);
    // A valid air cell with zero light is meaningful. Falling back to the
    // scalar lightmap there admits light sampled from the far side of a wall.
    float influence=local.a*blockShare;
    // Native block light already has a tungsten cast; use its intensity once
    // and let the source volume supply hue, especially for cool emitters.
    float intensity=dot(nativeLight,vec3(.2126,.7152,.0722));
    // Preserve bright pools close to emitters, then let their reach fall away.
    // The native scalar lightmap alone keeps distant walls nearly as bright
    // as the floor beside a source, which obscures the room's depth.
    float falloff=.08+.92*power*power;
    vec3 fill=vec3(intensity)*hue*(.25*falloff);
    vec3 sources=sourceRadianceAt(world,normal);
    return mix(nativeAmbient,fill+sources,influence);
}
vec3 heldRadianceAt(vec3 world,vec3 normal) {
    float radius=max(HeldMain.w,HeldOff.w);
    if(radius<1.0||LocalOriginReady.w<.5)return vec3(0);
    vec3 toLight=HeldPosition.xyz-world;float distance=length(toLight);
    if(distance>=radius)return vec3(0);
    // Cell visibility prevents a held torch from illuminating through walls.
    // Endpoints are offset by the caller onto the receiving air side.
    vec3 origin=world-LocalOriginReady.xyz,ray=toLight/max(distance,.001);
    ivec3 cell=ivec3(floor(origin)),stepCell=ivec3(sign(ray));
    vec3 inverse=1.0/max(abs(ray),vec3(.00001));
    vec3 nextBoundary=mix(vec3(cell),vec3(cell)+1.0,step(vec3(0),ray));
    vec3 nextDistance=abs(nextBoundary-origin)*inverse;
    for(int i=0;i<48;i++){
        if(localCell(cell).a<.75)return vec3(0);
        float next=min(nextDistance.x,min(nextDistance.y,nextDistance.z));
        if(next>=distance)break;
        if(nextDistance.x<=nextDistance.y && nextDistance.x<=nextDistance.z){cell.x+=stepCell.x;nextDistance.x+=inverse.x;}
        else if(nextDistance.y<=nextDistance.z){cell.y+=stepCell.y;nextDistance.y+=inverse.y;}
        else {cell.z+=stepCell.z;nextDistance.z+=inverse.z;}
    }
    float attenuation=pow(max(0.0,1.0-distance/radius),2.0);
    float diffuse=.25+.75*max(dot(normal,toLight/max(distance,.001)),0.0);
    return max(HeldMain.rgb,HeldOff.rgb)*attenuation*diffuse*1.55;
}
#endif
