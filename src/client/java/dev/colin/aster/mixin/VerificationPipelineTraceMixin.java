package dev.colin.aster.mixin;

import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.colin.aster.NativePipelineDiagnostics;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Private tracing with an explicitly selected host-serialization crash experiment. */
@Mixin(VulkanDevice.class)
public final class VerificationPipelineTraceMixin {
    @Unique private static final Object aster$compileLock = new Object();

    @WrapMethod(method="compilePipeline")
    private BackendRenderPipeline.Pending aster$compile(BackendRenderPipeline.CreateInfo info,
            Operation<BackendRenderPipeline.Pending> original) {
        if (!NativePipelineDiagnostics.enabled()) return original.call(info);
        if (NativePipelineDiagnostics.serialized()) {
            synchronized (aster$compileLock) { return aster$tracedCompile(info, original); }
        }
        return aster$tracedCompile(info, original);
    }

    @Unique
    private BackendRenderPipeline.Pending aster$tracedCompile(BackendRenderPipeline.CreateInfo info,
            Operation<BackendRenderPipeline.Pending> original) {
        NativePipelineDiagnostics.begin();
        LoggerFactory.getLogger("AsterPipelineTrace").info("Native compile begin {}", info.name());
        try {
            var pending = original.call(info);
            LoggerFactory.getLogger("AsterPipelineTrace").info("Native compile complete {}", info.name());
            return pending;
        } finally { NativePipelineDiagnostics.end(); }
    }
}
