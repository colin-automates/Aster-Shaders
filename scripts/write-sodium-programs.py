"""Create thin Iris entry points around Aster's renderer-independent modules."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
S=ROOT/'shaderpacks/Aster/shaders'
def write(name,text):
    (S/name).write_text(text.strip()+'\n',encoding='utf-8')
def program(name,body,defines=''):
    write(name+'.vsh','#version 330 compatibility\n'+defines+'\n#include "/lib/fullscreen.vsh"')
    write(name+'.fsh','#version 330 compatibility\n#define ASTER_FRAGMENT\n'+defines+'\n'+body)

for name,defines in {
    'gbuffers_terrain':'', 'gbuffers_block':'',
    'gbuffers_entities':'#define ASTER_ENTITY',
    'gbuffers_entities_glowing':'#define ASTER_ENTITY',
    'gbuffers_entities_translucent':'#define ASTER_ENTITY\n#define ASTER_FORWARD',
    'gbuffers_hand':'#define ASTER_HAND\n#define ASTER_FORWARD',
    'gbuffers_hand_water':'#define ASTER_HAND\n#define ASTER_FORWARD',
    'gbuffers_water':'#define ASTER_FORWARD',
    'gbuffers_textured':'#define ASTER_PARTICLE\n#define ASTER_FORWARD',
    'gbuffers_textured_lit':'#define ASTER_PARTICLE\n#define ASTER_FORWARD',
    'gbuffers_weather':'#define ASTER_PARTICLE\n#define ASTER_FORWARD',
    'gbuffers_damagedblock':'#define ASTER_PARTICLE\n#define ASTER_FORWARD',
    'gbuffers_beaconbeam':'#define ASTER_PARTICLE\n#define ASTER_FORWARD',
    'gbuffers_spidereyes':'#define ASTER_ENTITY\n#define ASTER_FORWARD\n#define ASTER_EMISSIVE',
}.items():
    write(name+'.vsh','#version 330 compatibility\n'+defines+'\n#include "/lib/geometry.vsh"')
    write(name+'.fsh','#version 330 compatibility\n#define ASTER_FRAGMENT\n'+defines+'\n#include "/lib/geometry.fsh"')

for name in ['gbuffers_skybasic','gbuffers_skytextured','gbuffers_clouds']:
    write(name+'.vsh','#version 330 compatibility\nvoid main(){gl_Position=ftransform();}')
    write(name+'.fsh','#version 330 compatibility\nvoid main(){discard;}')
write('gbuffers_basic.vsh','''#version 330 compatibility
varying vec4 tint;
void main(){gl_Position=ftransform();tint=gl_Color;}
''')
write('gbuffers_basic.fsh','''#version 330 compatibility
varying vec4 tint;
/* RENDERTARGETS: 0 */
void main(){gl_FragData[0]=tint;}
''')

cloudSource=ROOT/'scripts/templates/aster-cloud-march.glsl'
body=cloudSource.read_text()
body=body[body.index('float previousCloudDensity('):]
body=body.replace('texCoord','texcoord').replace('fragColor','gl_FragData[0]')
body=body.replace('/128.0','/float(CLOUD_STEPS)').replace('i<128','i<CLOUD_STEPS')
program('prepare','''#include "/lib/common.glsl"
#include "/lib/original/cloud_density.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: 8 */
'''+body)

for name,target,extent,epsilon,isFar in [('prepare1',11,'128.0','.125','false'),('prepare2',12,'512.0','.5','true')]:
    program(name,'''#include "/lib/common.glsl"
#include "/lib/original/water_caustic_source.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: TARGET */
void main(){
    if(SkySun.y<.02){gl_FragData[0]=vec4(0);return;}
    vec2 center=floor(cameraPosition.xz/16.0)*16.0;
    vec2 p=center+(texcoord-.5)*EXTENT;
    float e=EPSILON;
    vec2 dx=(causticSourceField(p+vec2(e,0),0.0,IS_FAR).yz-causticSourceField(p-vec2(e,0),0.0,IS_FAR).yz)/(2.0*e);
    vec2 dz=(causticSourceField(p+vec2(0,e),0.0,IS_FAR).yz-causticSourceField(p-vec2(0,e),0.0,IS_FAR).yz)/(2.0*e);
    gl_FragData[0]=vec4(dx,dz);
}
'''.replace('TARGET',str(target)).replace('EXTENT',extent).replace('EPSILON',epsilon).replace('IS_FAR',isFar))

write('shadow.vsh','''#version 330 compatibility
#include "/lib/common.glsl"
#include "/lib/original/foliage.glsl"
attribute vec4 mc_Entity;
attribute vec4 at_midBlock;
varying vec2 texcoord;
varying vec4 tint;
varying vec3 playerPos;
flat varying int blockId;
void main(){
    texcoord=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;tint=gl_Color;
    blockId=int(mc_Entity.x+.5);
    vec3 p=(shadowModelViewInverse*gl_ModelViewMatrix*gl_Vertex).xyz;
    if(blockId>=2&&blockId<=5){
        float height=clamp(.5-at_midBlock.y/64.0,0,1)+(blockId==5?1.0:0.0);
        p+=foliageWindOffset(p+cameraPosition,height,blockId==2,CameraWorldTime.w,rainStrength*.5);
    }
    if(blockId==1)p.y+=waterWaveField((p+cameraPosition).xz,CameraWorldTime.w,rainStrength*.5).x*WAVE_HEIGHT*smoothstep(.15,.85,fract(p.y+cameraPosition.y+.001));
    playerPos=p;
    gl_Position=shadowProjection*shadowModelView*vec4(p,1);
    gl_Position.xy/=.18+.82*length(gl_Position.xy);
    gl_Position.z*=.25;
}
''')
write('shadow.fsh','''#version 330 compatibility
uniform sampler2D gtexture;
varying vec2 texcoord;
varying vec4 tint;
varying vec3 playerPos;
flat varying int blockId;
/* DRAWBUFFERS:01 */
void main(){
    vec4 texel=texture2D(gtexture,texcoord)*tint;
    if(texel.a<.10)discard;
    vec3 transmission=vec3(0);
    float type=0;
    if(blockId==1){transmission=vec3(1);type=1.0;}
    else if(blockId==2){transmission=mix(vec3(.12),sqrt(max(texel.rgb,vec3(0)))*.45,.75);type=.65;}
    else if(blockId==14){transmission=mix(vec3(1),texel.rgb,texel.a);type=.25;}
    gl_FragData[0]=vec4(transmission,type);
    gl_FragData[1]=vec4(playerPos.y,0,0,type);
}
''')

program('deferred','''#include "/lib/common.glsl"
#include "/lib/surface.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: 0 */
void main(){
    float depth=texture(depthtex0,texcoord).r;
    if(depth>=.99999){gl_FragData[0]=vec4(SceneInfo.w>.5?asterSkyRadiance(worldRay(texcoord)):displayToLinear(fogColor),1);return;}
    vec4 encoded=texture(colortex1,texcoord),material=texture(colortex2,texcoord),base=texture(colortex3,texcoord);
    // Iris draws opaque hands before deferred lighting, with a compressed
    // depth range. Their forward lighting is already complete.
    if(encoded.a>2.5){gl_FragData[0]=vec4(texture(colortex0,texcoord).rgb,1);return;}
    vec3 n=normalize(encoded.xyz*2.0-1.0);
    vec3 p=relativePosition(texcoord,depth);
    vec4 properties=vec4(material.xy,texture(colortex4,texcoord).a,material.z);
    float ao=clamp(texture(colortex0,texcoord).a,.20,1.0);
    vec3 color=surfaceLight(base.rgb,n,p,properties,vec2(base.a,material.a),texture(colortex14,texcoord).rgb,ao);
    gl_FragData[0]=vec4(color,1);
}
''')
program('deferred1','''#include "/lib/settings.glsl"
uniform sampler2D colortex0;
varying vec2 texcoord;
/* RENDERTARGETS: 5 */
const bool colortex5MipmapEnabled = true;
void main(){gl_FragData[0]=texture(colortex0,texcoord);}
''')

program('composite','''#include "/lib/common.glsl"
#include "/lib/reflection.glsl"
#include "/lib/fog.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: 0 */
void main(){
    vec3 color=texture(colortex0,texcoord).rgb;
    float depth=texture(depthtex0,texcoord).r;
    if(depth<.99999){
        vec4 encoded=texture(colortex1,texcoord),material=texture(colortex2,texcoord);
        vec3 p=relativePosition(texcoord,depth),n=normalize(encoded.xyz*2.0-1.0);
        if(encoded.a<2.5&&(material.x<.70||material.y>.05)){
            vec4 reflection=screenReflection(p,n,material.x);
            vec3 fallback=encoded.a>.5&&encoded.a<1.5?waterSky(reflect(normalize(p),n)):materialSkyRadiance(material.x,n,normalize(-p));
            vec3 albedo=texture(colortex3,texcoord).rgb;
            vec3 weight=encoded.a>.5&&encoded.a<1.5?vec3(waterDielectricFresnel(max(dot(n,normalize(-p)),0),isEyeInWater==1)):materialReflectionWeight(albedo,material.x,material.y,n,normalize(-p))*material.a*material.a;
            color+=(reflection.rgb-fallback)*reflection.a*weight;
        }
        if(encoded.a<2.5)color=atmosphericFog(max(color,vec3(0)),p);
    }else if(isEyeInWater==1)color=atmosphericFog(color,worldRay(texcoord)*48.0);
    color*=1.0-clamp(blindness+darknessFactor*.75,0.0,1.0);
    gl_FragData[0]=vec4(max(color,vec3(0)),1);
}
''')
program('composite1','''#include "/lib/common.glsl"
#include "/lib/temporal.glsl"
varying vec2 texcoord;
/* RENDERTARGETS: 0,6,7 */
void main(){
    float depth=texture(depthtex0,texcoord).r;
    vec3 current=texture(colortex0,texcoord).rgb;
    vec3 resolved=temporalResolve(texcoord,current,depth);
    gl_FragData[0]=vec4(resolved,1);
    gl_FragData[1]=vec4(resolved,1);
    gl_FragData[2]=vec4(depth,1,0,1);
}
''')
program('composite2','''#include "/lib/settings.glsl"
uniform sampler2D colortex0;
varying vec2 texcoord;
/* RENDERTARGETS: 9 */
void main(){
    vec2 pixel=1.0/vec2(textureSize(colortex0,0));vec3 sum=vec3(0);
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec3 color=texture(colortex0,texcoord+vec2(x,y)*pixel*2.0).rgb;
        float l=dot(color,vec3(.2126,.7152,.0722));sum+=color*smoothstep(.7,2.0,l);
    }
    gl_FragData[0]=vec4(sum/9.0,1);
}
''')
for name,axis in [('composite3','vec2(1,0)'),('composite4','vec2(0,1)')]:
    program(name,'''#include "/lib/settings.glsl"
uniform sampler2D colortex9;
varying vec2 texcoord;
/* RENDERTARGETS: 9 */
void main(){
    vec2 delta=AXIS/vec2(textureSize(colortex9,0));
    vec3 sum=texture(colortex9,texcoord).rgb*.227027;
    sum+=(texture(colortex9,texcoord+delta*1.384615).rgb+texture(colortex9,texcoord-delta*1.384615).rgb)*.316216;
    sum+=(texture(colortex9,texcoord+delta*3.230769).rgb+texture(colortex9,texcoord-delta*3.230769).rgb)*.070270;
    gl_FragData[0]=vec4(sum,1);
}
'''.replace('AXIS',axis))
program('composite5','''#include "/lib/settings.glsl"
uniform sampler2D colortex0,colortex10;
uniform float frameTime;
uniform int frameCounter;
varying vec2 texcoord;
/* RENDERTARGETS: 10 */
void main(){
    float sum=0;
    for(int y=0;y<16;y++)for(int x=0;x<24;x++){
        vec3 color=texture(colortex0,(vec2(x,y)+.5)/vec2(24,16)).rgb;
        sum+=log2(max(dot(color,vec3(.2126,.7152,.0722)),.0001));
    }
    float luminance=exp2(sum/384.0);
    float target=clamp(.14/max(luminance,.004),EXPOSURE_MIN,EXPOSURE_MAX);
    float previous=texture(colortex10,vec2(.5)).r;
    float rate=target<previous?2.5:.8;
    float weight=frameCounter<3||previous<=0?1.0:1.0-exp(-frameTime*rate);
    gl_FragData[0]=vec4(mix(previous,target,weight),luminance,0,1);
}
''')
tone=(ROOT/'tools/solid-reflections-release/evidence/candidate-v1/source/src/main/resources/assets/aster_vulkan/shaders/core/tonemap.fsh').read_text()
tone=tone[tone.index('vec3 filmic('):]
tone=tone.replace('texCoord','texcoord').replace('fragColor','gl_FragData[0]').replace('bloom*0.065','bloom*BLOOM_STRENGTH')
program('final','''#include "/lib/common.glsl"
#define HdrInput colortex0
#define Exposure colortex10
#define Bloom0 colortex9
#define Bloom1 colortex9
#define Bloom2 colortex9
#define Bloom3 colortex9
#define TAA_INPUT
varying vec2 texcoord;
'''+tone)
print('Wrote Sodium/Iris geometry, shadow, cloud, lighting, reflection, fog, temporal, exposure and final programs.')
for folder,define in [('world-1','ASTER_NETHER'),('world1','ASTER_END')]:
    target=S/folder;target.mkdir(exist_ok=True)
    for file in list(S.glob('*.vsh'))+list(S.glob('*.fsh')):
        text=file.read_text().replace('#version 330 compatibility','#version 330 compatibility\n#define '+define,1)
        if file.name=='prepare.fsh':
            text='#version 330 compatibility\n/* RENDERTARGETS: 8 */\nvoid main(){gl_FragData[0]=vec4(0,0,0,1);}\n'
        (target/file.name).write_text(text)
