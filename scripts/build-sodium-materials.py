"""Move Aster's generated relief into an ordinary Iris normal-map resource pack."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
from PIL import Image
from datetime import datetime,timezone
import numpy as np
import io,json,hashlib,os,argparse
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'tools/solid-reflections-release/evidence/candidate-v1/source'
TABLE=ROOT/'materials/aster-materials.json'
parser=argparse.ArgumentParser()
parser.add_argument('--minecraft-client',type=Path,default=Path(os.environ.get('APPDATA',str(Path.home()/'.config')))/'.minecraft/versions/26.3/26.3.jar')
GAME=parser.parse_args().minecraft_client
assert GAME.is_file(),'Supply the installed Minecraft 26.3 client with --minecraft-client.'
OUT=ROOT/'resourcepacks/Aster-Materials';OUT.mkdir(parents=True,exist_ok=True)
maps=[]
with ZipFile(GAME) as game:
    version=json.loads(game.read('version.json'))
    major=version['pack_version']['resource_major'];minor=version['pack_version']['resource_minor']
    for name,material in json.loads(TABLE.read_text())['materials'].items():
        strength=material.get('generatedRelief',0)
        if not strength:continue
        namespace,path=name.split(':')
        filename=f'assets/{namespace}/textures/{path}.png'
        image=Image.open(io.BytesIO(game.read(filename))).convert('RGBA')
        color=np.array(image,dtype=np.float64)
        height=(color[:,:,0]*.2126+color[:,:,1]*.7152+color[:,:,2]*.0722)/255.0
        nx=(np.roll(height,1,axis=1)-np.roll(height,-1,axis=1))*strength*3.0
        ny=(np.roll(height,1,axis=0)-np.roll(height,-1,axis=0))*strength*3.0
        length=np.sqrt(nx*nx+ny*ny+1.0)
        normals=np.stack([nx/length,ny/length,1/length],axis=2)
        encoded=np.floor((normals*.5+.5)*255+.5).clip(0,255).astype(np.uint8)
        rgba=np.concatenate([encoded,np.full((*encoded.shape[:2],1),255,dtype=np.uint8)],axis=2)
        target=OUT/filename.replace('.png','_n.png');target.parent.mkdir(parents=True,exist_ok=True)
        Image.fromarray(rgba).save(target)
        maps.append({'originalTexture':filename,'sourcePngSha256':hashlib.sha256(game.read(filename)).hexdigest(),
                     'normalTexture':str(target.relative_to(OUT)),'generatedRelief':strength,
                     'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
    (OUT/'pack.mcmeta').write_text(json.dumps({'pack':{'description':'Aster material relief for Sodium and Iris',
        'min_format':[major,minor],'max_format':[major,minor]}},indent=2)+'\n')
(OUT/'README.txt').write_text('Aster generated material relief. Normal maps reproduce the preserved MaterialAtlas.deriveNormal formula using the installed Minecraft textures. Diffuse textures are unchanged. This resource pack is prepared for local use with the Aster shader pack.\n')
archive=ROOT/'build/resourcepacks/Aster-Materials-0.2.0.zip';archive.parent.mkdir(parents=True,exist_ok=True)
with ZipFile(archive,'w',compression=ZIP_DEFLATED,compresslevel=9) as z:
    for p in sorted(OUT.rglob('*')):
        if p.is_file():
            entry=ZipInfo(p.relative_to(OUT).as_posix(),(2026,10,11,0,0,0));entry.compress_type=ZIP_DEFLATED
            z.writestr(entry,p.read_bytes())
receipt={'builtUtc':datetime.now(timezone.utc).isoformat(),'pack':str(archive),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
         'packVersion':[major,minor],'maps':maps,'diffuseTexturesIncluded':False,
         'sourceMaterialTableSha256':hashlib.sha256(TABLE.read_bytes()).hexdigest(),
         'sourceMaterialTable':str(TABLE.relative_to(ROOT)),
         'minecraftClient':str(GAME),'minecraftClientSha256':hashlib.sha256(GAME.read_bytes()).hexdigest(),
         'formulaSource':str((BASE/'src/client/java/dev/colin/aster/MaterialAtlas.java').relative_to(ROOT))}
path=ROOT/'tools/aster-sodium-migration/evidence/materials-current.json'
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps({'normalMaps':len(maps),'packVersion':[major,minor],'sha256':receipt['sha256']},indent=2))
