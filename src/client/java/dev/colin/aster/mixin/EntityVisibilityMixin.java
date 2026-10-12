package dev.colin.aster.mixin;

import dev.colin.aster.AsterRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityVisibilityMixin {
    @Inject(method="shouldRender",at=@At("RETURN"),cancellable=true)
    private void aster$nearbyCasters(Entity entity, Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        var mc=Minecraft.getInstance();
        if(!cir.getReturnValue() && AsterRenderer.INSTANCE.effectsEnabled() && mc.level != null && mc.level.dimension().equals(Level.OVERWORLD)
            && (AsterRenderer.INSTANCE.verificationLightingFlags()&524288) == 0
            && entity.distanceToSqr(x,y,z) < 96*96 && entity.shouldRender(x,y,z)) cir.setReturnValue(true);
    }
}
