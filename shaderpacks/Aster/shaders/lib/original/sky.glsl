#ifndef ASTER_ORIGINAL_SKY
#define ASTER_ORIGINAL_SKY
#ifndef ASTER_SKY_GLSL
#define ASTER_SKY_GLSL
// Authored scene-linear atmosphere, shared verbatim by the sky and water.
// Clouds use curved layers with optical depth and a sampled light-facing edge.
// This is a procedural sky model, not a spectral atmosphere simulation.
const float SKY_PI=3.14159265359;
#ifndef SKY_NO_CLOUD_TEXTURE
// The Iris scene contract supplies the cloud sampler.
#endif
float skyHash(vec3 p) {
    p=fract(p*.1031);p+=dot(p,p.yzx+33.33);
    return fract((p.x+p.y)*p.z);
}
float skyNoise(vec3 p) {
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(skyHash(i),skyHash(i+vec3(1,0,0)),f.x),
                   mix(skyHash(i+vec3(0,1,0)),skyHash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(skyHash(i+vec3(0,0,1)),skyHash(i+vec3(1,0,1)),f.x),
                   mix(skyHash(i+vec3(0,1,1)),skyHash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float skyFbm(vec3 p) {
    float n=0.0,a=.533;
    for(int i=0;i<4;i++) { n+=a*skyNoise(p);p=p*2.07+vec3(17.1,9.2,13.7);a*=.48; }
    return n;
}
vec3 skyRotateStars(vec3 d) {
    // Undo the authored 30-degree orbital-plane tilt, then apply Minecraft's
    // actual sidereal clock so the stellar field and celestial bodies agree.
    d=vec3(d.x,.8660254*d.y-.5*d.z,.5*d.y+.8660254*d.z);
    float a=SkyMoon.w,c=cos(a),s=sin(a);
    return vec3(c*d.x+s*d.y,-s*d.x+c*d.y,d.z);
}
vec3 skyAtmosphere(vec3 d) {
    float h=max(d.y,0.0),e=SkySun.y;
    float day=smoothstep(-.14,.20,e);
    float blueHour=exp(-pow((e+.075)/.11,2.0));
    float dusk=exp(-pow((e-.10)/.29,2.0));
    float horizon=exp(-h*5.5);
    float facing=pow(max(dot(normalize(vec3(d.x,.05,d.z)),normalize(vec3(SkySun.x,.05,SkySun.z))),0.0),3.0);
    // Sapphire zenith, cyan middle atmosphere and pale, warm distant air.
    vec3 daylight=mix(vec3(.085,.235,.48),vec3(.62,.77,.88),horizon);
    vec3 night=mix(vec3(.0018,.0032,.009),vec3(.012,.019,.038),horizon);
    vec3 color=mix(night,daylight,day);
    vec3 twilight=mix(vec3(.46,.28,.18),vec3(1.32,.62,.26),facing);
    vec3 duskField=mix(vec3(.30,.19,.28),twilight,pow(horizon,.65));
    color=mix(color,duskField,dusk*.60);
    color=mix(color,twilight,dusk*pow(horizon,.56)*(.55+.38*facing));
    color+=vec3(.08,.08,.22)*blueHour*exp(-pow((h-.22)*3.5,2.0));
    // Broad forward-scattering aureole; the small disk remains HDR downstream.
    float sunDot=max(dot(d,SkySun.xyz),0.0);
    float haze=pow(sunDot,24.0)*.15+pow(sunDot,260.0)*.18;
    color+=mix(vec3(1.1,.24,.065),vec3(1.0,.83,.58),smoothstep(.05,.45,e))*haze*smoothstep(-.10,.02,e);
    float rain=clamp((1.0-AtmosphereColor.a)*2.0,0.0,1.0);
    vec3 overcast=mix(vec3(.008,.011,.019),vec3(.23,.255,.28),day);
    color=mix(color,overcast,rain*.92);
    // Curvature and distant extinction blend the lower hemisphere into haze.
    color=mix(color,mix(night,daylight,day),1.0-smoothstep(-.24,.0,d.y));
    return max(color,vec3(0));
}
vec3 skyStars(vec3 d) {
    vec3 ad=abs(d);vec2 uv;float face;
    if(ad.x > ad.y && ad.x > ad.z) {uv=d.zy/ad.x;face=d.x > 0.0 ? 1.0 : 2.0;}
    else if(ad.y > ad.z) {uv=d.xz/ad.y;face=d.y > 0.0 ? 3.0 : 4.0;}
    else {uv=d.xy/ad.z;face=d.z > 0.0 ? 5.0 : 6.0;}
    vec3 stars=vec3(0);
    for(int layer=0;layer<2;layer++) {
        float grid=layer == 0 ? 115.0 : 265.0;
        vec2 p=(uv*.5+.5)*grid,id=floor(p);
        float footprint=max(length(dFdx(p)),length(dFdy(p)));
        float seed=skyHash(vec3(id,face+float(layer)*7.0));
        if(seed < (layer == 0 ? .035 : .025)) {
            vec2 center=vec2(skyHash(vec3(id,face+21.0)),skyHash(vec3(id,face+37.0)))*.60+.20;
            vec2 delta=fract(p)-center;
            // Pixel-footprint integration keeps fine stars visible during rotation.
            float radius=(layer == 0 ? .085 : .060)*mix(.75,1.4,skyHash(vec3(id,face+59.0)));
            float filtered=max(radius,footprint*.55);
            float profile=exp(-dot(delta,delta)/(filtered*filtered))*radius*radius/(filtered*filtered);
            float brightness=layer == 0 ? .65 : .23;
            vec3 tint=mix(vec3(.63,.77,1.0),vec3(1.0,.80,.57),skyHash(vec3(id,face+81.0)));
            stars+=tint*profile*brightness;
        }
    }
    return stars;
}
vec3 skyGalaxy(vec3 d) {
    vec3 axis=normalize(vec3(.38,.68,.62));
    float latitude=dot(d,axis);
    float band=exp(-pow(latitude/.145,2.0));
    float knots=skyFbm(d*13.0+vec3(29,6,10));
    float dust=skyFbm(d*34.0+vec3(8,21,19));
    float core=pow(max(dot(d,normalize(vec3(-.81,.55,-.2))),0.0),6.0);
    vec3 glow=mix(vec3(.009,.015,.028),vec3(.038,.027,.031),core);
    return glow*band*(.25+1.75*knots)*smoothstep(.24,.67,dust);
}
vec3 skyMoon(vec3 d,out float mask) {
    vec3 moon=SkyMoon.xyz;
    vec3 right=normalize(cross(moon,vec3(0,0,1))),up=cross(right,moon);
    vec2 p=vec2(dot(d,right),dot(d,up))/.024;
    // Reflection directions jump at opaque silhouettes. An unbounded
    // derivative can illuminate unrelated pixels far outside the lunar disk.
    float r2=dot(p,p),edge=clamp(fwidth(r2),.006,.25);
    mask=(1.0-smoothstep(1.0-edge,1.0+edge,r2))*step(0.0,dot(d,moon));
    if(mask < .001) return vec3(0);
    float z=sqrt(max(1.0-r2,0.0)),phase=SkySun.w*2.0*SKY_PI;
    float illuminated=smoothstep(-.055,.055,dot(vec3(p,z),vec3(sin(phase),0,cos(phase))));
    float maria=skyFbm(vec3(p*4.2,3.1));
    float fine=skyNoise(vec3(p*28.0,7.0));
    float surface=mix(.45,1.0,smoothstep(.29,.65,maria))*(.87+.13*fine);
    // Small crater bowls and raised rims give the visible disk structure.
    for(int i=0;i<7;i++) {
        float n=float(i);
        vec2 center=(vec2(skyHash(vec3(n,5,1)),skyHash(vec3(n,8,2)))-.5)*1.55;
        float radius=.04+.10*skyHash(vec3(n,11,3));
        float ring=length(p-center)/radius;
        surface*=1.0-.18*exp(-ring*ring*2.0)+.18*exp(-pow((ring-1.0)*5.0,2.0));
    }
    vec3 earthshine=vec3(.012,.022,.037)*surface;
    return mix(earthshine,vec3(.82,.91,1.08)*surface*(.48+.52*z),illuminated);
}
vec4 skyCloudLayer(vec3 d,float altitude,float scale,bool cirrus) {
    float cameraHeight=clamp(CameraWorldTime.y,-64.0,altitude-20.0);
    float radius=8000.0+cameraHeight,outer=8000.0+altitude;
    float b=radius*d.y;
    float distance=-b+sqrt(max(b*b+outer*outer-radius*radius,0.0));
    vec2 drift=vec2(sin(SkyTiming.x),cos(SkyTiming.x))*18.0;
    // Curved layers retain a finite sampling domain at the horizon.
    vec3 p=vec3((CameraWorldTime.xz+d.xz*distance)*scale+drift,cirrus ? 15.7 : 4.1);
    float base;
    if(cirrus && !verificationFlag(32768)) {
        // Curl separate wisps inside sparse large patches. They have their own
        // altitude, scale and wind shear instead of repeating the low layer.
        vec2 w=p.xy;
        float patch=skyNoise(vec3(w*.26,64.2));
        float curl=skyNoise(vec3(w*.70,9.4))-.5;
        p.xy=vec2(w.x*.32+w.y*.19+curl*.65,w.y*2.2+curl*.9);
        base=skyFbm(p)*smoothstep(.32,.70,patch);
    } else {
        if(cirrus) p.xy=vec2(p.x*.38+p.y*.24,p.y*1.65);
        base=skyFbm(p);
    }
    float rain=1.0-AtmosphereColor.a;
    float density=cirrus ? smoothstep(.42,.72,base)*.24 : smoothstep(mix(.49,.32,rain),.76,base);
    if(cirrus && verificationFlag(32768)) density=smoothstep(.53,.79,base)*.18;
    density*=smoothstep(-.035,.08,d.y);
    float alpha=1.0-exp(-density*(cirrus ? 1.2 : 5.5));
    vec3 light=SkySun.y > -.08 ? SkySun.xyz : SkyMoon.xyz;
    float lightSide=skyFbm(p+vec3(light.xz*.72,.34));
    float edgeLight=clamp(.55+(base-lightSide)*3.5,0.0,1.0);
    float day=smoothstep(-.13,.18,SkySun.y);
    float dusk=exp(-pow((SkySun.y-.02)/.19,2.0));
    vec3 shade=mix(vec3(.007,.013,.026),vec3(.22,.32,.47),day);
    vec3 lit=mix(vec3(.045,.065,.105),vec3(.95,1.02,1.12),day);
    lit=mix(lit,vec3(1.4,.52,.24),dusk*.80);
    shade=mix(shade,vec3(.22,.09,.20),dusk*.62);
    vec3 color=mix(shade,lit,edgeLight*(1.0-density*.43));
    float lining=pow(max(dot(d,light),0.0),32.0)*pow(1.0-density,3.0);
    color+=mix(vec3(.10,.15,.25),vec3(1.6,1.10,.62),day)*lining*.70;
    color=mix(color,mix(vec3(.009,.016,.028),vec3(.20,.25,.30),day),rain*.72);
    // Distant clouds dissolve naturally into the atmospheric perspective.
    float haze=1.0-exp(-distance*.00022);
    color=mix(color,skyAtmosphere(d),haze*.64);
    return vec4(color,alpha);
}
vec3 asterSkyRadiance(vec3 direction) {
    vec3 d=normalize(direction),color=skyAtmosphere(d);
    float clear=AtmosphereColor.a;
    float stars=(1.0-smoothstep(-.20,-.045,SkySun.y))*clear*clear*smoothstep(-.01,.18,d.y);
    vec3 celestial=skyRotateStars(d);
    color+=(skyStars(celestial)+skyGalaxy(celestial))*stars;
    float moonMask;vec3 moon=skyMoon(d,moonMask);
    float celestialHorizon=smoothstep(-.001,.001,d.y);
    moonMask*=celestialHorizon;
    float moonVisibility=(1.0-smoothstep(.04,.30,SkySun.y))*clear*clear;
    color=mix(color,moon,moonMask*moonVisibility);
    float lunarIllumination=.5+.5*cos(SkySun.w*2.0*SKY_PI);
    color+=vec3(.018,.030,.060)*pow(max(dot(d,SkyMoon.xyz),0.0),180.0)*moonVisibility*lunarIllumination;
    float sunAngle=acos(clamp(dot(d,SkySun.xyz),-1.0,1.0));
    // Keep the solar edge filter local to its angular disk, including
    // water pixels next to trees, shorelines and gaps in leaf coverage.
    float aa=clamp(fwidth(sunAngle),.00012,.0045);
    float disk=1.0-smoothstep(.009-aa,.009+aa,sunAngle);
    color+=mix(vec3(8.0,2.2,.45),vec3(13.0,11.0,7.8),smoothstep(.04,.50,SkySun.y))*disk*clear*clear*celestialHorizon;
    vec4 high=skyCloudLayer(d,420.0,.0038,true);
    color=mix(color,high.rgb,high.a);
#ifndef SKY_NO_CLOUD_TEXTURE
    vec2 cloudUv=vec2(atan(d.z,d.x)/(2.0*SKY_PI)+.5,.5-asin(clamp(d.y,-1.0,1.0))/SKY_PI);
    vec4 cloud=texture(SkyClouds,cloudUv);
    color=color*cloud.a+cloud.rgb;
#endif
    return max(color,vec3(0));
}
#endif

#endif
