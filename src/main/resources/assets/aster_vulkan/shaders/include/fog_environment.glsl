uniform sampler2D FogEnvironment;
uniform sampler2D FogTerrain;
int fogWrap(int x) { return (x%40+40)%40; }
vec4 fogEnvironmentCell(ivec3 cell) {
    int y=clamp(cell.y,0,23);
    ivec2 pixel=ivec2(fogWrap(cell.x)+(y%8)*40,fogWrap(cell.z)+(y/8)*40);
    vec4 value=texelFetch(FogEnvironment,pixel,0);
    return value.a > .5 ? value : vec4(1,0,0,1);
}
vec3 fogAirLight(vec3 world) {
    if(any(greaterThan(abs(world.xz-FogEnvironmentOrigin.xz),vec2(156))) ||
        world.y < FogEnvironmentOrigin.y || world.y > FogEnvironmentOrigin.y+96.0) return vec3(1,0,0);
    vec3 grid=vec3(world.x/8.0-.5,(world.y-FogEnvironmentOrigin.y)/4.0-.5,world.z/8.0-.5);
    ivec3 base=ivec3(floor(grid));vec3 f=fract(grid),sum=vec3(0);
    for(int z=0;z<2;z++) for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        float w=(x == 0 ? 1.0-f.x : f.x)*(y == 0 ? 1.0-f.y : f.y)*(z == 0 ? 1.0-f.z : f.z);
        sum+=fogEnvironmentCell(base+ivec3(x,y,z)).rgb*w;
    }
    return sum;
}
vec2 fogGround(vec3 world) {
    vec2 grid=world.xz/8.0-.5;ivec2 base=ivec2(floor(grid));vec2 f=fract(grid),sum=vec2(0);
    if(any(greaterThan(abs(world.xz-FogEnvironmentOrigin.xz),vec2(156)))) return vec2(62,.05);
    for(int z=0;z<2;z++) for(int x=0;x<2;x++) {
        vec4 value=texelFetch(FogTerrain,ivec2(fogWrap(base.x+x),fogWrap(base.y+z)),0);
        vec2 ground=value.a > .5 ? vec2(value.r*512.0-64.0,value.g) : vec2(62,.05);
        sum+=ground*(x == 0 ? 1.0-f.x : f.x)*(z == 0 ? 1.0-f.y : f.y);
    }
    return sum;
}
float fogNoise(vec3 p) {
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float result=0.0;
    for(int z=0;z<2;z++) for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        vec3 q=i+vec3(x,y,z);
        float n=fract(sin(dot(q,vec3(127.1,311.7,74.7)))*43758.5453);
        result+=n*(x == 0 ? 1.0-f.x : f.x)*(y == 0 ? 1.0-f.y : f.y)*(z == 0 ? 1.0-f.z : f.z);
    }
    return result;
}
float fogWorldDensity(vec3 world,vec3 air) {
    vec2 ground=fogGround(world);
    float rain=1.0-AtmosphereColor.a;
    float twilight=1.0-smoothstep(.04,.35,abs(SkySun.y));
    float night=1.0-smoothstep(-.15,.05,SkySun.y);
    // Close the drift loop at the native clock wrap rather than snapping noise.
    float t=CameraWorldTime.w*6.283185307/1200.0;
    vec3 drift=vec3(sin(t)*12.0,0,(cos(t)-1.0)*8.0);
    float patch=smoothstep(.20,.78,fogNoise((world-drift)*vec3(.045,.07,.045)));
    float humidity=.10+ground.y*.9+rain*.55;
    float groundMist=.0042*humidity*(.18+.65*twilight+.25*night)*
        exp(-max(world.y-ground.x,0.0)/6.0)*(.25+patch*1.5)*smoothstep(-2.0,1.0,world.y-ground.x);
    float haze=.00085*exp(-max(world.y-62.0,0.0)*.012)*(1.0+rain*1.5);
    return max(0.0,(haze+groundMist)*(1.0-clamp(air.b,0.0,1.0)));
}
