package dev.colin.aster;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsterClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("AsterVulkan");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Aster Vulkan initialized; native backend verification is pending renderer creation.");
        AutomatedVerification.register();
    }
}
