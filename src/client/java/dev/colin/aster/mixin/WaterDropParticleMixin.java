package dev.colin.aster.mixin;

import dev.colin.aster.AsterRainSplash;
import dev.colin.aster.AsterRenderer;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WaterDropParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterDropParticle.class)
public class WaterDropParticleMixin {
    @Inject(method="getLayer",at=@At("HEAD"),cancellable=true)
    private void aster$rainLayer(CallbackInfoReturnable<SingleQuadParticle.Layer> ci) {
        if(AsterRenderer.INSTANCE.effectsEnabled()) ci.setReturnValue(AsterRainSplash.layer());
    }
}
