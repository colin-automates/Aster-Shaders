package dev.colin.aster;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Measures host backend calls in private tests; it cannot measure driver-internal work. */
public final class NativePipelineDiagnostics {
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final AtomicInteger MAXIMUM = new AtomicInteger();
    private static final AtomicInteger TOTAL = new AtomicInteger();

    private NativePipelineDiagnostics() {}

    public static boolean enabled() {
        return AutomatedVerification.HIDDEN || Boolean.getBoolean("watercompare.hidden");
    }

    public static boolean serialized() {
        return enabled() && Boolean.getBoolean("aster.serialPipelineCompile");
    }

    public static void begin() {
        int active = ACTIVE.incrementAndGet();
        MAXIMUM.accumulateAndGet(active, Math::max);
        TOTAL.incrementAndGet();
    }

    public static void end() { ACTIVE.decrementAndGet(); }

    public static Object evidence() {
        return Map.of("enabled", enabled(), "serializedHostCalls", serialized(),
                "activeBackendCalls", ACTIVE.get(), "maxConcurrentBackendCalls", MAXIMUM.get(),
                "totalBackendCalls", TOTAL.get());
    }
}
