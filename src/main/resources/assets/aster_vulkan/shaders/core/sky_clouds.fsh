#version 330
#extension GL_ARB_separate_shader_objects : require
#include <aster_vulkan:scene.glsl>
#define SKY_NO_CLOUD_TEXTURE
#include <aster_vulkan:sky.glsl>
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
#include <aster_vulkan:cloud_density.glsl>

float previousCloudDensity(vec3 p,float rain) {
    float h=p.y;
    if(h <= 0.0 || h >= 1.0) return 0.0;
    vec2 horizontal=p.xz*.85,cell=floor(horizontal),f=fract(horizontal);
    float billow=0.0;
    // Overlapping ellipsoids give cloud groups distinct rounded silhouettes.
    // Fine erosion carves those volumes instead of blurring a flat noise mask.
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        vec2 offset=vec2(x,y),id=cell+offset;
        float seed=skyHash(vec3(id,41.0));
        if(seed > .40+rain*.42) continue;
        vec2 center=vec2(skyHash(vec3(id,53.0)),skyHash(vec3(id,67.0)))*.65+.175;
        float radius=.55+.24*skyHash(vec3(id,79.0));
        float verticalCenter=.38+.15*skyHash(vec3(id,83.0));
        vec3 delta=vec3(f-offset-center,(h-verticalCenter)*1.65);
        billow=max(billow,1.0-length(delta)/radius);
    }
    float erosion=skyFbm(vec3(p.x*11.0,h*13.0,p.z*11.0)+vec3(6.4,12.1,8.3));
    float density=max(billow-(1.0-erosion)*.25,0.0)*2.1;
    return density*smoothstep(0.0,.12,h)*(1.0-smoothstep(.72,1.0,h));
}
void previousClouds(vec3 d) {

    if(d.y < -.04) {fragColor=vec4(0,0,0,1);return;}
    const float earth=8000.0,bottom=180.0,top=310.0;
    float cameraHeight=clamp(CameraWorldTime.y,-64.0,bottom-5.0);
    float radius=earth+cameraHeight,b=radius*d.y;
    float start=-b+sqrt(b*b+(earth+bottom)*(earth+bottom)-radius*radius);
    float end=-b+sqrt(b*b+(earth+top)*(earth+top)-radius*radius);
    float stepSize=(end-start)/18.0;
    float rain=1.0-AtmosphereColor.a,day=smoothstep(-.13,.18,SkySun.y);
    float dusk=exp(-pow((SkySun.y-.015)/.25,2.0));
    vec3 light=SkySun.y > -.10 ? SkySun.xyz : SkyMoon.xyz;
    vec3 ambient=mix(vec3(.007,.012,.026),vec3(.16,.25,.40),day);
    ambient=mix(ambient,vec3(.18,.075,.14),dusk*.52);
    vec3 sunlight=mix(vec3(.042,.066,.12),vec3(1.4,1.36,1.25),day);
    sunlight*=mix(.22+.78*(.5+.5*cos(SkySun.w*2.0*SKY_PI)),1.0,day);
    sunlight=mix(sunlight,vec3(1.55,.44,.14),dusk*.82);
    vec2 drift=vec2(sin(SkyTiming.x),cos(SkyTiming.x))*18.0;
    vec3 sum=vec3(0);float transmittance=1.0;
    for(int i=0;i<18;i++) {
        float distance=start+(float(i)+.5)*stepSize;
        vec3 position=d*distance;
        float height=length(position+vec3(0,radius,0))-earth;
        vec3 p=vec3((CameraWorldTime.x+position.x)/155.0+drift.x,(height-bottom)/(top-bottom),
                    (CameraWorldTime.z+position.z)/155.0+drift.y);
        float density=previousCloudDensity(p,rain);
        if(density > .001) {
            vec3 lightOffset=vec3(light.x*35.0/155.0,light.y*35.0/(top-bottom),light.z*35.0/155.0);
            float shadow=exp(-previousCloudDensity(p+lightOffset,rain)*2.3);
            float powder=1.0-exp(-density*3.0);
            float phase=.60+pow(max(dot(d,light),0.0),12.0)*1.4;
            vec3 source=ambient+sunlight*shadow*(.40+.45*powder)*phase;
            source=mix(source,mix(vec3(.008,.015,.026),vec3(.19,.25,.31),day),rain*.70);
            float absorbed=1.0-exp(-density*stepSize*.028);
            sum+=transmittance*source*absorbed;
            transmittance*=1.0-absorbed;
            if(transmittance < .012) break;
        }
    }
    float fade=smoothstep(-.04,.07,d.y),haze=1.0-exp(-start*.00022);
    float alpha=(1.0-transmittance)*fade;
    vec3 radiance=sum/max(1.0-transmittance,.001);
    radiance=mix(radiance,skyAtmosphere(d),haze*.68);
    fragColor=vec4(radiance*alpha,1.0-alpha);
}

