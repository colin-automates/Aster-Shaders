package dev.colin.aster;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in fixed-scene CPU recording and non-blocking GPU render-scope timing. */
public final class FrameProfiler {
    private static final GpuQueryPool[] pools = new GpuQueryPool[8];
    private static final boolean[] pending = new boolean[8];
    private static final int[] issuedFrame = new int[8];
    private static final String[] tags = new String[8];
    private static final Map<String,List<Double>> cpu = new LinkedHashMap<>(), gpu = new LinkedHashMap<>();
    private static String tag, activeTag;
    private static int frame, activeSlot = -1;
    private static long cpuStarted;
    private FrameProfiler() {}
    public static String currentTag() { return tag; }
    public static void setTag(String value) { tag = value; }

    public static void begin() {
        if (!AutomatedVerification.HIDDEN) return;
        frame++;
        var device = RenderSystem.getDevice();
        for (int i=0;i<pools.length;i++) {
            if (!pending[i] || frame-issuedFrame[i] < pools.length) continue;
            var values = pools[i].getValues(0,2);
            if (values[0].isPresent() && values[1].isPresent()) {
                double ms = (values[1].getAsLong()-values[0].getAsLong())*device.getDeviceInfo().timestampPeriod()/1_000_000.0;
                gpu.computeIfAbsent(tags[i],key -> new ArrayList<>()).add(ms);
                pending[i] = false;
            }
        }
        activeTag = tag;
        activeSlot = -1;
        if (activeTag == null) return;
        cpuStarted = System.nanoTime();
        int slot = frame%pools.length;
        if (pending[slot]) return;
        if (pools[slot] == null) pools[slot] = device.createTimestampQueryPool(2);
        activeSlot = slot;
        tags[slot] = activeTag;
        issuedFrame[slot] = frame;
        device.createCommandEncoder().writeTimestamp(pools[slot],0);
    }
    public static void end() {
        if (activeTag == null) return;
        cpu.computeIfAbsent(activeTag,key -> new ArrayList<>()).add((System.nanoTime()-cpuStarted)/1_000_000.0);
        if (activeSlot >= 0) {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pools[activeSlot],1);
            pending[activeSlot] = true;
        }
        activeTag = null;
        activeSlot = -1;
    }
    public static void save() {
        var report = new LinkedHashMap<String,Object>();
        report.put("gpu",RenderSystem.getDevice().getDeviceInfo().name());
        report.put("backend",RenderSystem.getDevice().getDeviceInfo().backendName());
        report.put("resolution",new int[]{Minecraft.getInstance().gameRenderer.mainRenderTarget().width,Minecraft.getInstance().gameRenderer.mainRenderTarget().height});
        report.put("renderDistanceChunks",Minecraft.getInstance().options.renderDistance().get());
        report.put("frameCap",30);
        report.put("scope","GameRenderer.render CPU recording and GPU render commands, including hand/HUD. Excludes CPU extraction, chunk worker threads, presentation, and FPS limiting. These are render-scope times, not measured gameplay FPS.");
        var cases = new LinkedHashMap<String,Object>();
        for (var key : cpu.keySet()) {
            var result = new LinkedHashMap<String,Object>();
            result.put("cpuRender",statistics(cpu.get(key)));
            result.put("gpuRender",statistics(gpu.getOrDefault(key,List.of())));
            cases.put(key,result);
        }
        report.put("cases",cases);
        report.put("effectPassTimings",AsterRenderer.INSTANCE.groupTimingEvidence());
        try {
            var file = FabricLoader.getInstance().getGameDir().resolve("aster-evidence/frame-profiling.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private static Object statistics(List<Double> samples) {
        var result = new LinkedHashMap<String,Object>();
        result.put("samples",samples.size());
        if (!samples.isEmpty()) {
            var sorted = samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            result.put("medianMs",sorted[sorted.length/2]);
            result.put("p95Ms",sorted[Math.min(sorted.length-1,(int)(sorted.length*0.95))]);
        }
        return result;
    }
    public static void close() {
        for (int i=0;i<pools.length;i++) if (pools[i] != null) { pools[i].close(); pools[i] = null; pending[i] = false; }
        tag = null;
        activeTag = null;
    }
}
