"""Package the authored Iris shader sources reproducibly, without legacy mods."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from datetime import datetime, timezone
import hashlib,json,re,sys
ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'shaderpacks/Aster'
OUT=ROOT/'build/shaderpacks/Aster-Sodium-0.2.1.zip'
inspect='--inspect-sky' in sys.argv
if inspect:OUT=ROOT/'build/shaderpacks/Aster-Sodium-Inspect-0.2.0.zip'
def sha(path):
    with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
files=sorted(p for p in PACK.rglob('*') if p.is_file())
assert files
for p in files:
    if p.suffix in ['.glsl','.vsh','.fsh']:
        text=p.read_text()
        assert 'aster_vulkan:' not in text,p
        for name in re.findall(r'#include\s+"([^"]+)"',text):
            target=PACK/'shaders'/name.lstrip('/')
            assert target.is_file(),(p,name)
assert (PACK/'shaders/final.fsh').exists()
OUT.parent.mkdir(parents=True,exist_ok=True)
with ZipFile(OUT,'w',compression=ZIP_DEFLATED,compresslevel=9) as z:
    for p in files:
        entry=ZipInfo(p.relative_to(PACK).as_posix(),date_time=(2026,10,11,0,0,0))
        entry.compress_type=ZIP_DEFLATED
        data=p.read_bytes()
        if inspect and entry.filename=='shaders/final.fsh':
            data=b'''#version 330 compatibility
#define ASTER_FRAGMENT
#include "/lib/common.glsl"
varying vec2 texcoord;
void main(){
    vec2 local=fract(texcoord*2.0);vec3 color;
    if(texcoord.x<.5&&texcoord.y>=.5){vec4 cloud=texture(colortex8,local);color=cloud.rgb+vec3(.25)*cloud.a;}
    else if(texcoord.x>=.5&&texcoord.y>=.5){float d=texture(depthtex0,local).r;color=vec3(d);}
    else if(texcoord.x<.5){color=asterSkyRadiance(worldRay(local));color=linearToDisplay(color/(1.0+color));}
    else {color=worldRay(local)*.5+.5;}
    gl_FragData[0]=vec4(color,1);
}
'''
        z.writestr(entry,data)
with ZipFile(OUT) as z:assert z.testzip() is None
receipt={'builtUtc':datetime.now(timezone.utc).isoformat(),'pack':str(OUT),'sha256':sha(OUT),
         'bytes':OUT.stat().st_size,'sourceFiles':[{'path':str(p.relative_to(PACK)),'sha256':sha(p)} for p in files],
         'requiredRuntime':{'minecraft':'26.3','sodium':'0.9.2','iris':'1.11.7','graphicsBackend':'opengl'},
         'legacyAsterVulkanModRequired':False,'shaderRuntimeVerified':False,'visualParityProven':False}
receipt['diagnosticSkyQuadrants']=inspect
evidence=ROOT/'tools/aster-sodium-migration/evidence';evidence.mkdir(parents=True,exist_ok=True)
(evidence/('build-inspect.json' if inspect else 'build-current.json')).write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps({k:receipt[k] for k in ['pack','sha256','bytes','legacyAsterVulkanModRequired']},indent=2))
