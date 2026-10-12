package dev.colin.aster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.LinkedHashMap;
import java.util.Map;

/** Companion data atlases preserve native UVs, sprite padding, and draw batching. */
public final class MaterialAtlas implements AutoCloseable {
    private GpuTexture source, texture, normals;
    private GpuTextureView view, normalView;
    private int definitions, matchedSprites, rebuilds, materialMaps, normalMaps, generatedNormals;
    private final Map<String,Object> probes=new LinkedHashMap<>();
    private Object brdfProbe;
    private static final int NEUTRAL_NORMAL=ARGB.color(0,128,128,255);

    public void ensure(TextureAtlas atlas) {
        if (source == atlas.getTexture()) return;
        close();
        var nativeTexture=atlas.getTexture();
        int width=nativeTexture.getWidth(0),height=nativeTexture.getHeight(0);
        var merged=new LinkedHashMap<Identifier,JsonObject>();
        definitions=0; matchedSprites=0; materialMaps=0; normalMaps=0; generatedNormals=0;
        try {
            var resources=Minecraft.getInstance().getResourceManager();
            for(var resource:resources.getResourceStack(AsterPipelines.id("materials.json"))) {
                try(var reader=resource.openAsReader()) {
                    var root=JsonParser.parseReader(reader).getAsJsonObject();
                    if(root.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported Aster material schema");
                    for(var entry:root.getAsJsonObject("materials").entrySet()) {
                        definitions++;
                        var id=Identifier.parse(entry.getKey());
                        if(atlas.getSprite(id) == atlas.missingSprite()) continue;
                        matchedSprites++;
                        merged.put(id,entry.getValue().getAsJsonObject());
                    }
                }
            }
            for(var data:merged.values()) {
                if(data.has("materialMap")) materialMaps++;
                if(data.has("normalMap")) normalMaps++;
                else if(data.has("generatedRelief") && value(data,"generatedRelief",0,0,1)>0) generatedNormals++;
            }
            int materialLevels=materialMaps > 0 ? nativeTexture.getMipLevels() : 1;
            boolean normalData=normalMaps+generatedNormals>0;
            int normalLevels=normalData ? nativeTexture.getMipLevels() : 1;
            try(var image=new NativeImage(width,height,false);
                var normalImage=new NativeImage(normalData ? width : 1,normalData ? height : 1,false)) {
                image.fillRect(0,0,width,height,ARGB.color(0,204,0,0));
                normalImage.fillRect(0,0,normalImage.getWidth(),normalImage.getHeight(),NEUTRAL_NORMAL);
                for(var entry:merged.entrySet()) {
                    var data=entry.getValue();
                    float rough=value(data,"roughness",.8f,.045f,1);
                    float metal=value(data,"metalness",0,0,1);
                    float emission=value(data,"emission",0,0,8);
                    float scale=value(data,"normalScale",1,0,4);
                    int transmission=Math.round(value(data,"thinTransmission",0,0,1)*255);
                    var sprite=atlas.getSprite(entry.getKey());
                    int material=ARGB.color(transmission,Math.round(rough*255),Math.round(metal*255),Math.round(emission/8*255));
                    fillSprite(image,sprite,width,height,material,null,false,0);
                    if(normalData) fillSprite(normalImage,sprite,width,height,NEUTRAL_NORMAL,null,true,0);
                    if(data.has("materialMap")) try(var map=readMap(data.get("materialMap").getAsString())) {
                        fillSprite(image,sprite,width,height,material,map,false,0);
                    }
                    if(data.has("normalMap")) try(var map=readMap(data.get("normalMap").getAsString())) {
                        fillSprite(normalImage,sprite,width,height,0,map,true,scale);
                    } else if(data.has("generatedRelief")) {
                        float relief=value(data,"generatedRelief",0,0,1);
                        if(relief>0) try(var albedo=readMap(entry.getKey().withPath("textures/"+entry.getKey().getPath()+".png").toString());
                            var map=deriveNormal(albedo,relief)) {
                            fillSprite(normalImage,sprite,width,height,0,map,true,1);
                        }
                    }
                }
                var device=RenderSystem.getDevice();
                int usage=GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING;
                texture=device.createTexture("Aster roughness/metalness/emission atlas",usage,GpuFormat.RGBA8_UNORM,width,height,1,materialLevels);
                normals=device.createTexture("Aster tangent normal/scale atlas",usage,GpuFormat.RGBA8_UNORM,normalImage.getWidth(),normalImage.getHeight(),1,normalLevels);
                uploadPyramid(texture,image,false);
                uploadPyramid(normals,normalImage,true);
                view=device.createTextureView(texture);
                normalView=device.createTextureView(normals);
            }
            source=nativeTexture;
            rebuilds++;
            AsterClient.LOGGER.info("Material atlas rebuilt: {} definitions, {} matched; {} material maps, {} normal maps",definitions,matchedSprites,materialMaps,normalMaps);
        } catch(Exception ex) { close(); throw new IllegalStateException("Unable to build Aster material atlas",ex); }
    }
    private static float value(JsonObject data,String field,float fallback,float min,float max) {
        float result=data.has(field) ? data.get(field).getAsFloat() : fallback;
        if(!Float.isFinite(result) || result < min || result > max) throw new IllegalArgumentException("Invalid Aster material "+field);
        return result;
    }
    private static NativeImage readMap(String path) throws Exception {
        var id=Identifier.parse(path);
        if(!id.getPath().endsWith(".png")) throw new IllegalArgumentException("Aster material maps must be PNG: "+id);
        var resource=Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow(() -> new IllegalArgumentException("Missing Aster material map: "+id));
        try(var input=resource.open()) {
            var image=NativeImage.read(input);
            if(image.getWidth() > 4096 || image.getHeight() > 4096) { image.close(); throw new IllegalArgumentException("Material map exceeds 4096 pixels: "+id); }
            return image;
        }
    }
    private static void fillSprite(NativeImage destination,TextureAtlasSprite sprite,int atlasWidth,int atlasHeight,int constant,NativeImage map,boolean normal,float scale) {
        int x=Math.round(sprite.getU0()*atlasWidth),y=Math.round(sprite.getV0()*atlasHeight);
        int w=Math.round((sprite.getU1()-sprite.getU0())*atlasWidth),h=Math.round((sprite.getV1()-sprite.getV0())*atlasHeight);
        int padding=x-sprite.getX();
        if(map == null) { destination.fillRect(sprite.getX(),sprite.getY(),w+padding*2,h+padding*2,constant); return; }
        if((long)map.getWidth()*h != (long)map.getHeight()*w) throw new IllegalArgumentException("Static material map aspect ratio must match sprite: "+sprite);
        // Extend edge texels through the engine's padded sprite allocation.
        for(int dy=-padding;dy<h+padding;dy++) for(int dx=-padding;dx<w+padding;dx++) {
            int sx=Math.clamp(dx,0,w-1)*map.getWidth()/w,sy=Math.clamp(dy,0,h-1)*map.getHeight()/h;
            int pixel=map.getPixel(sx,sy);
            if(normal) {
                double nx=ARGB.red(pixel)/127.5-1,ny=ARGB.green(pixel)/127.5-1,nz=ARGB.blue(pixel)/127.5-1;
                double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(length < .001) { nx=0;ny=0;nz=1;length=1; }
                pixel=ARGB.color(Math.round(scale/4*255),encode(nx/length),encode(ny/length),encode(nz/length));
            } else pixel=ARGB.color(ARGB.alpha(constant),Math.max(12,ARGB.red(pixel)),ARGB.green(pixel),ARGB.blue(pixel));
            destination.setPixel(x+dx,y+dy,pixel);
        }
    }
    private static int encode(double n) { return Math.clamp((int)Math.round((n*.5+.5)*255),0,255); }
    private static NativeImage deriveNormal(NativeImage albedo,float relief) {
        int w=albedo.getWidth(),h=albedo.getHeight();
        var normal=new NativeImage(w,h,false);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            // Shallow relief from authored texture contrast, not added geometry.
            // Periodic taps preserve tile boundaries; mip vector variance then
            // broadens specular highlights when this relief becomes subpixel.
            double nx=(height(albedo,x-1,y)-height(albedo,x+1,y))*relief*3;
            double ny=(height(albedo,x,y-1)-height(albedo,x,y+1))*relief*3;
            double length=Math.sqrt(nx*nx+ny*ny+1);
            normal.setPixel(x,y,ARGB.color(255,encode(nx/length),encode(ny/length),encode(1/length)));
        }
        return normal;
    }
    private static double height(NativeImage image,int x,int y) {
        int p=image.getPixel(Math.floorMod(x,image.getWidth()),Math.floorMod(y,image.getHeight()));
        return (.2126*ARGB.red(p)+.7152*ARGB.green(p)+.0722*ARGB.blue(p))/255;
    }
    private static void uploadPyramid(GpuTexture target,NativeImage base,boolean normal) {
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        NativeImage current=base;
        try {
            for(int level=0;level<target.getMipLevels();level++) {
                encoder.writeToTexture(target,current,level,0,0,0);
                if(level+1 == target.getMipLevels()) break;
                var next=new NativeImage(target.getWidth(level+1),target.getHeight(level+1),false);
                for(int y=0;y<next.getHeight();y++) for(int x=0;x<next.getWidth();x++) {
                    double r=0,g=0,b=0,a=0;
                    for(int oy=0;oy<2;oy++) for(int ox=0;ox<2;ox++) {
                        int pixel=current.getPixel(Math.min(x*2+ox,current.getWidth()-1),Math.min(y*2+oy,current.getHeight()-1));
                        double red=ARGB.red(pixel);
                        r+=normal ? red : red*red;
                        g+=ARGB.green(pixel); b+=ARGB.blue(pixel); a+=ARGB.alpha(pixel);
                    }
                    // Average GGX alpha (perceptual roughness squared). Normal
                    // RGB retains mean vector length for shader variance filtering.
                    next.setPixel(x,y,ARGB.color((int)Math.round(a/4),(int)Math.round(normal ? r/4 : Math.sqrt(r/4)),(int)Math.round(g/4),(int)Math.round(b/4)));
                }
                if(current != base) current.close();
                current=next;
            }
        } finally { if(current != base) current.close(); }
    }
    public GpuTextureView view() { return view; }
    public GpuTextureView normalView() { return normalView; }
    public void captureProbe(String label,TextureAtlasSprite sprite) { captureProbe(label,sprite,.5f,.5f,0,false); }
    public void captureProbe(String label,TextureAtlasSprite sprite,float u,float v,int mip,boolean normal) {
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        var probe=new com.mojang.blaze3d.pipeline.TextureTarget("Aster material parameter probe",2,1,GpuFormat.RGBA8_UNORM,null);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var data=stack.malloc(16);
            data.putFloat(sprite.getU(u)).putFloat(sprite.getV(v)).putFloat(normal ? 1 : 0).putFloat(mip).flip();
            var uniform=encoder.transientMemory().uploadGpu(data,RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(),com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_UNIFORM);
            try(var pass=encoder.createRenderPass(() -> "Aster material parameter probe",probe.getColorTextureView(),java.util.Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.MATERIAL_PROBE));
                var sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST,true);
                pass.setUniform("MaterialAtlas",view,sampler); pass.setUniform("NormalAtlas",normalView,sampler);
                pass.setUniform("MaterialProbe",uniform); pass.draw(3,1,0,0);
            }
        }
        net.minecraft.client.Screenshot.takeScreenshot(probe,image -> {
            try {
                int pixel=image.getPixel(0,0);
                // Screenshot's conversion forces opaque alpha. The second
                // diagnostic pixel copies stored scale into RGB for readback.
                if(normal) probes.put(label,Map.of("x",ARGB.red(pixel)/127.5-1,"y",ARGB.green(pixel)/127.5-1,"z",ARGB.blue(pixel)/127.5-1,"scale",ARGB.red(image.getPixel(1,0))*4.0/255,"mip",mip));
                else probes.put(label,Map.of("roughness",ARGB.red(pixel)/255.0,"metalness",ARGB.green(pixel)/255.0,"emission",ARGB.blue(pixel)*8.0/255.0,"thinTransmission",ARGB.red(image.getPixel(1,0))/255.0,"mip",mip));
            } finally { image.close(); probe.destroyBuffers(); }
        });
    }
    private static long bytes(GpuTexture target) {
        long result=0;
        if(target != null) for(int i=0;i<target.getMipLevels();i++) result+=(long)target.getWidth(i)*target.getHeight(i)*4;
        return result;
    }
    public void captureBrdfProbe() {
        var probe=new com.mojang.blaze3d.pipeline.TextureTarget("Aster BRDF hemisphere probe",1,1,GpuFormat.RGBA8_UNORM,null);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        try(var pass=encoder.createRenderPass(() -> "Aster BRDF hemisphere probe",probe.getColorTextureView(),java.util.Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.BRDF_PROBE)); pass.draw(3,1,0,0);
        }
        net.minecraft.client.Screenshot.takeScreenshot(probe,image -> {
            try {
                int pixel=image.getPixel(0,0);
                brdfProbe=Map.of("frontPositive",ARGB.red(pixel) == 255,"backEyeDark",ARGB.green(pixel) == 255,"backLightDark",ARGB.blue(pixel) == 255);
            } finally { image.close();probe.destroyBuffers(); }
        });
    }
    public Object evidence() {
        var result=new LinkedHashMap<String,Object>(); result.put("definitions",definitions); result.put("matchedSprites",matchedSprites);
        result.put("rebuilds",rebuilds); result.put("materialMaps",materialMaps); result.put("normalMaps",normalMaps);result.put("generatedNormals",generatedNormals);
        result.put("materialMipLevels",texture == null ? 0 : texture.getMipLevels()); result.put("normalMipLevels",normals == null ? 0 : normals.getMipLevels());
        result.put("atlasWidth",texture == null ? 0 : texture.getWidth(0)); result.put("atlasHeight",texture == null ? 0 : texture.getHeight(0));
        result.put("allocatedTextureBytes",bytes(texture)+bytes(normals));
        result.put("format","RGBA8_UNORM material data: roughness, metalness, emission / 8, thin transmission; tangent normal data: XYZ, scale / 4");
        result.put("probes",new LinkedHashMap<>(probes)); result.put("brdfHemisphereProbe",brdfProbe); return result;
    }
    @Override public void close() {
        if(view != null) { view.close();view=null; } if(normalView != null) { normalView.close();normalView=null; }
        if(texture != null) { texture.close();texture=null; } if(normals != null) { normals.close();normals=null; } source=null;
    }
}
