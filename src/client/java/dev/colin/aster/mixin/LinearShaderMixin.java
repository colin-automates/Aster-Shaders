package dev.colin.aster.mixin;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import dev.colin.aster.NativeLinearShaders;
import net.minecraft.client.renderer.ShaderDefines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(GlslCompiler.class)
public class LinearShaderMixin {
    @ModifyVariable(method="compileToSpv",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private String aster$linear(String value, String name, String source, ShaderType type, ShaderDefines defines, ShaderSource includes) {
        return NativeLinearShaders.adapt(name,value,type,defines);
    }
}
