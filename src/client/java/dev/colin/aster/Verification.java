package dev.colin.aster;

import com.google.gson.GsonBuilder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import dev.colin.aster.mixin.FrontendGpuDeviceAccessor;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class Verification {
    private Verification() {}

    public static void recordDevice(GpuDevice device) {
        var info = device.getDeviceInfo();
        var report = new LinkedHashMap<String, Object>();
        report.put("gpu", info.name());
        report.put("vendor", info.vendorName());
        report.put("driver", info.driverInfo());
        report.put("backend", info.backendName());
        report.put("depthZeroToOne", info.isZZeroToOne());
        report.put("timestampPeriod", info.timestampPeriod());
        report.put("engineFeatures", info.features());
        report.put("extensionsReportedByEngine", info.underlyingExtensions());
        if (device instanceof FrontendGpuDeviceAccessor access && access.aster$backend() instanceof VulkanDevice vk) {
            report.put("sharesMinecraftVulkanDevice", true);
            var caps = vk.vkDevice().getCapabilities();
            report.put("rayQueryExtensionEnabled", caps.VK_KHR_ray_query);
            report.put("accelerationStructureExtensionEnabled", caps.VK_KHR_acceleration_structure);
            report.put("graphicsQueueAvailable", vk.graphicsQueue() != null);
            report.put("computeQueueAvailable", vk.computeQueue() != null);
        }
        try {
            Path folder = FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("device.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            AsterClient.LOGGER.info("Verified backend {} on {} ({})", info.backendName(), info.name(), info.driverInfo());
        } catch (Exception ex) {
            AsterClient.LOGGER.error("Unable to save device verification", ex);
        }
    }
}
