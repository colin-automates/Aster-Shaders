#ifndef ASTER_REFLECTION
#define ASTER_REFLECTION
vec4 screenReflection(vec3 p,vec3 n,float roughness) {
    vec3 origin=(gbufferModelView*vec4(p,1)).xyz;
    vec3 direction=normalize(mat3(gbufferModelView)*reflect(normalize(p),n));
    if(direction.z>=-.025)return vec4(0);
    float stepSize=.12+length(p)*.012;
    vec3 ray=origin+mat3(gbufferModelView)*n*.06;
    vec3 previous=ray;
    for(int i=0;i<REFLECTION_STEPS;i++) {
        previous=ray;ray+=direction*stepSize;stepSize*=1.10;
        vec2 uv=projectView(ray);
        if(any(lessThan(uv,vec2(.002)))||any(greaterThan(uv,vec2(.998))))break;
        float depth=texture(depthtex1,uv).r;
        if(depth>=.99999)continue;
        vec3 scene=viewPosition(uv,depth);
        float delta=scene.z-ray.z;
        if(delta>0&&delta<max(.18,stepSize*1.4)) {
            vec3 a=previous,b=ray;
            for(int j=0;j<5;j++) {
                vec3 mid=(a+b)*.5;vec2 q=projectView(mid);
                float d=texture(depthtex1,q).r;
                if(viewPosition(q,d).z>mid.z)b=mid;else a=mid;
            }
            uv=projectView((a+b)*.5);
            float edge=min(min(uv.x,1.0-uv.x),min(uv.y,1.0-uv.y));
            float confidence=smoothstep(.015,.10,edge)*exp(-float(i)*.025);
            vec3 color=textureLod(colortex5,uv,roughness*roughness*5.0).rgb;
            return vec4(color,confidence);
        }
    }
    return vec4(0);
}
#endif
