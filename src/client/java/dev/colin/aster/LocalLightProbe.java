package dev.colin.aster;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.util.ARGB;
import java.util.*;
/** Hidden native GPU measurements of fixed points in an enclosed fixture. */
public final class LocalLightProbe {
    private static final Map<String,Object> results=new LinkedHashMap<>();
    public static void capture(String label) {
        if(!AutomatedVerification.HIDDEN)return;
        var target=new TextureTarget("Aster local light visibility probe",6,1,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            ()->"Aster local light visibility probe",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.LOCAL_LIGHT_PROBE));
            AsterRenderer.INSTANCE.bindVerificationScene(pass);pass.draw(3,1,0,0);
        }
        Screenshot.takeScreenshot(target,image->{
            try {
                var values=new ArrayList<int[]>();
                for(int x=0;x<6;x++) {int pixel=image.getPixel(x,0);values.add(new int[]{ARGB.red(pixel),ARGB.green(pixel),ARGB.blue(pixel)});}
                results.put(label,values);
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+"-local-probe.png"));
            }catch(Exception ex){throw new RuntimeException(ex);}
            finally {image.close();target.destroyBuffers();}
        });
    }
    public static Object evidence(){return Map.copyOf(results);}
}