void main() {
    float az=(texCoord.x-.5)*2.0*SKY_PI,elevation=(.5-texCoord.y)*SKY_PI;
    vec3 d=vec3(cos(az)*cos(elevation),sin(elevation),sin(az)*cos(elevation));
    if(verificationFlag(32768)) {previousClouds(d);return;}
    if(d.y < -.035) {fragColor=vec4(0,0,0,1);return;}
    const float earth=12000.0,bottom=180.0,top=425.0,scale=220.0;
    float cameraHeight=clamp(CameraWorldTime.y,-64.0,bottom-5.0);
    float radius=earth+cameraHeight,b=radius*d.y;
    float start=-b+sqrt(b*b+(earth+bottom)*(earth+bottom)-radius*radius);
    float end=-b+sqrt(b*b+(earth+top)*(earth+top)-radius*radius);
    // A fixed phase/count prevents angular bands when neighboring rays would
    // otherwise change their integer step count and shift every sample.
    float stepSize=(end-start)/128.0;
    float rain=clamp((1.0-AtmosphereColor.a)*2.0,0.0,1.0),day=smoothstep(-.13,.18,SkySun.y);
    float dusk=exp(-pow((SkySun.y-.10)/.29,2.0));
    vec3 light=SkySun.y > -.10 ? SkySun.xyz : SkyMoon.xyz;
    vec3 ambient=mix(vec3(.007,.011,.020),vec3(.16,.22,.31),day);
    ambient=mix(ambient,vec3(.19,.16,.18),dusk*.65);
    vec3 sunlight=mix(vec3(.034,.048,.074),vec3(1.38,1.36,1.30),day);
    sunlight*=mix(.20+.80*(.5+.5*cos(SkySun.w*2.0*SKY_PI)),1.0,day);
    sunlight=mix(sunlight,vec3(1.65,.85,.37),dusk*.80);
    vec2 drift=vec2(sin(SkyTiming.x),cos(SkyTiming.x))*18.0;
    vec3 lightStep=vec3(light.x/scale,light.y/(top-bottom),light.z/scale);
    float forward=pow(max(dot(d,light),0.0),16.0);
    vec3 sum=vec3(0);float transmittance=1.0;
    for(int i=0;i<128;i++) {
        float distance=start+(float(i)+.5)*stepSize;
        vec3 position=d*distance;
        float height=length(position+vec3(0,radius,0))-earth;
        vec3 p=vec3((CameraWorldTime.x+position.x)/scale+drift.x,(height-bottom)/(top-bottom),
                    (CameraWorldTime.z+position.z)/scale+drift.y);
        float verticalStep=stepSize*abs(distance+radius*d.y)/((earth+height)*(top-bottom));
        float footprint=max(distance*.0016/scale,max(stepSize*length(d.xz)/scale,verticalStep));
        float density=cloudDensity(p,rain,footprint);
        if(density > .001) {
            // Sample through the billow toward the light so its underside
            // retains depth while exposed shoulders remain bright.
            float optical=cloudDensity(p+lightStep*18.0,rain,footprint)*1.6
                +cloudShape(p+lightStep*38.0,rain)*1.7
                +cloudShape(p+lightStep*70.0,rain)*1.7
                +cloudShape(p+lightStep*115.0,rain)*1.5;
            float shadow=exp(-optical);
            float powder=1.0-exp(-density*2.5);
            vec3 source=ambient*(.60+.42*p.y)+sunlight*(shadow*(.88+forward*.70)+.13*powder);
            source=mix(source,mix(vec3(.008,.015,.025),vec3(.20,.25,.31),day),rain*.62);
            float absorbed=1.0-exp(-density*stepSize*.032);
            sum+=transmittance*source*absorbed;
            transmittance*=1.0-absorbed;
            if(transmittance < .008) break;
        }
    }
    float fade=smoothstep(-.035,.065,d.y),haze=1.0-exp(-start*.00018);
    float alpha=(1.0-transmittance)*fade;
    vec3 radiance=sum/max(1.0-transmittance,.001);
    radiance=mix(radiance,skyAtmosphere(d),haze*.66);
    fragColor=vec4(radiance*alpha,1.0-alpha);
}
