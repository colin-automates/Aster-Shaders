package dev.colin.aster;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.sdl.SDLVideo;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeycode;

/** Opt-in, bounded verification. The test window is hidden and cannot receive focus or grab input. */
public final class AutomatedVerification {
    public static final boolean HIDDEN = Boolean.getBoolean("aster.hiddenVerification");
    private static final String PROFILE = System.getProperty("aster.verificationProfile", "fixture");
    private static final long STARTED_AT = System.nanoTime();
    private static boolean started;
    private static boolean fixtureStarted;
    private static CompletableFuture<Void> fixture;
    private static CompletableFuture<Void> profileFixture;
    private static int frames;
    private static boolean keyboardToggleVerified;
    private static final List<String> captures = new ArrayList<>();
    private static final Map<String,Object> scenes = new LinkedHashMap<>();
    private static final LinkedHashSet<String> gpuMessages = new LinkedHashSet<>();
    private static CompletableFuture<Void> resourceReload;
    private static CompletableFuture<Boolean> waterFloorCheck;
    private static boolean resourceReloadVerified;
    private static int naturalX, naturalY, naturalZ;
    private static int waterResetCaptures;
    private static int fogResetCaptures;
    private static float controlledBobPhase = -1;

    /** Drive native bobView itself in the private matched-phase comparison. */
    public static void prepareWorldCamera() {
        if (!HIDDEN || !(PROFILE.equals("waterbob") || PROFILE.equals("waterbobcoast")) || controlledBobPhase < 0) return;
        var entity=Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.entityRenderState;
        entity.bob=.075f;
        entity.backwardsInterpolatedWalkDistance=controlledBobPhase;
    }


    public static void register() {
        if (HIDDEN) ClientTickEvents.END_CLIENT_TICK.register(AutomatedVerification::tick);
    }
    private static void tick(Minecraft mc) {
        if (System.nanoTime()-STARTED_AT > 240_000_000_000L) {
            AsterClient.LOGGER.error("Automated verification exceeded its 240 second time limit.");
            mc.stop();
            return;
        }
        if (!started && mc.gui.overlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            // Explicitly enforce runtime values: the startup graphics preset
            // can overwrite individual values read from options.txt.
            mc.options.graphicsPreset().set(GraphicsPreset.CUSTOM);
            mc.options.renderDistance().set(PROFILE.equals("fixture") ? 6 : PROFILE.equals("watergroups") ? 16 : (PROFILE.equals("waterdistance") || PROFILE.equals("waterbobcoast") || PROFILE.equals("wateredgecoast")) ? 32 : (PROFILE.equals("watercoast") || PROFILE.equals("waterbobcoast") || PROFILE.equals("wateredgecoast")) ? 16 : PROFILE.equals("benchmark") ? 12 : 8);
            mc.options.simulationDistance().set(5);
            mc.options.improvedTransparency().set(false);
            var settings = new LevelSettings("Aster automated fixture", GameType.SPECTATOR,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, true, false), true, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("aster-check-"+System.currentTimeMillis(), settings,
                new WorldOptions((PROFILE.equals("watercoast") || PROFILE.equals("waterbobcoast") || PROFILE.equals("wateredgecoast")) ? -8179928118184774485L : 9070L, false, false), provider -> provider.lookupOrThrow(Registries.WORLD_PRESET)
                    .getOrThrow((PROFILE.equals("natural") || PROFILE.equals("fogredesign")) || PROFILE.equals("benchmark") || PROFILE.equals("foliage") || PROFILE.equals("leafgrain") || PROFILE.equals("sky") || PROFILE.equals("clouds") || (PROFILE.equals("watercoast") || PROFILE.equals("waterbobcoast") || PROFILE.equals("wateredgecoast")) ? WorldPresets.NORMAL : WorldPresets.FLAT).value().createWorldDimensions(), mc.gui.screen());
        }
        if (mc.level != null && mc.player != null && !fixtureStarted) {
            fixtureStarted = true;
            fixture = new CompletableFuture<>();
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    if ((PROFILE.equals("natural") || PROFILE.equals("fogredesign")) || PROFILE.equals("benchmark") || PROFILE.equals("foliage") || PROFILE.equals("leafgrain") || PROFILE.equals("sky") || PROFILE.equals("clouds") || (PROFILE.equals("watercoast") || PROFILE.equals("waterbobcoast") || PROFILE.equals("wateredgecoast"))) {
                        naturalX = 0;
                        naturalZ = 8;
                        // A heightmap query can race decoration/tree generation.
                        // Use a known safe fixed height for repeatable captures.
                        naturalY = 91;
                        String[] naturalCommands = {
                            "gamerule minecraft:advance_time false", "gamerule minecraft:advance_weather false",
                            "gamerule minecraft:spawn_mobs false", "time set 4000", "weather clear",
                            "tp AsterCheck "+naturalX+" "+naturalY+" "+naturalZ+" 140 22"
                        };
                        for (String command : naturalCommands) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                        if((PROFILE.equals("watercoast") || PROFILE.equals("waterbobcoast") || PROFILE.equals("wateredgecoast"))) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"tp AsterCheck -300 85 440 -145 18");
                        if(PROFILE.equals("waterbobcoast")) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"tp AsterCheck -1336.251639 64 300.425630 -52.536804 11.250128");
                        if(PROFILE.equals("wateredgecoast")) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"tp AsterCheck -1531.612405 63 119.064481 -166.989944 10.500109");
                        fixture.complete(null);
                        AsterClient.LOGGER.info("Natural verification ready at {}, {}, {}", naturalX, naturalY, naturalZ);
                        return;
                    }
                    String[] commands = {
                        "gamerule minecraft:advance_time false", "gamerule minecraft:advance_weather false",
                        "gamerule minecraft:spawn_mobs false", "time set 4000", "weather clear",
                        "fill -32 63 -32 32 64 32 minecraft:grass_block",
                        "fill -12 64 -6 8 64 12 minecraft:stone_bricks",
                        "fill -12 65 -6 -9 72 -3 minecraft:stone_bricks",
                        "fill 4 65 -6 7 76 -3 minecraft:stone_bricks",
                        "fill -12 73 -6 7 73 -3 minecraft:stone_bricks",
                        "fill -6 65 -8 -6 70 -8 minecraft:oak_log",
                        "fill -9 70 -11 -3 73 -5 minecraft:oak_leaves[persistent=true]",
                        "fill 10 62 -4 24 62 16 minecraft:sandstone",
                        "fill 10 63 -4 24 63 16 minecraft:sand",
                        "fill 11 64 -3 23 64 15 minecraft:water",
                        "fill -2 65 8 2 68 8 minecraft:glass",
                        "setblock -4 65 8 minecraft:glowstone", "setblock -4 65 10 minecraft:torch",
                        "summon minecraft:cow -3 65 4 {NoAI:1b}",
                        "summon minecraft:cow 2 65 4 {NoAI:1b,Glowing:1b}",
                        "tp AsterCheck 24 73 28 140 18"
                    };
                    for (String command : commands) {
                        prepareConstructionChunks(server.overworld(),command);
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                    }
                    fixture.complete(null);
                    AsterClient.LOGGER.info("Automated verification fixture ready.");
                } catch (Throwable ex) { fixture.completeExceptionally(ex); }
            });
        }
    }

    public static void afterRender() {
        if (!HIDDEN || fixture == null || !fixture.isDone()) return;
        fixture.join();
        if(profileFixture!=null) {
            if(!profileFixture.isDone())return;
            profileFixture.join();profileFixture=null;
        }
        for (var message : RenderSystem.getDevice().getLastDebugMessages()) gpuMessages.add(String.valueOf(message));
        if(PROFILE.equals("water") || PROFILE.equals("lifecycle")) {
            @SuppressWarnings("unchecked") var water=(Map<String,Object>)AsterRenderer.INSTANCE.waterEvidence();
            if(!((List<?>)water.get("lastResetReasons")).isEmpty() && AsterRenderer.INSTANCE.effectsEnabled())
                AsterRenderer.INSTANCE.captureWater("reset-"+(++waterResetCaptures),false);
        }
        if(PROFILE.equals("fog") || PROFILE.equals("lifecycle")) {
            @SuppressWarnings("unchecked") var fog=(Map<String,Object>)AsterRenderer.INSTANCE.fogEvidence();
            if(!((List<?>)fog.get("lastResetReasons")).isEmpty() && AsterRenderer.INSTANCE.effectsEnabled())
                AsterRenderer.INSTANCE.captureFog("reset-"+(++fogResetCaptures),false);
        }
        if (resourceReload != null) {
            if (!resourceReload.isDone()) return;
            resourceReload.join();
            resourceReloadVerified = true;
            resourceReload = null;
        }
        frames++;
        if (PROFILE.equals("taa")) { temporalAaFrames(); return; }
        if (PROFILE.equals("solidreflections")) { solidReflectionFrames(); return; }
        // Large-distance chunk compilation is asynchronous. Do not compare
        // range while the fixture's distant towers are still missing meshes.
        if(PROFILE.equals("waterdistance") && frames == 580 && !distanceFixtureReady()) {frames--;return;}
        if (PROFILE.equals("fogredesign")) { fogRedesignFrames(); return; }
        if (PROFILE.equals("clouds")) { cloudFrames(); return; }
        if (PROFILE.equals("sky")) { skyFrames(); return; }
        if ((PROFILE.equals("natural") || PROFILE.equals("fogredesign"))) { naturalFrames(); return; }
        if (PROFILE.equals("benchmark")) { benchmarkFrames(); return; }
        if (PROFILE.equals("foliage")) { foliageFrames(); return; }
        if (PROFILE.equals("leafgrain")) { leafGrainFrames(); return; }
        if (PROFILE.equals("watercoast")) { waterCoastFrames(); return; }
        if (PROFILE.equals("waterbob")) { waterBobFrames(); return; }
        if (PROFILE.equals("locallights")) { localLightFrames(); return; }
        if (PROFILE.equals("waterbobcoast")) { waterBobCoastFrames(); return; }
        if (PROFILE.equals("wateredgecoast")) { waterEdgeCoastFrames(); return; }
        if (frames == 75) {
            waterFloorCheck = new CompletableFuture<>();
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> waterFloorCheck.complete(
                server.overworld().getBlockState(new BlockPos(17,63,8)).is(Blocks.SAND)
                && server.overworld().getBlockState(new BlockPos(17,62,8)).is(Blocks.SANDSTONE)));
        }
        if (frames == 80 && !Boolean.getBoolean("aster.baselineVerification")) AsterRenderer.INSTANCE.captureShadowDebug();
        if (frames == 90) { capture("day-enabled"); toggleByKey(); keyboardToggleVerified = !AsterRenderer.INSTANCE.effectsEnabled(); }
        if (frames == 140) { capture("day-disabled"); toggleByKey(); keyboardToggleVerified &= AsterRenderer.INSTANCE.effectsEnabled(); }
        if (frames == 145) Minecraft.getInstance().options.improvedTransparency().set(true);
        if (frames == 205) capture("day-oit");
        if (frames == 210) command("time set midnight");
        if (frames == 260) capture("night");
        if (frames == 265) { command("time set 4000"); command("tp AsterCheck 17 63 8 150 0"); }
        if (frames == 320) capture("underwater");
        if (frames == 320 && PROFILE.equals("fog")) AsterRenderer.INSTANCE.captureFogPhysicsProbe("underwater");
        if (PROFILE.equals("lifecycle")) {
            lifecycleFrames();
        } else if (PROFILE.equals("waterlighting")) {
            waterLightingFrames();
        } else if (PROFILE.equals("watergroups")) {
            waterGroupFrames();
        } else if (PROFILE.equals("wateredge")) {
            waterEdgeFrames();
        } else if (PROFILE.equals("wind")) {
            windFrames();
        } else if (PROFILE.equals("playershadow")) {
            playerShadowFrames();
        } else if (PROFILE.equals("entitypolish")) {
            entityPolishFrames();
        } else if (PROFILE.equals("entities")) {
            entityFrames();
        } else if (PROFILE.equals("lighting")) {
            lightingFrames();
        } else if (PROFILE.equals("pbr")) {
            materialFrames();
        } else if (PROFILE.equals("maps")) {
            mapFrames();
        } else if (PROFILE.equals("water")) {
            waterFrames();
        } else if (PROFILE.equals("waterlook")) {
            waterLookFrames();
        } else if (PROFILE.equals("waves")) {
            waveFrames();
        } else if (PROFILE.equals("reflections")) {
            reflectionFrames();
        } else if (PROFILE.equals("watermotion")) {
            waterMotionFrames();
        } else if (PROFILE.equals("waterhybrid")) {
            waterHybridFrames();
        } else if (PROFILE.equals("waterdistance")) {
            waterDistanceFrames();
        } else if (PROFILE.equals("fog")) {
            fogFrames();
        } else if (frames == 350) finish();
    }

    private static void captureWaterLook(String label) {
        capture(label);AsterRenderer.INSTANCE.captureWater(label,true);
    }
    private static void reflectionQuality(int percent,int flags) {
        AsterRenderer.INSTANCE.setVerificationReflectionResolution(percent);
        AsterRenderer.INSTANCE.setVerificationLightingFlags(flags);
    }
    private static void waterMotionFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);
            command("fill 40 58 -16 68 64 18 minecraft:sandstone");
            command("fill 41 60 -15 67 64 17 minecraft:air");
            command("fill 41 63 -15 53 63 17 minecraft:sand");
            command("fill 41 64 -15 53 64 17 minecraft:water");
            command("fill 54 59 -15 67 59 17 minecraft:sand");
            command("fill 54 60 -15 67 64 17 minecraft:water");
            command("fill 42 65 -9 46 76 -7 minecraft:gold_block");
            command("fill 51 65 -9 55 73 -7 minecraft:stone_bricks");
            command("fill 62 65 -9 66 79 -7 minecraft:iron_block");
            // A diagonal corner, a raised solid bank, and a native flow outlet.
            command("fill 41 64 7 43 65 9 minecraft:stone_bricks");
            command("fill 72 60 -3 78 63 6 minecraft:sandstone");
            command("fill 73 61 -2 77 63 5 minecraft:air");
            command("setblock 74 64 2 minecraft:water");
            reflectionQuality(75,8192);renderer.setVerificationWaterTime(12f);
            command("tp AsterCheck 54.5 65.1 15.5 180 8");
        }
        for(int version=0;version<2;version++) {
            int start=version == 0 ? 420 : 570;
            if(frames == start) { reflectionQuality(75,version == 0 ? 8192 : 0);command("tp AsterCheck 54.5 65.1 15.5 180 8"); }
            if(frames >= start && frames <= start+75) {
                int n=frames-start;renderer.setVerificationWaterTime(12f+n/30f);
                if(n%3 == 0) command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 65.1 15.5 %.4f %.4f",54.5+n*.02,180+18*Math.sin(n*.05),8+18*Math.sin(n*.028)));
            }
            if(frames >= start+30 && frames <= start+75 && (frames-start-30)%3 == 0)
                captureWaterLook("watermotion-"+(version == 0 ? "before" : "after")+"-"+((frames-start-30)/3));
        }
        if(frames == 690) command("tp AsterCheck 45.5 65.15 13.5 90 25");
        for(int version=0;version<2;version++) for(int phase=0;phase<4;phase++) {
            int sample=740+version*200+phase*40;
            if(frames == sample-30) { reflectionQuality(75,version == 0 ? 8192 : 0);renderer.setVerificationWaterTime(phase*2f); }
            if(frames == sample) {
                String label="waterbank-"+(version == 0 ? "before" : "after")+"-"+phase;
                captureWaterLook(label);renderer.captureWaterContactProbe(label);
            }
        }
        if(frames == 1100) { reflectionQuality(75,0);command("tp AsterCheck 76.5 65.1 8.5 180 28"); }
        if(frames == 1180) captureWaterLook("watermotion-flow");
        if(frames == 1210) { command("tp AsterCheck 54.5 65.1 15.5 180 8");mc.options.improvedTransparency().set(true);renderer.setVerificationWaterTime(12f); }
        if(frames == 1280) { captureWaterLook("watermotion-oit");renderer.captureWaterWaveProbe();renderer.captureWaterOpticsProbe(); }
        if(frames == 1290) { mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577); }
        if(frames == 1340) captureWaterLook("watermotion-resized");
        if(frames == 1350) resourceReload=mc.reloadResourcePacks();
        if(frames == 1420) captureWaterLook("watermotion-reloaded");
        if(frames == 1440) { FrameProfiler.save();finish(); }
    }
    private static void waterHybridFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            command("fill 40 58 -16 68 64 18 minecraft:stone");
            command("fill 41 60 -15 67 64 17 minecraft:air");
            command("fill 41 63 -15 53 63 17 minecraft:sand");
            command("fill 41 64 -15 53 64 17 minecraft:water");
            command("fill 54 59 -15 67 59 17 minecraft:sand");
            command("fill 54 60 -15 67 64 17 minecraft:water");
            command("fill 42 65 -9 46 76 -7 minecraft:gold_block");
            command("fill 51 65 -9 55 73 -7 minecraft:stone_bricks");
            command("fill 62 65 -9 66 79 -7 minecraft:iron_block");
            command("fill 40 65 -16 68 67 -14 minecraft:stone");
            command("fill 40 65 -16 40 67 18 minecraft:stone");
            command("fill 68 65 -16 68 67 18 minecraft:stone");
            command("fill 41 64 7 43 65 9 minecraft:stone_bricks");
            command("time set 12000");renderer.setVerificationWaterTime(12f);
        }
        for(int version=0;version<2;version++) {
            int start=420+version*160;
            if(frames == start) {reflectionQuality(75,version == 0 ? 196608 : 0);command("tp AsterCheck 54.5 65.1 15.5 180 45");}
            if(frames>=start && frames<=start+104 && (frames-start)%4 == 0) {
                int n=frames-start;renderer.setVerificationWaterTime(12f+n/30f);
                command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 65.1 15.5 180 45",54.5+2*Math.sin(n*.045)));
            }
            if(frames>=start+48 && frames<=start+104 && (frames-start-48)%8 == 0) {
                String label="hybrid-motion-"+(version == 0 ? "before" : "after")+"-"+((frames-start-48)/8);
                captureWaterLook(label);renderer.captureWaterSourceProbe(label);
            }
        }
        if(frames == 740) {reflectionQuality(75,196608);command("tp AsterCheck 45.5 64.95 13.5 90 8");renderer.setVerificationWaterTime(2f);}
        if(frames == 800) captureWaterLook("hybrid-bank-before");
        if(frames == 810) reflectionQuality(75,0);
        if(frames == 870) {captureWaterLook("hybrid-bank-after");renderer.captureWaterContactProbe("hybrid-bank-after");}
        if(frames == 880) {command("time set 18000");reflectionQuality(75,196608);}
        if(frames == 940) captureWaterLook("hybrid-night-before");
        if(frames == 950) reflectionQuality(75,0);
        if(frames == 1010) captureWaterLook("hybrid-night-after");
        if(frames == 1020) {command("time set 4000");command("tp AsterCheck 54.5 65.1 15.5 180 45");renderer.setVerificationWaterTime(12f);reflectionQuality(75,196608);}
        if(frames == 1080) {captureWaterLook("hybrid-day-before");renderer.captureWaterSourceProbe("hybrid-day-before");}
        if(frames == 1090) reflectionQuality(75,0);
        if(frames == 1150) {captureWaterLook("hybrid-day-after");renderer.captureWaterSourceProbe("hybrid-day-after");renderer.captureWaterWaveProbe();renderer.captureWaterOpticsProbe();}
        if(frames == 1160) mc.options.improvedTransparency().set(true);
        if(frames == 1220) captureWaterLook("hybrid-oit");
        if(frames == 1230) {mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577);}
        if(frames == 1290) captureWaterLook("hybrid-resized");
        if(frames == 1300) resourceReload=mc.reloadResourcePacks();
        if(frames == 1380) captureWaterLook("hybrid-reloaded");
        if(frames == 1390) command("tp AsterCheck 54.5 62.8 15.5 180 0");
        if(frames == 1450) captureWaterLook("hybrid-underwater");
        if(frames == 1460) command("tp AsterCheck 54.5 65.1 15.5 180 45");
        if(frames == 1520) captureWaterLook("hybrid-return");
        if(frames == 1530) {reflectionQuality(75,196608);FrameProfiler.setTag("hybrid-before");}
        if(frames == 1830) {FrameProfiler.setTag(null);reflectionQuality(75,0);}
        if(frames == 1930) FrameProfiler.setTag("hybrid-after");
        if(frames == 2230) FrameProfiler.setTag(null);
        if(frames == 2250) {mc.getWindow().setWindowed(1280,720);command("time set 12000");command("tp AsterCheck 45.5 64.95 13.5 90 8");renderer.setVerificationWaterTime(2f);reflectionQuality(75,131072);}
        if(frames == 2310) captureWaterLook("hybrid-bank-source-only");
        if(frames == 2320) reflectionQuality(75,65536);
        if(frames == 2380) captureWaterLook("hybrid-bank-contact-only");
        if(frames == 2390) reflectionQuality(75,0);
        if(frames == 2450) captureWaterLook("hybrid-bank-combined");
        if(frames == 2460) reflectionQuality(75,262144);
        if(frames == 2530) captureWaterLook("hybrid-direct");
        if(frames == 2550) {reflectionQuality(75,0);FrameProfiler.save();finish();}
    }
    private static void bobCapture(String label) {
        distanceCapture(label);AsterRenderer.INSTANCE.captureWaterContactProbe(label);
    }
    private static void strafe(boolean left,boolean right) {
        var options=Minecraft.getInstance().options;
        options.keyLeft.setDown(left);options.keyRight.setDown(right);
    }
    private static void waterBobFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 1) {
            mc.options.bobView().set(true);renderer.setVerificationWaterTime(12f);
            profileFixture=new CompletableFuture<>();
            var setup=profileFixture;var server=mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    // Fill rejects unloaded endpoints. Finish generation before
                    // constructing the bank and entering a grounded game mode.
                    var level=server.overworld();
                    for(int cx=2;cx<=4;cx++)for(int cz=-8;cz<=1;cz++)level.getChunk(cx,cz);
                    for(String c:List.of("fill 38 63 -116 78 65 22 minecraft:sandstone",
                        "fill 40 64 -114 76 65 12 minecraft:air","fill 40 64 -114 76 64 12 minecraft:water",
                        "fill 44 65 -18 47 78 -15 minecraft:gold_block","fill 56 65 -18 59 75 -15 minecraft:stone_bricks",
                        "fill 68 65 -18 71 81 -15 minecraft:iron_block","fill 44 65 -94 47 91 -91 minecraft:gold_block",
                        "fill 68 65 -110 71 102 -107 minecraft:iron_block","tp AsterCheck 58 66 16.5 180 11",
                        "gamemode adventure AsterCheck"))server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),c);
                    // A few narrow piers give the low-resolution GPU probe
                    // enough native bank samples throughout the walking span.
                    // Its height-error and anchor-count thresholds are unchanged.
                    for(int x:new int[]{50,62})server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "fill "+x+" 64 10 "+(x+1)+" 65 12 minecraft:sandstone");
                    // Keep the qualified concave shore samples and root's
                    // asynchronous generation/native fixture assertions.
                    for(int x=42;x<=72;x+=6)for(String c:List.of(
                        "fill "+x+" 64 7 "+(x+2)+" 64 7 minecraft:sandstone",
                        "fill "+x+" 64 7 "+x+" 64 9 minecraft:sandstone"))
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),c);
                    if(!level.getBlockState(new BlockPos(58,65,16)).is(Blocks.SANDSTONE)
                        || !level.getBlockState(new BlockPos(58,63,0)).is(Blocks.SANDSTONE)
                        || !level.getFluidState(new BlockPos(58,64,0)).is(net.minecraft.tags.FluidTags.WATER))
                        throw new IllegalStateException("Grounded walking platform and water fixture failed native assertions");
                    setup.complete(null);
                }catch(Throwable ex){setup.completeExceptionally(ex);}
            });
        }
        if(frames == 40) {toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames == 45) {toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        for(int i=0;i<16;i++) {
            int start=200+i*36;
            if(frames == start) {controlledBobPhase=i*.25f;reflectionQuality(75,16777216);}
            if(frames == start+16) bobCapture("bob-before-"+i);
            if(frames == start+18) reflectionQuality(75,0);
            if(frames == start+34) bobCapture("bob-after-"+i);
        }
        if(frames == 790) {controlledBobPhase=-1;reflectionQuality(75,16777216);}
        // These keys belong only to the hidden client's simulation; no OS input is sent.
        if(frames == 820) {command("tp AsterCheck 58 66 16.5 180 11");strafe(true,false);}
        if(frames == 910) strafe(false,true);
        if(frames >= 849 && frames <= 999 && (frames-849)%15==0) bobCapture("walk-before-"+((frames-849)/15));
        if(frames == 1000) strafe(false,false);
        if(frames == 1030) {reflectionQuality(75,0);command("tp AsterCheck 58 66 16.5 180 11");}
        if(frames == 1060) strafe(true,false);
        if(frames == 1150) strafe(false,true);
        if(frames >= 1089 && frames <= 1239 && (frames-1089)%15==0) bobCapture("walk-after-"+((frames-1089)/15));
        if(frames == 1240) strafe(false,false);
        if(frames == 1260) {mc.options.improvedTransparency().set(true);command("tp AsterCheck 58 66 16.5 180 11");}
        if(frames == 1290) strafe(true,false);
        if(frames == 1350) strafe(false,true);
        if(frames >= 1319 && frames <= 1409 && (frames-1319)%15==0) bobCapture("walk-oit-"+((frames-1319)/15));
        if(frames == 1410) {strafe(false,false);mc.options.improvedTransparency().set(false);reflectionQuality(75,262144);command("tp AsterCheck 58 66 16.5 180 11");}
        if(frames == 1440) strafe(true,false);
        if(frames == 1500) strafe(false,true);
        if(frames >= 1469 && frames <= 1559 && (frames-1469)%15==0) bobCapture("walk-direct-"+((frames-1469)/15));
        if(frames == 1560) {strafe(false,false);reflectionQuality(75,0);command("gamemode spectator AsterCheck");command("tp AsterCheck 58 66 16.5 180 11");}
        if(frames == 1600) strafe(true,false);
        // Keep native spectator flight within the bank's inspection span.
        // Its travel speed is higher than the grounded walking sequences.
        if(frames == 1640) strafe(false,true);
        if(frames == 1690) strafe(false,false);
        if(frames >= 1629 && frames <= 1704 && (frames-1629)%25==0) bobCapture("air-"+((frames-1629)/25));
        if(frames == 1710) {strafe(false,false);command("gamemode adventure AsterCheck");command("tp AsterCheck 58 66 16.5 180 11");mc.options.bobView().set(false);}
        if(frames == 1750) strafe(true,false);
        if(frames == 1800) strafe(false,true);
        if(frames >= 1779 && frames <= 1854 && (frames-1779)%25==0) bobCapture("bob-off-"+((frames-1779)/25));
        if(frames == 1860) {strafe(false,false);mc.options.bobView().set(true);mc.getWindow().setWindowed(1025,577);controlledBobPhase=.375f;}
        if(frames == 1900) {bobCapture("bob-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames == 1980) bobCapture("bob-reloaded");
        if(frames == 2000) {controlledBobPhase=-1;strafe(false,false);FrameProfiler.save();finish();}
    }

    private static void waterBobCoastFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 1) {mc.options.bobView().set(true);renderer.setVerificationWaterTime(12f);renderer.setVerificationWindTime(12f);renderer.setVerificationCloudAngle(.02f);command("time set 11000");}
        if(frames == 40) {toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames == 45) {toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        for(int i=0;i<16;i++) {
            int start=200+i*36;
            if(frames == start) {controlledBobPhase=i*.25f;reflectionQuality(75,16777216);}
            if(frames == start+16) distanceCapture("coast-bob-before-"+i);
            if(frames == start+18) reflectionQuality(75,0);
            if(frames == start+34) distanceCapture("coast-bob-after-"+i);
        }
        if(frames == 790) {controlledBobPhase=-1;reflectionQuality(75,0);}
        if(frames == 850) distanceCapture("coast-air-control");
        if(frames == 860) {controlledBobPhase=.25f;mc.options.improvedTransparency().set(true);}
        if(frames == 930) distanceCapture("coast-bob-oit");
        if(frames == 1000) {controlledBobPhase=-1;FrameProfiler.save();finish();}
    }

    private static void waterLightingFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);
            renderer.setVerificationWaterTime(12f);renderer.setVerificationWindTime(12f);renderer.setVerificationCloudAngle(.02f);
            command("time set 6000");command("fill 10 59 -4 24 64 16 minecraft:sandstone");
            command("fill 11 60 -3 23 60 15 minecraft:sand");
            command("fill 11 61 -3 23 64 15 minecraft:water");
            command("fill 11 61 -3 14 62 15 minecraft:sandstone");
            command("fill 11 63 -3 14 63 15 minecraft:sand");
            command("fill 15 61 -3 17 61 15 minecraft:sandstone");
            command("fill 15 62 -3 17 62 15 minecraft:sand");
            command("fill 14 69 1 17 69 5 minecraft:stone_bricks");
            command("fill 51 62 39 69 62 57 minecraft:sandstone");
            command("fill 51 63 39 69 63 57 minecraft:sand");
            command("fill 52 64 40 68 64 56 minecraft:water");
            command("tp AsterCheck 19.5 65 14.5 180 42");renderer.setVerificationWaterLightingDisabled(true);
        }
        if(frames == 400) {
            waterFloorCheck=new CompletableFuture<>();
            var server=mc.getSingleplayerServer();
            server.execute(() -> waterFloorCheck.complete(server.overworld().getBlockState(new BlockPos(20,60,5)).is(Blocks.SAND)
                && server.overworld().getBlockState(new BlockPos(20,59,5)).is(Blocks.SANDSTONE)
                && server.overworld().getFluidState(new BlockPos(20,61,5)).is(net.minecraft.tags.FluidTags.WATER)
                && server.overworld().getBlockState(new BlockPos(56,62,48)).is(Blocks.SANDSTONE)
                && server.overworld().getBlockState(new BlockPos(56,63,48)).is(Blocks.SAND)
                && server.overworld().getFluidState(new BlockPos(56,64,48)).is(net.minecraft.tags.FluidTags.WATER)));
        }
        if(frames == 440) capture("lighting-above-before");
        if(frames == 450) renderer.setVerificationWaterLightingDisabled(false);
        if(frames == 530) {capture("lighting-above-after");renderer.captureWaterLightingProbe();renderer.captureWaterWaveProbe();renderer.captureWaterOpticsProbe();}
        if(frames == 540) {command("tp AsterCheck 20.5 62 13.5 180 25");renderer.setVerificationWaterLightingDisabled(true);}
        if(frames == 620) capture("lighting-underwater-before");
        if(frames == 630) renderer.setVerificationWaterLightingDisabled(false);
        if(frames == 710) capture("lighting-underwater-after");
        if(frames >= 720 && frames <= 800) renderer.setVerificationWaterTime(12f+(frames-720)/30f);
        if(frames==710 || frames==750 || frames==780 || frames==800)
            renderer.captureUnderwaterReceiverProbe("lighting-wet-"+frames);
        if(frames >= 740 && frames <= 800 && (frames-740)%15 == 0) capture("lighting-motion-"+((frames-740)/15));
        if(frames == 810) {renderer.setVerificationWaterTime(12f);command("time set midnight");}
        if(frames == 890) capture("lighting-underwater-night");
        if(frames == 900) {command("time set 6000");command("weather rain");mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1);}
        if(frames == 990) capture("lighting-underwater-rain");
        if(frames == 1000) {command("weather clear");mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(0));mc.level.setRainLevel(0);command("tp AsterCheck 19.5 65 14.5 180 42");}
        if(frames == 1090) capture("lighting-classic");
        if(frames == 1100) mc.options.improvedTransparency().set(true);
        if(frames == 1180) capture("lighting-oit");
        if(frames == 1190) {mc.options.improvedTransparency().set(false);resourceReload=mc.reloadResourcePacks();}
        if(frames == 1290) capture("lighting-reloaded");
        if(frames == 1300) renderer.setVerificationWaterLightingDisabled(true);
        if(frames == 1360) FrameProfiler.setTag("lighting-before");
        if(frames == 1560) {FrameProfiler.setTag(null);renderer.setVerificationWaterLightingDisabled(false);}
        if(frames == 1620) FrameProfiler.setTag("lighting-after");
        if(frames == 1820) {FrameProfiler.setTag(null);renderer.setVerificationWaterLightingDisabled(true);}
        if(frames == 1880) FrameProfiler.setTag("lighting-before-repeat");
        if(frames == 2080) {FrameProfiler.setTag(null);renderer.setVerificationWaterLightingDisabled(false);}
        // Extra private scope diagnostic after the original timed passes.
        // Preserve the original 19 capture contract and all earlier poses.
        if(frames == 2090) command("tp AsterCheck 20.5 62 13.5 180 -30");
        if(frames == 2120) renderer.captureUnderwaterReceiverProbe("lighting-wet-look-up");
        if(frames == 2150) {FrameProfiler.save();finish();}
    }

    private static void waterGroupFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);mc.options.fov().set(70);
            renderer.setVerificationWaterTime(12f);renderer.setVerificationWindTime(12f);renderer.setVerificationCloudAngle(.02f);
            command("time set 6000");
            for(int y=60;y<=64;y++) command("fill 40 "+y+" -80 191 "+y+" 71 "+(y==60 ? "minecraft:sandstone" : y==61 ? "minecraft:sand" : "minecraft:water"));
            command("fill 39 61 -81 192 65 -81 minecraft:stone_bricks");
            command("fill 39 61 72 192 65 72 minecraft:stone_bricks");
            command("fill 39 61 -80 39 65 71 minecraft:stone_bricks");
            command("fill 192 61 -80 192 65 71 minecraft:stone_bricks");
            command("fill 65 65 -42 69 84 -38 minecraft:stone_bricks");
            command("fill 114 65 -55 118 88 -51 minecraft:oak_leaves[persistent=true]");
            command("fill 163 65 -42 167 84 -38 minecraft:iron_block");
            command("tp AsterCheck 117.5 65.8 65.5 180 8");reflectionQuality(75,67108864);
        }
        if(frames == 450) captureWaterLook("groups-close-before");
        if(frames == 460) reflectionQuality(75,0);
        if(frames == 530) {captureWaterLook("groups-close-after");renderer.captureWaterWaveProbe();renderer.captureWaterOpticsProbe();}
        if(frames == 540) {command("tp AsterCheck 117.5 72 92.5 180 12");reflectionQuality(75,67108864);}
        if(frames == 620) captureWaterLook("groups-mid-before");
        if(frames == 630) reflectionQuality(75,0);
        if(frames == 700) captureWaterLook("groups-mid-after");
        if(frames == 710) {command("time set 12000");command("tp AsterCheck 117.5 85 125.5 180 10");reflectionQuality(75,67108864);}
        if(frames == 800) captureWaterLook("groups-far-before");
        if(frames == 810) reflectionQuality(75,0);
        if(frames == 880) captureWaterLook("groups-far-after");
        if(frames == 890) {command("tp AsterCheck 147.5 85 125.5 170 10");reflectionQuality(75,67108864);}
        if(frames == 970) captureWaterLook("groups-oblique-before");
        if(frames == 980) reflectionQuality(75,0);
        if(frames == 1050) captureWaterLook("groups-oblique-after");
        if(frames == 1060) {command("tp AsterCheck 117.5 65.8 65.5 180 8");command("time set 12000");reflectionQuality(75,67108864);}
        if(frames == 1140) captureWaterLook("groups-golden-before");
        if(frames == 1150) reflectionQuality(75,0);
        if(frames == 1220) captureWaterLook("groups-golden-after");
        if(frames == 1230) {command("time set 6000");command("weather rain");reflectionQuality(75,67108864);mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1);}
        if(frames == 1330) captureWaterLook("groups-rain-before");
        if(frames == 1340) reflectionQuality(75,0);
        if(frames == 1410) captureWaterLook("groups-rain-after");
        if(frames == 1420) {command("weather clear");mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(0));mc.level.setRainLevel(0);}
        if(frames>=1460 && frames<=1565) renderer.setVerificationWaterTime(12f+(frames-1460)/30f);
        if(frames>=1490 && frames<=1565 && (frames-1490)%15==0) captureWaterLook("groups-motion-"+((frames-1490)/15));
        if(frames == 1580) renderer.setVerificationWaterTime(12f);
        if(frames == 1600) captureWaterLook("groups-classic");
        if(frames == 1610) mc.options.improvedTransparency().set(true);
        if(frames == 1680) captureWaterLook("groups-oit");
        if(frames == 1690) {mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577);}
        if(frames == 1760) {captureWaterLook("groups-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames == 1840) captureWaterLook("groups-reloaded");
        if(frames == 1850) {mc.getWindow().setWindowed(1280,720);command("tp AsterCheck 117.5 61.5 65.5 180 -20");}
        if(frames == 1930) captureWaterLook("groups-underwater");
        if(frames == 1940) command("tp AsterCheck 117.5 65.8 65.5 180 8");
        if(frames == 2010) {captureWaterLook("groups-return");reflectionQuality(75,67108864);}
        if(frames == 2130) FrameProfiler.setTag("groups-before");
        if(frames == 2430) {FrameProfiler.setTag(null);reflectionQuality(75,0);}
        if(frames == 2490) FrameProfiler.setTag("groups-after");
        if(frames == 2790) {FrameProfiler.setTag(null);reflectionQuality(75,67108864);}
        if(frames == 2850) FrameProfiler.setTag("groups-before-repeat");
        if(frames == 3150) {FrameProfiler.setTag(null);reflectionQuality(75,0);}
        if(frames == 3250) {FrameProfiler.save();finish();}
    }
    private static void waterEdgeFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);mc.options.fov().set(70);
            renderer.setVerificationWaterTime(12f);renderer.setVerificationWindTime(12f);renderer.setVerificationCloudAngle(.02f);
            command("fill 38 63 -42 102 64 22 minecraft:sandstone");
            command("fill 41 64 -39 99 65 11 minecraft:air");
            command("fill 41 64 -39 99 64 11 minecraft:water");
            // Opposing concave banks expose fully pinned native triangles
            // throughout the lateral walk. A straight raised bank hides its
            // narrow pinned strip behind the opaque lip at this camera height.
            command("fill 64 64 6 66 64 6 minecraft:sandstone");
            command("fill 64 64 6 64 64 8 minecraft:sandstone");
            command("fill 72 64 6 74 64 6 minecraft:sandstone");
            command("fill 74 64 6 74 64 8 minecraft:sandstone");
            for(int z=-36;z<=10;z+=4) command("fill 41 63 "+z+" 99 63 "+(z+1)+" minecraft:polished_andesite");
            for(int x=44;x<=98;x+=6) command("fill "+x+" 63 -39 "+(x+1)+" 63 11 minecraft:quartz_block");
            command("fill 52 65 -24 55 78 -21 minecraft:stone_bricks");
            command("fill 82 65 -24 85 78 -21 minecraft:oak_leaves[persistent=true]");
            command("tp AsterCheck 70.5 65.8 8.5 180 16");reflectionQuality(75,33554432);
        }
        if(frames == 440) {captureWaterLook("edge-before");renderer.captureWaterEdgeProbe("edge-before");}
        if(frames == 450) reflectionQuality(75,0);
        if(frames == 520) {captureWaterLook("edge-after");renderer.captureWaterEdgeProbe("edge-after");}
        if(frames == 530) reflectionQuality(75,268435456);
        if(frames == 600) captureWaterLook("edge-refraction-off");
        if(frames == 610) reflectionQuality(75,536870912);
        if(frames == 680) captureWaterLook("edge-normal-flat");
        if(frames == 690) reflectionQuality(75,1073741824);
        if(frames == 760) captureWaterLook("edge-geometry-flat");
        if(frames == 770) reflectionQuality(75,1644167168);
        if(frames == 840) captureWaterLook("edge-flat-before");
        if(frames == 850) reflectionQuality(75,1610612736);
        if(frames == 920) captureWaterLook("edge-flat-after");
        if(frames == 930) {reflectionQuality(75,33554432);mc.options.fov().set(110);}
        if(frames == 1000) captureWaterLook("edge-wide-before");
        if(frames == 1010) reflectionQuality(75,0);
        if(frames == 1080) {captureWaterLook("edge-wide-after");renderer.captureWaterEdgeProbe("edge-wide-after");}
        if(frames == 1090) {mc.options.fov().set(70);command("gamemode adventure AsterCheck");command("tp AsterCheck 70.5 65 14.5 180 16");}
        if(frames == 1140) strafe(true,false);
        if(frames == 1200) strafe(false,true);
        if(frames >= 1169 && frames <= 1259 && (frames-1169)%15==0) {captureWaterLook("edge-walk-"+((frames-1169)/15));renderer.captureWaterContactProbe("edge-walk-"+((frames-1169)/15));}
        if(frames == 1260) {strafe(false,false);mc.options.improvedTransparency().set(true);}
        if(frames == 1330) captureWaterLook("edge-oit");
        if(frames == 1340) {mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577);}
        if(frames == 1410) {captureWaterLook("edge-resized");renderer.captureWaterEdgeProbe("edge-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames == 1490) captureWaterLook("edge-reloaded");
        if(frames == 1500) {command("gamemode spectator AsterCheck");command("tp AsterCheck 70.5 63 8.5 180 -30");}
        if(frames == 1570) captureWaterLook("edge-underwater");
        if(frames == 1580) command("tp AsterCheck 70.5 65.8 8.5 180 16");
        if(frames == 1650) captureWaterLook("edge-return");
        if(frames == 1700) {reflectionQuality(75,262144);command("tp AsterCheck 70.5 65.8 8.5 180 16");}
        if(frames == 1770) captureWaterLook("edge-direct");
        if(frames == 1800) {reflectionQuality(75,0);FrameProfiler.save();finish();}
    }
    private static void waterEdgeCoastFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 1) {renderer.setVerificationWaterTime(12f);renderer.setVerificationWindTime(12f);renderer.setVerificationCloudAngle(.02f);command("time set 6253");reflectionQuality(75,33554432);}
        if(frames == 40) {toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames == 45) {toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        if(frames == 180) distanceCapture("coast-edge-before");
        if(frames == 190) reflectionQuality(75,0);
        if(frames == 280) distanceCapture("coast-edge-after");
        if(frames == 290) reflectionQuality(75,268435456);
        if(frames == 380) distanceCapture("coast-edge-refraction-off");
        if(frames == 390) reflectionQuality(75,1644167168);
        if(frames == 480) distanceCapture("coast-edge-flat-before");
        if(frames == 490) reflectionQuality(75,1610612736);
        if(frames == 580) distanceCapture("coast-edge-flat-after");
        if(frames == 590) {reflectionQuality(75,33554432);mc.options.fov().set(110);}
        if(frames == 670) distanceCapture("coast-edge-wide-before");
        if(frames == 680) reflectionQuality(75,0);
        if(frames == 760) distanceCapture("coast-edge-wide-after");
        if(frames == 770) {mc.options.fov().set(70);command("time set 22253");}
        if(frames == 850) distanceCapture("coast-edge-twilight");
        if(frames == 860) mc.options.improvedTransparency().set(true);
        if(frames == 940) distanceCapture("coast-edge-oit");
        if(frames == 1000) {FrameProfiler.save();finish();}
    }

    private static void waterCoastFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 1) {reflectionQuality(75,8388608);renderer.setVerificationWaterTime(12f);}
        if(frames == 40) {toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames == 45) {toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        if(frames == 60) FrameProfiler.setTag("coast-before");
        if(frames == 180) {FrameProfiler.setTag(null);distanceCapture("coast-day-before");}
        if(frames == 190) reflectionQuality(75,0);
        if(frames == 220) FrameProfiler.setTag("coast-after");
        if(frames == 340) {FrameProfiler.setTag(null);distanceCapture("coast-day-after");}
        if(frames == 350) {command("time set 14000");reflectionQuality(75,8388608);}
        if(frames == 430) distanceCapture("coast-twilight-before");
        if(frames == 440) reflectionQuality(75,0);
        if(frames == 520) distanceCapture("coast-twilight-after");
        if(frames == 530) command("tp AsterCheck -230 85 221 -50 18");
        if(frames == 640) distanceCapture("coast-second-view");
        if(frames == 650) mc.options.improvedTransparency().set(true);
        if(frames == 730) distanceCapture("coast-oit");
        if(frames == 740) {mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577);}
        if(frames == 820) distanceCapture("coast-resized");
        for(int i=0;i<4;i++) {
            if(frames == 840+i*36) command("tp AsterCheck "+(-300+(i-1.5)*6)+" 85 440 -145 18");
            if(frames == 868+i*36) distanceCapture("coast-motion-"+i);
        }
        if(frames == 1000) {FrameProfiler.save();finish();}
    }
    private static void distanceCapture(String name) {
        captureWaterLook(name);AsterRenderer.INSTANCE.captureWaterSourceProbe(name);
    }
    private static boolean distanceFixtureReady() {
        var area=Minecraft.getInstance().levelRenderer.viewArea();
        if(area == null) return false;
        for(int[] pillar:new int[][]{{48,0,112},{144,-20,104},{240,20,96}})
            for(int y=64;y<=pillar[2];y+=16) {
                var section=area.getRenderSectionAt(new BlockPos(pillar[0],y,pillar[1]));
                if(section == null || section.getSectionMesh() == null || !section.getSectionMesh().hasRenderableLayers()) return false;
            }
        for(int x:new int[]{304,320,336,344}) {
            var section=area.getRenderSectionAt(new BlockPos(x,64,0));
            if(section == null || section.getSectionMesh() == null || !section.getSectionMesh().hasRenderableLayers()) return false;
        }
        return true;
    }
    private static void waterDistanceFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            for(int x=40;x<360;x+=32) {
                command("fill "+x+" 63 -48 "+(x+31)+" 63 48 minecraft:sand");
                command("fill "+x+" 64 -48 "+(x+31)+" 64 48 minecraft:water");
            }
            command("fill 48 65 -12 52 112 12 minecraft:gold_block");
            command("fill 144 65 -26 148 104 -14 minecraft:red_concrete");
            command("fill 240 65 14 244 96 26 minecraft:quartz_block");
            command("time set 4000");renderer.setVerificationWaterTime(12f);
            command("tp AsterCheck 344.5 68.1 .5 90 14");
            reflectionQuality(75,8388608);
        }
        if(frames == 600) distanceCapture("distance-far-before");
        if(frames == 610) reflectionQuality(75,0);
        if(frames == 690) distanceCapture("distance-far-after");
        // The same landmark crosses the old five-chunk boundary in both
        // directions. Fixed wave time separates range changes from animation.
        for(int version=0;version<2;version++) {
            int start=710+version*400;
            if(frames == start) reflectionQuality(75,version == 0 ? 8388608 : 0);
            for(int i=0;i<12;i++) {
                double x= i<6 ? 152.5-i*8 : 112.5+(i-6)*8;
                if(frames == start+i*32) command("tp AsterCheck "+x+" 68.1 .5 90 24");
                if(frames == start+i*32+26) distanceCapture("distance-motion-"+(version == 0 ? "before" : "after")+"-"+i);
            }
        }
        if(frames == 1510) {command("tp AsterCheck 344.5 98.1 .5 90 24");reflectionQuality(75,8388608);}
        if(frames == 1590) distanceCapture("distance-high-before");
        if(frames == 1600) reflectionQuality(75,0);
        if(frames == 1680) distanceCapture("distance-high-after");
        if(frames == 1690) {command("tp AsterCheck 344.5 68.1 .5 90 14");reflectionQuality(75,0);mc.options.improvedTransparency().set(true);}
        if(frames == 1770) distanceCapture("distance-oit");
        if(frames == 1780) {mc.options.improvedTransparency().set(false);reflectionQuality(75,262144);}
        if(frames == 1860) distanceCapture("distance-direct");
        if(frames == 1870) {reflectionQuality(75,0);mc.options.renderDistance().set(8);}
        if(frames == 1990) distanceCapture("distance-eight-chunks");
        for(int i=0;i<9;i++) {
            if(frames == 2000+i*40) command("tp AsterCheck "+(168.5-i*5)+" 68.1 .5 90 24");
            if(frames == 2000+i*40+32) distanceCapture("distance-fade-"+i);
        }
        if(frames == 2360) {mc.options.renderDistance().set(32);command("tp AsterCheck 344.5 68.1 .5 90 14");}
        if(frames == 2510) distanceCapture("distance-restored");
        if(frames == 2520) {reflectionQuality(75,8388608);FrameProfiler.setTag("distance-before");}
        if(frames == 2760) {FrameProfiler.setTag(null);reflectionQuality(75,0);}
        if(frames == 2800) FrameProfiler.setTag("distance-after");
        if(frames == 3040) FrameProfiler.setTag(null);
        if(frames == 3050) resourceReload=mc.reloadResourcePacks();
        if(frames == 3160) distanceCapture("distance-reloaded");
        if(frames == 3180) {FrameProfiler.save();finish();}
    }
    private static void reflectionFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);
            command("fill 40 58 -16 68 64 18 minecraft:sandstone");
            command("fill 41 60 -15 67 64 17 minecraft:air");
            command("fill 41 63 -15 53 63 17 minecraft:sand");
            command("fill 41 64 -15 53 64 17 minecraft:water");
            command("fill 54 59 -15 67 59 17 minecraft:sand");
            command("fill 54 60 -15 67 64 17 minecraft:water");
            command("fill 42 65 -9 46 76 -7 minecraft:gold_block");
            command("fill 51 65 -9 55 73 -7 minecraft:stone_bricks");
            command("fill 62 65 -9 66 79 -7 minecraft:iron_block");
            // Fine reflected silhouettes and leafy texture detail alongside
            // the broad pillars; use native prepared meshes throughout.
            command("fill 48 65 -10 48 70 -10 minecraft:oak_log");
            command("fill 47 70 -11 50 72 -9 minecraft:oak_leaves[persistent=true]");
            command("fill 58 65 -8 58 73 -8 minecraft:iron_bars");
            command("tp AsterCheck 54.5 65.1 15.5 180 8");
            renderer.setVerificationWaterTime(12f);reflectionQuality(50,4096);
        }
        if(frames == 420) captureWaterLook("reflection-day-before");
        if(frames == 430) reflectionQuality(50,0);
        if(frames == 500) captureWaterLook("reflection-day-half");
        if(frames == 510) reflectionQuality(75,0);
        if(frames == 580) { captureWaterLook("reflection-day-balanced");renderer.captureWaterWaveProbe();renderer.captureWaterOpticsProbe(); }
        if(frames == 590) reflectionQuality(100,0);
        if(frames == 660) captureWaterLook("reflection-day-full");
        if(frames == 670) { reflectionQuality(50,4096);command("time set 12000");command("tp AsterCheck 65.5 65.2 5.5 90 6"); }
        if(frames == 740) captureWaterLook("reflection-golden-before");
        if(frames == 750) reflectionQuality(50,0);
        if(frames == 820) captureWaterLook("reflection-golden-half");
        if(frames == 830) reflectionQuality(75,0);
        if(frames == 900) captureWaterLook("reflection-golden-balanced");
        if(frames == 910) reflectionQuality(100,0);
        if(frames == 980) captureWaterLook("reflection-golden-full");
        if(frames == 990) { reflectionQuality(75,0);command("time set 4000");command("tp AsterCheck 67.5 65.1 13.5 90 30"); }
        if(frames == 1060) captureWaterLook("reflection-shore-balanced");
        if(frames == 1070) reflectionQuality(100,0);
        if(frames == 1140) captureWaterLook("reflection-shore-full");
        if(frames == 1150) { reflectionQuality(75,0);command("tp AsterCheck 58.5 61.5 8.5 180 5"); }
        if(frames == 1220) captureWaterLook("reflection-underwater");
        if(frames == 1230) {
            command("tp AsterCheck 54.5 65.1 15.5 180 8");command("weather rain");
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1);
        }
        if(frames == 1300) captureWaterLook("reflection-rain-balanced");
        if(frames == 1310) reflectionQuality(100,0);
        if(frames == 1380) captureWaterLook("reflection-rain-full");
        if(frames == 1390) {
            reflectionQuality(50,4096);command("weather clear");
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(0));mc.level.setRainLevel(0);
        }
        if(frames >= 1500 && frames < 1516) renderer.captureWater("reflection-stable-before-"+(frames-1500),true);
        if(frames == 1516) captureWaterLook("reflection-stationary-before");
        if(frames == 1520) reflectionQuality(50,0);
        if(frames >= 1580 && frames < 1596) renderer.captureWater("reflection-stable-after-"+(frames-1580),true);
        if(frames == 1596) captureWaterLook("reflection-stationary-after");
        if(frames == 1600) reflectionQuality(50,4096);
        if(frames >= 1600 && frames <= 1684) {
            renderer.setVerificationWaterTime(12f+(frames-1600)/30f);
            if(frames%6 == 4) command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 65.1 15.5 %.4f 8",54.5+(frames-1600)*.012,180+(frames-1600)*.07));
        }
        if(frames >= 1640 && frames <= 1682 && (frames-1640)%6 == 0) captureWaterLook("reflection-motion-before-"+((frames-1640)/6));
        if(frames == 1690) { reflectionQuality(50,0);command("tp AsterCheck 54.5 65.1 15.5 180 8");renderer.setVerificationWaterTime(12f); }
        if(frames >= 1720 && frames <= 1804) {
            renderer.setVerificationWaterTime(12f+(frames-1720)/30f);
            if(frames%6 == 4) command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 65.1 15.5 %.4f 8",54.5+(frames-1720)*.012,180+(frames-1720)*.07));
        }
        if(frames >= 1760 && frames <= 1802 && (frames-1760)%6 == 0) captureWaterLook("reflection-motion-after-"+((frames-1760)/6));
        if(frames == 1810) { reflectionQuality(75,0);command("tp AsterCheck 54.5 65.1 15.5 180 8");renderer.setVerificationWaterTime(12f); }
        if(frames == 1880) captureWaterLook("reflection-classic");
        if(frames == 1890) mc.options.improvedTransparency().set(true);
        if(frames == 1960) captureWaterLook("reflection-oit");
        if(frames == 1970) { mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577); }
        if(frames == 2070) captureWaterLook("reflection-odd-size");
        if(frames == 2100) resourceReload=mc.reloadResourcePacks();
        if(frames == 2170) captureWaterLook("reflection-reloaded");
        if(frames == 2180) mc.getWindow().setWindowed(1280,720);
        if(frames == 2260) reflectionQuality(50,4096);
        if(frames == 2360) FrameProfiler.setTag("reflection-before-half");
        if(frames == 2660) FrameProfiler.setTag(null);
        if(frames == 2670) reflectionQuality(50,0);
        if(frames == 2770) FrameProfiler.setTag("reflection-after-half");
        if(frames == 3070) FrameProfiler.setTag(null);
        if(frames == 3080) reflectionQuality(75,0);
        if(frames == 3180) FrameProfiler.setTag("reflection-balanced");
        if(frames == 3480) FrameProfiler.setTag(null);
        if(frames == 3490) reflectionQuality(100,0);
        if(frames == 3590) FrameProfiler.setTag("reflection-full");
        if(frames == 3890) FrameProfiler.setTag(null);
        if(frames == 3900) reflectionQuality(50,4096);
        if(frames == 4000) FrameProfiler.setTag("reflection-before-repeat");
        if(frames == 4300) FrameProfiler.setTag(null);
        if(frames == 4310) reflectionQuality(75,0);
        if(frames == 4340) { FrameProfiler.save();finish(); }
    }
    private static void waterLookFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);
            command("fill 40 58 -16 68 64 18 minecraft:sandstone");
            command("fill 41 60 -15 67 64 17 minecraft:air");
            command("fill 41 63 -15 53 63 17 minecraft:sand");
            command("fill 41 64 -15 53 64 17 minecraft:water");
            command("fill 54 59 -15 67 59 17 minecraft:sand");
            command("fill 54 60 -15 67 64 17 minecraft:water");
            command("fill 42 65 -9 46 76 -7 minecraft:gold_block");
            command("fill 51 65 -9 55 73 -7 minecraft:stone_bricks");
            command("fill 62 65 -9 66 79 -7 minecraft:iron_block");
            command("tp AsterCheck 54.5 65.1 15.5 180 8");
            renderer.setVerificationWaterTime(12f);renderer.setVerificationLightingFlags(512);
        }
        if(frames == 420) captureWaterLook("waterlook-shallow-legacy");
        if(frames == 430) renderer.setVerificationLightingFlags(0);
        if(frames == 500) captureWaterLook("waterlook-shallow-refined");
        // Isolate SSR crossing against the same environment fallback. The
        // independent terrain source otherwise masks the old sparse-hit grain.
        if(frames == 510) renderer.setVerificationLightingFlags(1024 | 65536);
        if(frames == 570) captureWaterLook("waterlook-trace-midpoint");
        if(frames == 580) renderer.setVerificationLightingFlags(65536);
        if(frames == 640) { captureWaterLook("waterlook-trace-endpoint");renderer.captureWaterOpticsProbe(); }
        if(frames == 650) { renderer.setVerificationLightingFlags(512);command("tp AsterCheck 54.5 71 15.5 180 30"); }
        if(frames == 720) captureWaterLook("waterlook-depth-legacy");
        if(frames == 730) renderer.setVerificationLightingFlags(0);
        if(frames == 800) captureWaterLook("waterlook-depth-refined");
        if(frames == 810) { renderer.setVerificationLightingFlags(512);command("time set 12000");command("tp AsterCheck 65.5 65.2 5.5 90 6"); }
        if(frames == 880) captureWaterLook("waterlook-golden-legacy");
        if(frames == 890) renderer.setVerificationLightingFlags(0);
        if(frames == 960) captureWaterLook("waterlook-golden-refined");
        if(frames == 970) {
            renderer.setVerificationLightingFlags(512);command("time set 4000");command("weather rain");
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1);
            command("tp AsterCheck 54.5 67 15.5 180 16");
        }
        if(frames == 1060) captureWaterLook("waterlook-rain-legacy");
        if(frames == 1070) renderer.setVerificationLightingFlags(0);
        if(frames == 1150) captureWaterLook("waterlook-rain-refined");
        if(frames == 1160) {
            renderer.setVerificationLightingFlags(512);command("weather clear");
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(0));mc.level.setRainLevel(0);
            command("tp AsterCheck 54.5 63 15.5 180 -8");
        }
        if(frames == 1230) captureWaterLook("waterlook-underwater-legacy");
        if(frames == 1240) renderer.setVerificationLightingFlags(0);
        if(frames == 1310) captureWaterLook("waterlook-underwater-refined");
        if(frames == 1320) command("tp AsterCheck 54.5 63 6.5 90 -48");
        if(frames == 1390) captureWaterLook("waterlook-window");
        if(frames == 1400) command("tp AsterCheck 54.5 63 6.5 90 -7");
        if(frames == 1470) captureWaterLook("waterlook-tir");
        if(frames == 1480) command("tp AsterCheck 54.5 65.1 15.5 180 8");
        if(frames == 1550) captureWaterLook("waterlook-above-return");
        if(frames == 1560) mc.options.improvedTransparency().set(true);
        if(frames == 1630) captureWaterLook("waterlook-oit");
        if(frames == 1640) { mc.options.improvedTransparency().set(false);command("tp AsterCheck 61.5 60 6.5 180 0"); }
        if(frames == 1710) captureWaterLook("waterlook-deep-underwater");
        if(frames == 1720) command("tp AsterCheck 54.5 63.1 6.5 180 1");
        if(frames == 1790) captureWaterLook("waterlook-below-line");
        if(frames == 1800) command("tp AsterCheck 54.5 63.4 6.5 180 1");
        if(frames == 1870) captureWaterLook("waterlook-above-line");
        if(frames == 1880) { command("tp AsterCheck 54.5 65.1 15.5 180 8");renderer.setVerificationLightingFlags(512); }
        if(frames == 1980) FrameProfiler.setTag("water-legacy");
        if(frames == 2280) FrameProfiler.setTag(null);
        if(frames == 2290) renderer.setVerificationLightingFlags(0);
        if(frames == 2390) FrameProfiler.setTag("water-refined");
        if(frames == 2690) FrameProfiler.setTag(null);
        if(frames == 2700) renderer.setVerificationLightingFlags(512);
        if(frames == 2800) FrameProfiler.setTag("water-legacy-repeat");
        if(frames == 3100) FrameProfiler.setTag(null);
        if(frames == 3110) renderer.setVerificationLightingFlags(0);
        if(frames == 3130) { FrameProfiler.save();finish(); }
    }
    private static void waveFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);
            command("fill 40 58 -16 68 64 18 minecraft:sandstone");
            command("fill 41 60 -15 67 64 17 minecraft:air");
            command("fill 41 63 -15 53 63 17 minecraft:sand");
            command("fill 41 64 -15 53 64 17 minecraft:water");
            command("fill 54 59 -15 67 59 17 minecraft:sand");
            command("fill 54 60 -15 67 64 17 minecraft:water");
            command("fill 42 65 -9 46 76 -7 minecraft:gold_block");
            command("fill 51 65 -9 55 73 -7 minecraft:stone_bricks");
            command("fill 62 65 -9 66 79 -7 minecraft:iron_block");
            command("tp AsterCheck 54.5 65.1 15.5 180 8");
            renderer.setVerificationWaterTime(12f);renderer.setVerificationLightingFlags(2048);
        }
        if(frames == 420) captureWaterLook("waves-day-before");
        if(frames == 430) renderer.setVerificationLightingFlags(0);
        if(frames == 500) { captureWaterLook("waves-day-after");renderer.captureWaterWaveProbe();renderer.captureWaterOpticsProbe(); }
        if(frames == 510) { renderer.setVerificationLightingFlags(2048);command("time set 12000");command("tp AsterCheck 65.5 65.2 5.5 90 6"); }
        if(frames == 580) captureWaterLook("waves-golden-before");
        if(frames == 590) renderer.setVerificationLightingFlags(0);
        if(frames == 660) captureWaterLook("waves-golden-after");
        if(frames == 670) { command("time set 4000");command("tp AsterCheck 54.5 65.1 15.5 180 8"); }
        if(frames >= 700 && frames < 890) renderer.setVerificationWaterTime(12f+(frames-700)/30f);
        if(frames >= 740 && frames <= 872 && (frames-740)%12 == 0) captureWaterLook("waves-day-motion-"+((frames-740)/12));
        if(frames == 890) {
            renderer.setVerificationWaterTime(12f);renderer.setVerificationLightingFlags(2048);command("weather rain");
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1);
        }
        if(frames == 1000) captureWaterLook("waves-rain-before");
        if(frames == 1010) renderer.setVerificationLightingFlags(0);
        if(frames == 1080) captureWaterLook("waves-rain-after");
        if(frames >= 1120 && frames < 1310) renderer.setVerificationWaterTime(12f+(frames-1120)/30f);
        if(frames >= 1160 && frames <= 1292 && (frames-1160)%12 == 0) captureWaterLook("waves-rain-motion-"+((frames-1160)/12));
        if(frames == 1310) {
            command("weather clear");command("time set 12000");command("tp AsterCheck 65.5 65.2 5.5 90 6");
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(0));mc.level.setRainLevel(0);
        }
        if(frames >= 1370 && frames < 1560) renderer.setVerificationWaterTime(12f+(frames-1370)/30f);
        if(frames >= 1410 && frames <= 1542 && (frames-1410)%12 == 0) captureWaterLook("waves-golden-motion-"+((frames-1410)/12));
        if(frames == 1560) { renderer.setVerificationWaterTime(12f);command("time set 4000");command("tp AsterCheck 67.5 65.1 13.5 90 30"); }
        if(frames == 1630) captureWaterLook("waves-shore");
        if(frames == 1640) {
            command("fill 72 62 -3 80 62 8 minecraft:stone_bricks");
            command("fill 72 63 -3 80 63 8 minecraft:air");
            command("setblock 74 63 2 minecraft:water");
            command("tp AsterCheck 74.5 64.2 5.5 180 22");
        }
        if(frames == 1700) captureWaterLook("waves-flow");
        if(frames == 1710) command("tp AsterCheck 54.5 65.1 15.5 180 8");
        if(frames == 1780) captureWaterLook("waves-classic");
        if(frames == 1790) mc.options.improvedTransparency().set(true);
        if(frames == 1860) captureWaterLook("waves-oit");
        if(frames == 1870) mc.options.improvedTransparency().set(false);
        if(frames >= 1900 && frames <= 1984) {
            renderer.setVerificationWaterTime(12f+(frames-1900)/30f);
            if(frames%6 == 0) command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 65.1 15.5 %.4f 8",54.5+(frames-1900)*.02,180+(frames-1900)*.1));
        }
        if(frames >= 1940 && frames <= 1982 && (frames-1940)%6 == 0) captureWaterLook("waves-camera-"+((frames-1940)/6));
        if(frames == 1990) { command("tp AsterCheck 54.5 65.1 15.5 180 8");renderer.setVerificationWaterTime(12f);renderer.setVerificationLightingFlags(2048); }
        if(frames == 2090) FrameProfiler.setTag("waves-before");
        if(frames == 2390) FrameProfiler.setTag(null);
        if(frames == 2400) renderer.setVerificationLightingFlags(0);
        if(frames == 2500) FrameProfiler.setTag("waves-after");
        if(frames == 2800) FrameProfiler.setTag(null);
        if(frames == 2810) renderer.setVerificationLightingFlags(2048);
        if(frames == 2910) FrameProfiler.setTag("waves-before-repeat");
        if(frames == 3210) FrameProfiler.setTag(null);
        if(frames == 3220) renderer.setVerificationLightingFlags(0);
        if(frames == 3250) { FrameProfiler.save();finish(); }
    }
    private static void waterFrames() {
        var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);
            command("fill 40 62 -16 68 62 18 minecraft:sandstone");
            command("fill 40 63 -16 68 63 18 minecraft:sand");
            command("fill 41 64 -15 67 64 17 minecraft:water");
            command("fill 42 65 -9 46 76 -7 minecraft:gold_block");
            command("fill 51 65 -9 55 73 -7 minecraft:stone_bricks");
            command("fill 62 65 -9 66 79 -7 minecraft:iron_block");
            command("tp AsterCheck 54.5 65.1 15.5 180 8");
            // Freeze waves only for the isolated stationary stability sequence;
            // ray step phase still changes each rendered frame.
            AsterRenderer.INSTANCE.setVerificationWaterTime(12f);
        }
        if(frames >= 500 && frames < 516) AsterRenderer.INSTANCE.captureWater("stable-"+(frames-500),true);
        if(frames == 520) { capture("water-stationary");AsterRenderer.INSTANCE.captureWater("stationary",true); }
        if(frames == 530) AsterRenderer.INSTANCE.setVerificationWaterTime(null);
        if(frames >= 540 && frames <= 588 && frames%3 == 0) {
            double x=54.5+(frames-540)*.04,yaw=180+(frames-540)*.15;
            command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 65.1 15.5 %.4f 8",x,yaw));
        }
        if(frames == 560 || frames == 575 || frames == 588) AsterRenderer.INSTANCE.captureWater("motion-"+frames,true);
        if(frames == 588) capture("water-moving");
        if(frames == 590) command("fill 49 65 11 59 72 11 minecraft:stone_bricks");
        if(frames == 605) { capture("water-occluded");AsterRenderer.INSTANCE.captureWater("occluded",true); }
        if(frames == 610) command("fill 49 65 11 59 72 11 minecraft:air");
        if(frames >= 611 && frames <= 621) AsterRenderer.INSTANCE.captureWater("reveal-"+frames,false);
        if(frames == 640) { capture("water-revealed");AsterRenderer.INSTANCE.captureWater("revealed",true); }
        if(frames == 650) command("tp AsterCheck 190 85 15 180 8");
        if(frames == 680) capture("water-cut-away");
        if(frames == 690) command("tp AsterCheck 54.5 65.1 15.5 180 8");
        if(frames == 710) { capture("water-cut-return");AsterRenderer.INSTANCE.captureWater("cut-return",true); }
        if(frames == 720) mc.getWindow().setWindowed(1024,576);
        if(frames == 760) { capture("water-resized");AsterRenderer.INSTANCE.captureWater("resized",true); }
        if(frames == 770) resourceReload=mc.reloadResourcePacks();
        if(frames == 830) { capture("water-reloaded");AsterRenderer.INSTANCE.captureWater("reloaded",true); }
        if(frames == 840) mc.options.improvedTransparency().set(true);
        if(frames == 910) { capture("water-oit");AsterRenderer.INSTANCE.captureWater("oit",true); }
        if(frames == 940) finish();
    }

    private static void fogFrames() {
        var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);
            command("fill 40 63 -12 130 64 12 minecraft:stone_bricks");
            command("fill 40 65 -12 130 76 -12 minecraft:stone_bricks");
            command("fill 40 65 12 130 76 12 minecraft:stone_bricks");
            for(int x=48;x<=120;x+=8) {
                command("fill "+x+" 75 -12 "+(x+2)+" 75 12 minecraft:stone_bricks");
                command("fill "+x+" 65 -10 "+(x+1)+" 74 -9 minecraft:stone_bricks");
            }
            command("fill 129 65 -12 130 76 12 minecraft:stone_bricks");
            command("time set 1000");command("weather clear");
            command("tp AsterCheck 43.5 67 0.5 -90 0");
        }
        if(frames >= 500 && frames < 516) AsterRenderer.INSTANCE.captureFog("stable-"+(frames-500),true);
        if(frames == 520) { capture("fog-stationary");AsterRenderer.INSTANCE.captureFog("stationary",true);AsterRenderer.INSTANCE.captureFogPhysicsProbe("stationary");AsterRenderer.INSTANCE.captureFogShadowProbe(); }
        if(frames >= 540 && frames <= 588 && frames%3 == 0) {
            double x=43.5+(frames-540)*.025,yaw=-90+(frames-540)*.06;
            command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 67 0.5 %.4f 0",x,yaw));
        }
        if(frames == 560 || frames == 575 || frames == 588) AsterRenderer.INSTANCE.captureFog("motion-"+frames,true);
        if(frames == 588) capture("fog-moving");
        if(frames == 590) command("fill 51 65 -8 51 73 8 minecraft:stone_bricks");
        if(frames == 610) { capture("fog-occluded");AsterRenderer.INSTANCE.captureFog("occluded",true); }
        if(frames == 615) command("fill 51 65 -8 51 73 8 minecraft:air");
        if(frames >= 616 && frames <= 627) AsterRenderer.INSTANCE.captureFog("reveal-"+frames,false);
        if(frames == 640) { capture("fog-revealed");AsterRenderer.INSTANCE.captureFog("revealed",true); }
        if(frames == 645) { command("weather rain");mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1); }
        if(frames == 685) { capture("fog-rain");AsterRenderer.INSTANCE.captureFog("rain",true); }
        if(frames == 690) { command("weather clear");mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(0));mc.level.setRainLevel(0);command("time set midnight"); }
        if(frames == 735) { capture("fog-night");AsterRenderer.INSTANCE.captureFog("night",true); }
        if(frames == 740) { command("time set 1000");command("tp AsterCheck 220 90 0 -90 0"); }
        if(frames == 770) capture("fog-cut-away");
        if(frames == 775) command("tp AsterCheck 43.5 67 0.5 -90 0");
        if(frames == 815) { capture("fog-cut-return");AsterRenderer.INSTANCE.captureFog("cut-return",true); }
        if(frames == 820) mc.getWindow().setWindowed(1025,577);
        if(frames == 860) { capture("fog-resized");AsterRenderer.INSTANCE.captureFog("resized",true); }
        if(frames == 865) resourceReload=mc.reloadResourcePacks();
        if(frames == 925) { capture("fog-reloaded");AsterRenderer.INSTANCE.captureFog("reloaded",true); }
        if(frames == 930) mc.options.improvedTransparency().set(true);
        if(frames == 995) { capture("fog-oit");AsterRenderer.INSTANCE.captureFog("oit",true); }
        if(frames == 1020) finish();
    }

    private static void localLightFrames() {
        var mc=Minecraft.getInstance();var renderer=AsterRenderer.INSTANCE;
        if(frames==1) {
            profileFixture=new CompletableFuture<>();var setup=profileFixture;var server=mc.getSingleplayerServer();
            server.execute(()->{
                try {
                    var level=server.overworld();
                    for(int x=2;x<=3;x++)for(int z=-1;z<=0;z++)level.getChunk(x,z);
                    for(String c:List.of("fill 40 64 -8 56 72 8 minecraft:stone_bricks","fill 41 65 -7 55 71 7 minecraft:air",
                        "fill 49 65 -7 49 71 7 minecraft:stone_bricks","setblock 44 66 -5 minecraft:torch","setblock 53 64 -5 minecraft:sea_lantern",
                        "tp AsterCheck 46 66 5 180 8","item replace entity AsterCheck weapon.mainhand with minecraft:air"))
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),c);
                    for(int y=65;y<=71;y++)for(int z=-7;z<=7;z++)
                        if(!level.getBlockState(new BlockPos(49,y,z)).is(Blocks.STONE_BRICKS))throw new IllegalStateException("Local light partition not sealed");
                    setup.complete(null);
                }catch(Throwable ex){setup.completeExceptionally(ex);}
            });
        }
        if(frames==40){toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames==45){toggleByKey();keyboardToggleVerified&=renderer.effectsEnabled();}
        if(frames==150)localLightCapture("local-mixed");
        if(frames==180)command("setblock 44 66 -5 minecraft:air");
        if(frames==240)localLightCapture("local-removed");
        if(frames==270)command("item replace entity AsterCheck weapon.mainhand with minecraft:torch");
        if(frames==330)localLightCapture("local-held");
        if(frames==350)command("tp AsterCheck 48.2 66 5 180 8");
        if(frames==400)localLightCapture("local-cache-right");
        if(frames==410)command("tp AsterCheck 44.5 66 5 180 8");
        if(frames==460)localLightCapture("local-cache-left");
        if(frames==480){command("item replace entity AsterCheck weapon.mainhand with minecraft:air");command("setblock 44 66 -5 minecraft:torch");}
        if(frames==540)localLightCapture("local-return");
        if(frames==550)mc.options.improvedTransparency().set(true);
        if(frames==610)localLightCapture("local-oit");
        if(frames==650)resourceReload=mc.reloadResourcePacks();
        if(frames==720)localLightCapture("local-reloaded");
        if(frames==730)toggleByKey();
        if(frames==770)localLightCapture("local-disabled");
        if(frames==800)toggleByKey();
        if(frames==830)localLightCapture("local-enabled");
        if(frames==850)finish();
    }
    private static void localLightCapture(String label){capture(label);LocalLightProbe.capture(label);}
    private static void lightingFrames() {
        if (frames == 350) {
            command("fill 40 49 -8 56 60 8 minecraft:stone_bricks");
            command("fill 41 50 -7 55 59 7 minecraft:air");
            command("fill 44 52 -7 47 55 -7 minecraft:glowstone");
            command("fill 50 52 -7 53 55 -7 minecraft:sea_lantern");
            command("tp AsterCheck 48 53 5 180 0");
        }
        if (frames == 470) { capture("cave-emissive-enabled"); AsterRenderer.INSTANCE.captureHdrProbe(); }
        if (frames == 480) toggleByKey();
        if (frames == 540) { capture("cave-emissive-disabled"); toggleByKey(); }
        if (frames == 545) command("time set midnight");
        if (frames == 620) capture("cave-emissive-night");
        if (frames == 630) { command("time set 4000"); command("tp AsterCheck 24 73 28 140 18"); }
        if (frames == 710) capture("lighting-outdoor-return");
        if (frames == 740) finish();
    }

    private static void materialFrames() {
        var mc=Minecraft.getInstance();
        if(frames == 350) setupMaterialPlatform();
        if(frames == 490) { capture("materials-default"); AsterRenderer.INSTANCE.captureMaterialProbe("default-gold"); }
        if(frames == 500) mc.options.improvedTransparency().set(true);
        if(frames == 620) capture("materials-oit");
        if(frames == 630) {
            var packs=mc.getResourcePackRepository(); packs.reload();
            if(!packs.addPack("file/aster-material-override")) throw new IllegalStateException("Material test resource pack not found");
            resourceReload=mc.reloadResourcePacks();
        }
        if(frames == 790) { capture("materials-override"); AsterRenderer.INSTANCE.captureMaterialProbe("override-gold"); }
        if(frames == 830) finish();
    }
    private static void setupMaterialPlatform() {
        setupMaterialPlatform(false);
    }
    private static void setupMaterialPlatform(boolean oblique) {
            var mc=Minecraft.getInstance();
            mc.options.improvedTransparency().set(false);
            command("fill 40 63 -12 64 63 12 minecraft:stone_bricks");
            command("fill 40 64 -10 45 64 10 minecraft:gold_block");
            command("fill 46 64 -10 51 64 10 minecraft:iron_block");
            command("fill 52 64 -10 57 64 10 minecraft:copper_block");
            command("fill 58 64 -10 63 64 10 minecraft:stone_bricks");
            // View the gold panel along the sun's mirror direction so changing
            // roughness produces a visible highlight, not just different data.
            var sun=AsterRenderer.INSTANCE.lightingDirection();
            double vx=-sun.x*30,vy=sun.y*30,vz=-sun.z*30+(oblique ? 8 : 0);
            double px=43.5+vx,py=65+vy-mc.player.getEyeHeight(),pz=.5+vz;
            double yaw=Math.toDegrees(Math.atan2(vx,-vz));
            double pitch=Math.toDegrees(Math.atan2(vy,Math.sqrt(vx*vx+vz*vz)));
            command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.5f %.5f %.5f %.5f %.5f",px,py,pz,yaw,pitch));
    }

    private static void selectMapPack(String name) {
        var mc=Minecraft.getInstance(); var packs=mc.getResourcePackRepository(); packs.reload();
        for(var variant:new String[]{"flat","normal","inverted"}) packs.removePack("file/aster-maps-"+variant);
        if(name != null && !packs.addPack("file/aster-maps-"+name)) throw new IllegalStateException("Map test pack not found: "+name);
        resourceReload=mc.reloadResourcePacks();
    }
    private static void mapProbe(String label,String block,float u,float v,int mip,boolean normal) {
        AsterRenderer.INSTANCE.captureMapProbe(label,"minecraft:block/"+block,u,v,mip,normal);
    }
    private static void mapFrames() {
        var mc=Minecraft.getInstance();
        if(frames == 350) setupMaterialPlatform(true);
        if(frames == 490) capture("maps-baseline");
        if(frames == 500) selectMapPack("flat");
        if(frames == 600) {
            capture("maps-data-classic");
            AsterRenderer.INSTANCE.captureHdrProbe();
            AsterRenderer.INSTANCE.captureBrdfProbe();
            mapProbe("gold-low","gold_block",.15625f,.15625f,0,false);
            mapProbe("gold-high","gold_block",.65625f,.15625f,0,false);
            mapProbe("gold-mip","gold_block",.5f,.5f,4,false);
            mapProbe("copper-low","copper_block",.15625f,.15625f,0,false);
            mapProbe("copper-high","copper_block",.65625f,.15625f,0,false);
            mapProbe("stone-emission","stone_bricks",.15625f,.15625f,0,false);
            mapProbe("stone-dark","stone_bricks",.65625f,.15625f,0,false);
            mapProbe("normal-flat","gold_block",.15625f,.15625f,0,true);
        }
        if(frames == 610) mc.options.improvedTransparency().set(true);
        if(frames == 700) capture("maps-data-oit");
        if(frames == 710) selectMapPack("normal");
        if(frames == 820) {
            capture("maps-normal-oit");
            mapProbe("normal-enabled","gold_block",.15625f,.15625f,0,true);
            mapProbe("normal-mip","gold_block",.5f,.5f,4,true);
        }
        if(frames == 830) mc.options.improvedTransparency().set(false);
        if(frames == 920) capture("maps-normal-classic");
        if(frames == 930) selectMapPack("inverted");
        if(frames == 1040) {
            capture("maps-normal-inverted");
            mapProbe("normal-inverted","gold_block",.15625f,.15625f,0,true);
        }
        if(frames == 1050) selectMapPack(null);
        if(frames == 1140) {
            capture("maps-unloaded");
            mapProbe("gold-restored","gold_block",.15625f,.15625f,0,false);
            mapProbe("normal-restored","gold_block",.15625f,.15625f,0,true);
        }
        if(frames == 1200) finish();
    }

    private static void foliageFrames() {
        var renderer=AsterRenderer.INSTANCE;
        if(frames == 120) { toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled(); }
        if(frames == 130) { toggleByKey();keyboardToggleVerified&=renderer.effectsEnabled(); }
        if(frames == 200) capture("foliage-day-full");
        if(frames == 210) renderer.setVerificationLightingFlags(1);
        if(frames == 300) capture("foliage-day-no-fog");
        if(frames == 310) renderer.setVerificationLightingFlags(3);
        if(frames == 400) capture("foliage-day-no-shadow");
        if(frames == 410) renderer.setVerificationLightingFlags(5);
        if(frames == 500) capture("foliage-day-ambient");
        if(frames == 510) { renderer.setVerificationLightingFlags(0);command("time set 12000"); }
        if(frames == 640) capture("foliage-golden-full");
        if(frames == 650) renderer.setVerificationLightingFlags(1);
        if(frames == 740) capture("foliage-golden-no-fog");
        if(frames == 750) renderer.setVerificationLightingFlags(3);
        if(frames == 840) capture("foliage-golden-no-shadow");
        if(frames == 850) renderer.setVerificationLightingFlags(5);
        if(frames == 940) capture("foliage-golden-ambient");
        if(frames == 950) renderer.setVerificationLightingFlags(120);
        if(frames == 1060) captureFoliage("foliage-golden-legacy");
        if(frames == 1070) renderer.setVerificationLightingFlags(0);
        if(frames == 1180) {
            captureFoliage("foliage-golden-refined");
            mapProbe("leaf-transmission","oak_leaves",.5f,.5f,0,false);
            mapProbe("solid-transmission","stone",.5f,.5f,0,false);
        }
        if(frames == 1190) { renderer.setVerificationLightingFlags(120);command("tp AsterCheck 0 86 8 140 22"); }
        if(frames == 1280) captureFoliage("foliage-close-legacy");
        if(frames == 1290) renderer.setVerificationLightingFlags(0);
        if(frames == 1380) captureFoliage("foliage-close-refined");
        if(frames == 1390) renderer.setVerificationLightingFlags(25);
        if(frames == 1480) captureFoliage("foliage-close-shadow-legacy");
        if(frames == 1490) renderer.setVerificationLightingFlags(1);
        if(frames == 1580) captureFoliage("foliage-close-shadow-refined");
        if(frames == 1590) { renderer.setVerificationLightingFlags(0);Minecraft.getInstance().options.improvedTransparency().set(true); }
        if(frames == 1680) captureFoliage("foliage-close-oit");
        if(frames == 1690) { Minecraft.getInstance().options.improvedTransparency().set(false);command("tp AsterCheck 0.7 86 8.5 140 22"); }
        if(frames == 1780) { captureFoliage("foliage-close-motion");renderer.captureFog("foliage-motion",false); }
        if(frames == 1820) finish();
    }

    private static void leafGrainFrames() {
        var renderer=AsterRenderer.INSTANCE;
        if(frames == 80) command("tp AsterCheck 0 86 8 140 22");
        if(frames == 90) { toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled(); }
        if(frames == 100) { toggleByKey();keyboardToggleVerified&=renderer.effectsEnabled();renderer.setVerificationLightingFlags(256); }
        if(frames == 160) captureFoliage("leafgrain-day-legacy");
        if(frames == 170) renderer.setVerificationLightingFlags(0);
        if(frames == 220) captureFoliage("leafgrain-day-refined");
        if(frames == 230) renderer.setVerificationLightingFlags(385);
        if(frames == 280) captureFoliage("leafgrain-day-shadow-legacy");
        if(frames == 290) renderer.setVerificationLightingFlags(129);
        if(frames == 340) captureFoliage("leafgrain-day-shadow-refined");
        if(frames == 350) { renderer.setVerificationLightingFlags(256);command("time set 12000"); }
        if(frames == 410) captureFoliage("leafgrain-golden-legacy");
        if(frames == 420) renderer.setVerificationLightingFlags(0);
        if(frames == 480) captureFoliage("leafgrain-golden-refined");
        if(frames == 490) renderer.setVerificationLightingFlags(385);
        if(frames == 550) captureFoliage("leafgrain-golden-shadow-legacy");
        if(frames == 560) renderer.setVerificationLightingFlags(129);
        if(frames == 620) captureFoliage("leafgrain-golden-shadow-refined");
        if(frames >= 622 && frames < 630) captureFoliage("leafgrain-stationary-"+(frames-622));
        if(frames >= 632 && frames < 680 && (frames-632)%6 == 0) {
            double x=.5+.02*(1+(frames-632)/6);
            command("tp AsterCheck "+x+" 86 8.5 140 22");
        }
        if(frames >= 637 && frames < 685 && (frames-637)%6 == 0)
            captureFoliage("leafgrain-motion-"+((frames-637)/6));
        if(frames == 700) finish();
    }

    private static void captureFoliage(String label) {
        capture(label);AsterRenderer.INSTANCE.captureFoliageMask(label);
    }

    private static void benchmarkFrames() {
        // Fixed camera, seed and weather. Loading warmup and mode transitions
        // are excluded from each 300-frame measurement window.
        if (frames == 600) FrameProfiler.setTag("classic-effects-on");
        if (frames == 900) FrameProfiler.setTag(null);
        if (frames == 910) { capture("benchmark-classic-on"); AsterRenderer.INSTANCE.captureMaterials(); }
        if (frames == 920) { toggleByKey(); keyboardToggleVerified = !AsterRenderer.INSTANCE.effectsEnabled(); }
        if (frames == 1020) FrameProfiler.setTag("classic-effects-off");
        if (frames == 1320) FrameProfiler.setTag(null);
        if (frames == 1330) { capture("benchmark-classic-off"); toggleByKey(); keyboardToggleVerified &= AsterRenderer.INSTANCE.effectsEnabled(); }
        if (frames == 1340) Minecraft.getInstance().options.improvedTransparency().set(true);
        if (frames == 1540) FrameProfiler.setTag("oit-effects-on");
        if (frames == 1840) FrameProfiler.setTag(null);
        if (frames == 1850) capture("benchmark-oit-on");
        if (frames == 1880) {
            Minecraft.getInstance().options.improvedTransparency().set(false);
            AsterRenderer.INSTANCE.setVerificationFogReference(true);
        }
        if (frames == 2080) FrameProfiler.setTag("classic-reference-fog");
        if (frames == 2380) FrameProfiler.setTag(null);
        if (frames == 2390) capture("benchmark-reference-fog");
        if (frames == 2400) AsterRenderer.INSTANCE.setVerificationFogReference(false);
        if (frames == 2600) FrameProfiler.setTag("classic-optimized-repeat");
        if (frames == 2900) FrameProfiler.setTag(null);
        if (frames == 2910) capture("benchmark-optimized-repeat");
        // Isolate the leaf filter cost with the same camera, fog and bloom.
        if (frames == 2940) AsterRenderer.INSTANCE.setVerificationLightingFlags(256);
        if (frames == 3040) FrameProfiler.setTag("classic-leaf-legacy");
        if (frames == 3340) FrameProfiler.setTag(null);
        if (frames == 3360) AsterRenderer.INSTANCE.setVerificationLightingFlags(0);
        if (frames == 3460) FrameProfiler.setTag("classic-leaf-refined-repeat");
        if (frames == 3760) FrameProfiler.setTag(null);
        if (frames == 3780) { FrameProfiler.save(); finish(); }
    }

    private static void cloudFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 20) {command("time set 6000");command("tp AsterCheck .5 110 8.5 90 -25");renderer.setVerificationCloudAngle(.02f);renderer.setVerificationLightingFlags(32768);}
        if(frames == 40) FrameProfiler.setTag("clouds-before");
        if(frames == 120) {FrameProfiler.setTag(null);capture("cloud-day-before");renderer.setVerificationLightingFlags(0);}
        if(frames == 140) FrameProfiler.setTag("clouds-after");
        if(frames == 220) {FrameProfiler.setTag(null);capture("cloud-day-after");}
        if(frames == 230) command("tp AsterCheck .5 110 8.5 180 -35");
        if(frames == 290) capture("cloud-south");
        if(frames == 300) command("tp AsterCheck .5 110 8.5 0 -35");
        if(frames == 360) capture("cloud-north");
        if(frames == 370) command("tp AsterCheck .5 110 8.5 -90 -25");
        if(frames == 430) capture("cloud-east");
        if(frames == 440) command("tp AsterCheck 384.5 110 -256.5 90 -25");
        if(frames == 520) capture("cloud-different-region");
        if(frames == 530) {command("time set 12000");command("tp AsterCheck .5 110 8.5 90 -25");renderer.setVerificationLightingFlags(32768);}
        if(frames == 610) {capture("cloud-sunset-before");renderer.setVerificationLightingFlags(0);}
        if(frames == 690) capture("cloud-sunset-after");
        if(frames == 700) {command("time set 18000");command("tp AsterCheck .5 110 8.5 40 -30");renderer.setVerificationLightingFlags(32768);}
        if(frames == 780) {capture("cloud-night-before");renderer.setVerificationLightingFlags(0);}
        if(frames == 860) capture("cloud-night-after");
        if(frames >= 862 && frames <= 868 && frames%2 == 0) capture("cloud-night-still-"+((frames-862)/2));
        if(frames == 880) {command("time set 6000");command("weather rain");command("tp AsterCheck .5 110 8.5 90 -25");}
        if(frames == 1020) capture("cloud-overcast");
        if(frames == 1030) {command("weather clear");command("tp AsterCheck .5 91 8.5 140 22");}
        if(frames == 1190) capture("cloud-landscape-classic");
        if(frames == 1200) mc.options.improvedTransparency().set(true);
        if(frames == 1260) capture("cloud-landscape-oit");
        if(frames == 1270) {toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames == 1330) {capture("cloud-effects-off");toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();mc.options.improvedTransparency().set(false);}
        if(frames == 1340) mc.getWindow().setWindowed(1025,577);
        if(frames == 1400) {capture("cloud-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames == 1470) capture("cloud-reloaded");
        if(frames == 1480) {mc.getWindow().setWindowed(1280,720);command("time set 6000");command("tp AsterCheck .5 110 8.5 90 -25");}
        if(frames == 1540) capture("cloud-motion-start");
        if(frames >= 1550 && frames <= 1655 && (frames-1550)%15 == 0) {
            int n=(frames-1550)/15;command("tp AsterCheck "+(.5+n*4)+" 110 8.5 "+(90+n*6)+" -25");
        }
        if(frames >= 1555 && frames <= 1660 && (frames-1555)%15 == 0) capture("cloud-pan-"+((frames-1555)/15));
        if(frames == 1670) {command("tp AsterCheck .5 110 8.5 90 -25");renderer.setVerificationCloudAngle(.0215f);}
        if(frames == 1750) capture("cloud-wind");
        if(frames == 1760) {FrameProfiler.save();finish();}
    }

    private static void skyFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 20) { command("time set 6000");command("tp AsterCheck .5 110 8.5 90 -25");renderer.setVerificationLightingFlags(16384); }
        if(frames == 40) FrameProfiler.setTag("sky-previous-day");
        if(frames == 120) FrameProfiler.setTag(null);
        if(frames == 120) { capture("sky-day-before");renderer.setVerificationLightingFlags(0); }
        if(frames == 140) FrameProfiler.setTag("sky-volumetric-day");
        if(frames == 220) FrameProfiler.setTag(null);
        if(frames == 220) { capture("sky-day-after");toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled(); }
        if(frames == 270) {capture("sky-effects-off");toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        if(frames == 280) {command("time set 12000");renderer.setVerificationLightingFlags(16384);}
        if(frames == 360) {capture("sky-sunset-before");renderer.setVerificationLightingFlags(0);}
        if(frames == 450) capture("sky-sunset-after");
        if(frames == 460) command("time set 13000");
        if(frames == 540) capture("sky-twilight");
        if(frames == 550) command("time set 14000");
        if(frames == 630) capture("sky-blue-hour");
        if(frames == 640) {command("time set 18000");renderer.setVerificationLightingFlags(16384);command("tp AsterCheck .5 110 8.5 -90 -50");}
        if(frames == 730) {capture("sky-night-before");renderer.setVerificationLightingFlags(0);}
        if(frames == 830) capture("sky-night-after");
        if(frames >= 832 && frames <= 838 && frames%2 == 0) capture("sky-night-still-"+((frames-832)/2));
        if(frames == 840) command("tp AsterCheck .5 110 8.5 180 -60");
        if(frames == 900) capture("sky-full-moon");
        if(frames == 910) command("time set 162000");
        if(frames == 980) capture("sky-quarter-moon");
        if(frames == 990) command("time set 114000");
        if(frames == 1060) capture("sky-new-moon");
        if(frames == 1070) { command("time set 18000");command("tp AsterCheck .5 110 8.5 40 -30"); }
        if(frames == 1130) capture("sky-night-rotation");
        if(frames == 1140) { command("time set 0");command("tp AsterCheck .5 110 8.5 -90 -15"); }
        if(frames == 1210) capture("sky-sunrise");
        if(frames == 1220) {command("time set 6000");command("weather rain");}
        if(frames == 1350) capture("sky-overcast");
        if(frames == 1360) {command("weather clear");command("tp AsterCheck .5 91 8.5 140 22");mc.options.improvedTransparency().set(true);}
        if(frames == 1430) capture("sky-landscape-oit");
        if(frames == 1440) mc.options.improvedTransparency().set(false);
        if(frames == 1500) capture("sky-landscape-classic");
        if(frames == 1510) mc.getWindow().setWindowed(1025,577);
        if(frames == 1560) {capture("sky-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames == 1630) capture("sky-reloaded");
        if(frames == 1660) {FrameProfiler.save();finish();}
    }

    private static void fogRedesignCapture(String label,boolean diagnostics) {
        capture(label);
        if(diagnostics) AsterRenderer.INSTANCE.captureFogVolume(label,true);
    }
    private static void fogRedesignFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 20) {command("time set 6000");renderer.setVerificationLightingFlags(1);}
        if(frames == 100) {fogRedesignCapture("fog-clear-off",false);renderer.setVerificationLightingFlags(0);}
        if(frames == 180) fogRedesignCapture("fog-clear-on",true);
        if(frames >= 200 && frames < 232 && frames%2 == 0) fogRedesignCapture("fog-still-"+((frames-200)/2),true);
        if(frames == 240) command("time set 11000");
        if(frames == 330) fogRedesignCapture("fog-golden",true);
        if(frames == 340) {command("weather rain");mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1);}
        if(frames == 410) fogRedesignCapture("fog-rain",true);
        if(frames == 420) {command("time set midnight");command("weather clear");mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(0));mc.level.setRainLevel(0);}
        if(frames == 500) fogRedesignCapture("fog-moon",true);
        if(frames == 510) {
            for(int x=40;x<131;x+=30) command("fill "+x+" 65 -12 "+Math.min(130,x+29)+" 105 12 minecraft:air");
            command("fill 40 63 -12 130 64 12 minecraft:stone_bricks");
            command("fill 40 65 -12 130 76 -12 minecraft:stone_bricks");
            command("fill 40 65 12 130 76 12 minecraft:stone_bricks");
            for(int x=48;x<=120;x+=8) {
                command("fill "+x+" 75 -12 "+(x+2)+" 75 12 minecraft:stone_bricks");
                command("fill "+x+" 65 -10 "+(x+1)+" 74 -9 minecraft:stone_bricks");
            }
            command("fill 129 65 -12 130 76 12 minecraft:stone_bricks");
            command("fill 59 65 -6 59 72 -6 minecraft:oak_leaves[persistent=true]");
            command("fill 67 65 5 67 72 5 minecraft:iron_bars");
            command("time set 1000");command("tp AsterCheck 43.5 67 .5 -90 0");renderer.setVerificationLightingFlags(1);
        }
        if(frames == 600) {fogRedesignCapture("fog-building-off",false);renderer.setVerificationLightingFlags(0);}
        if(frames == 700) fogRedesignCapture("fog-building-on",true);
        if(frames >= 702 && frames <= 716 && frames%2 == 0) {
            int i=(frames-702)/2;command("tp AsterCheck "+(43.5+i*.12)+" 67 .5 "+(-90+i*.25)+" 0");
            fogRedesignCapture("fog-pan-"+i,true);
        }
        if(frames == 725) command("tp AsterCheck 43.5 67 .5 -90 0");
        if(frames == 740) command("fill 51 65 -8 51 73 8 minecraft:stone_bricks");
        if(frames == 780) fogRedesignCapture("fog-blocked",true);
        if(frames == 785) command("fill 51 65 -8 51 73 8 minecraft:air");
        if(frames >= 788 && frames <= 802 && frames%2 == 0) fogRedesignCapture("fog-reveal-"+((frames-788)/2),true);
        if(frames == 830) fogRedesignCapture("fog-revealed",true);
        if(frames == 860) command("time set midnight");
        if(frames == 920) fogRedesignCapture("fog-torch-off",true);
        if(frames == 925) {command("setblock 48 66 0 minecraft:lantern");command("setblock 56 68 -6 minecraft:lantern");}
        if(frames == 1020) fogRedesignCapture("fog-torch-on",true);
        if(frames == 1040) {command("setblock 48 66 0 minecraft:air");command("setblock 56 68 -6 minecraft:air");}
        if(frames >= 1044 && frames <= 1058 && frames%2 == 0) fogRedesignCapture("fog-torch-remove-"+((frames-1044)/2),true);
        if(frames == 1110) fogRedesignCapture("fog-torch-removed",true);
        if(frames == 1120) {command("time set 1000");command("fill 64 65 -4 64 71 4 minecraft:glass");command("fill 70 65 -4 70 71 4 minecraft:blue_stained_glass");}
        if(frames == 1180) {fogRedesignCapture("fog-glass-classic",true);mc.options.improvedTransparency().set(true);}
        if(frames == 1250) fogRedesignCapture("fog-glass-oit",true);
        if(frames == 1260) mc.getWindow().setWindowed(1025,577);
        if(frames == 1300) fogRedesignCapture("fog-odd-size",true);
        if(frames == 1310) resourceReload=mc.reloadResourcePacks();
        if(frames == 1380) fogRedesignCapture("fog-reloaded",true);
        if(frames == 1410) {command("fill 70 63 -3 78 66 3 minecraft:glass");command("fill 71 64 -2 77 65 2 minecraft:water");command("tp AsterCheck 74 63.6 0 -90 0");}
        if(frames == 1460) fogRedesignCapture("fog-underwater",true);
        if(frames == 1490) command("tp AsterCheck 43.5 67 .5 -90 0");
        if(frames == 1520) {toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames == 1560) {capture("fog-effects-off");toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        if(frames == 1600) fogRedesignCapture("fog-effects-return",true);
        if(frames == 1610) mc.getWindow().setWindowed(1920,1080);
        if(frames == 1690) FrameProfiler.setTag("fog-volume-1080p");
        if(frames == 1810) {fogRedesignCapture("fog-1080p",true);FrameProfiler.setTag(null);renderer.setVerificationFogNoHistory(true);}
        if(frames == 1880) {fogRedesignCapture("fog-history-off",true);renderer.setVerificationFogNoHistory(false);}
        if(frames == 1960) fogRedesignCapture("fog-history-return",true);
        if(frames == 2000) {FrameProfiler.save();finish();}
    }

    private static void naturalFrames() {
        if (frames == 120) { capture("natural-day-enabled"); AsterRenderer.INSTANCE.captureMaterials(); toggleByKey(); keyboardToggleVerified = !AsterRenderer.INSTANCE.effectsEnabled(); }
        if (frames == 180) { capture("natural-day-disabled"); toggleByKey(); keyboardToggleVerified &= AsterRenderer.INSTANCE.effectsEnabled(); }
        if (frames == 185) Minecraft.getInstance().options.improvedTransparency().set(true);
        if (frames == 260) capture("natural-oit");
        if (frames == 265) command("weather rain");
        if (frames == 360) capture("natural-rain");
        if (frames == 365) command("time set midnight");
        if (frames == 430) capture("natural-night");
        if (frames == 435) {
            command("weather clear"); command("time set 4000");
            command("tp AsterCheck "+(naturalX+48)+" "+(naturalY+4)+" "+(naturalZ-48)+" 45 28");
        }
        if (frames == 520) capture("natural-camera-move");
        if (frames == 530) {
            command("time set 12000");
            command("tp AsterCheck "+naturalX+" "+naturalY+" "+naturalZ+" 140 22");
        }
        if (frames == 630) capture("natural-golden-hour");
        if (frames == 660) finish();
    }

    private static void entityFlags(int flags,String tag) {
        AsterRenderer.INSTANCE.setVerificationLightingFlags(flags);
        FrameProfiler.setTag(tag);
    }
    private static void entityFrames() {
        if(frames == 350) {
            command("fill 40 63 -12 72 64 12 minecraft:smooth_stone");
            command("fill 46 69 -3 51 69 3 minecraft:stone_bricks");
            command("difficulty normal");
            command("summon minecraft:cow 54 65 0 {NoAI:1b,Invulnerable:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\",\"shadow_move\"]}");
            command("summon minecraft:sheep 49 65 0 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:pig 58 65 0 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:horse 63 65 -1 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:chicken 56 65 -5 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:villager 52 65 -5 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:armor_stand 45 65 0 {NoGravity:1b,ShowArms:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.head with minecraft:diamond_helmet");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.chest with minecraft:diamond_chestplate");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.legs with minecraft:diamond_leggings");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.feet with minecraft:diamond_boots");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] weapon.mainhand with minecraft:diamond_sword");
            command("summon minecraft:slime 67 65 0 {NoAI:1b,Size:1,Tags:[\"aster_shadow\"]}");
            command("summon minecraft:spider 60 65 -5 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("tp AsterCheck 56 69 13 180 18");
            AsterRenderer.INSTANCE.setVerificationCloudAngle(0);
            entityFlags(524288,null);
        }
        if(frames == 420) FrameProfiler.setTag("entities-before");
        if(frames == 520) { capture("entity-day-before");entityFlags(0,null); }
        if(frames == 550) FrameProfiler.setTag("entities-after");
        if(frames == 650) { capture("entity-day-after");entityFlags(1048576,null); }
        if(frames == 700) { capture("entity-without-casters");entityFlags(2097152,null); }
        if(frames == 750) { capture("entity-visibility");entityFlags(6291456,null); }
        if(frames == 800) { capture("entity-unshadowed-visibility");entityFlags(524288,null);command("tp AsterCheck 54 67 5 180 15"); }
        if(frames == 860) { capture("entity-close-before");entityFlags(0,null); }
        if(frames == 920) { capture("entity-close-after");command("time set 11000"); }
        if(frames == 980) { capture("entity-golden");command("time set 18000");command("tp AsterCheck 56 68 11 180 14"); }
        if(frames == 1040) { capture("entity-night");Minecraft.getInstance().options.improvedTransparency().set(true); }
        if(frames == 1050) command("setblock 54 65 2 minecraft:torch");
        if(frames == 1080) capture("entity-blocklight");
        if(frames == 1100) { capture("entity-night-oit");command("time set 4000"); }
        if(frames == 1120) command("setblock 54 65 2 minecraft:air");
        if(frames == 1160) { capture("entity-day-oit");Minecraft.getInstance().options.improvedTransparency().set(false); }
        if(frames == 1170) command("tp @e[tag=shadow_move,limit=1] 54 65 0 0 0");
        if(frames == 1200) capture("entity-turned");
        if(frames >= 1210 && frames < 1330 && (frames-1210)%15 == 0) {
            int step=(frames-1210)/15;
            command("tp @e[tag=shadow_move,limit=1] "+(54+step*.3)+" 65 0 "+(step*25)+" 0");
        }
        if(frames >= 1224 && frames <= 1329 && (frames-1224)%15 == 0) capture("entity-motion-"+((frames-1224)/15));
        if(frames == 1340) {
            command("tp @e[tag=aster_shadow,tag=!shadow_move] 1000 65 0");
            command("fill 46 69 -3 51 69 3 minecraft:air");
            command("tp @e[tag=shadow_move,limit=1] 50 65 0 90 0");
            command("time set 11500");
            command("tp AsterCheck 52.5 66.5 0 -90 40");
            entityFlags(1048576,null);
        }
        if(frames == 1410) { capture("entity-offscreen-without-caster");entityFlags(0,null); }
        if(frames == 1470) { capture("entity-offscreen-caster");command("tp AsterCheck 54 67 7 160 15");command("time set 4000"); }
        if(frames == 1530) { capture("entity-return");Minecraft.getInstance().getWindow().setWindowed(1024,576); }
        if(frames == 1540) command("data merge entity @e[tag=shadow_move,limit=1] {Invulnerable:0b}");
        if(frames == 1545) command("damage @e[tag=shadow_move,limit=1] .1");
        if(frames == 1550) capture("entity-hurt");
        if(frames == 1590) { capture("entity-resized");resourceReload=Minecraft.getInstance().reloadResourcePacks(); }
        if(frames == 1650) { capture("entity-reloaded");toggleByKey();keyboardToggleVerified &= !AsterRenderer.INSTANCE.effectsEnabled(); }
        if(frames == 1700) { capture("entity-effects-off");toggleByKey();keyboardToggleVerified &= AsterRenderer.INSTANCE.effectsEnabled(); }
        if(frames == 1760) capture("entity-effects-return");
        if(frames == 1800) { FrameProfiler.save();finish(); }
    }

    private static void entityPolishFrames() {
        if(frames == 350) {
            command("fill 40 63 -12 72 64 12 minecraft:smooth_stone");
            command("fill 46 69 -3 51 69 3 minecraft:stone_bricks");
            command("difficulty normal");
            command("summon minecraft:cow 54 65 0 {NoAI:1b,Invulnerable:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\",\"shadow_move\"]}");
            command("summon minecraft:sheep 49 65 0 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:pig 58 65 0 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:horse 63 65 -1 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:chicken 56 65 -5 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:villager 52 65 -5 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("summon minecraft:armor_stand 45 65 0 {NoGravity:1b,ShowArms:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.head with minecraft:diamond_helmet");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.chest with minecraft:diamond_chestplate");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.legs with minecraft:diamond_leggings");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] armor.feet with minecraft:diamond_boots");
            command("item replace entity @e[type=armor_stand,tag=aster_shadow,limit=1] weapon.mainhand with minecraft:diamond_sword");
            command("summon minecraft:slime 67 65 0 {NoAI:1b,Size:1,Tags:[\"aster_shadow\"]}");
            command("summon minecraft:spider 60 65 -5 {NoAI:1b,Rotation:[90f,0f],Tags:[\"aster_shadow\"]}");
            command("tp AsterCheck 56 69 13 180 18");
            AsterRenderer.INSTANCE.setVerificationCloudAngle(0);
            entityFlags(67108864,null);
        }
        if(frames == 420) FrameProfiler.setTag("polish-before");
        if(frames == 520) { capture("polish-day-before");entityFlags(0,null); }
        if(frames == 550) FrameProfiler.setTag("polish-after");
        if(frames == 650) { capture("polish-day-after");entityFlags(1048576,null); }
        if(frames == 700) { capture("polish-without-casters");entityFlags(2097152,null); }
        if(frames == 750) { capture("polish-visibility");entityFlags(6291456,null); }
        if(frames == 800) { capture("polish-unshadowed-visibility");entityFlags(67108864,null);command("tp AsterCheck 54 67 5 180 15"); }
        if(frames == 860) { capture("polish-close-before");entityFlags(0,null); }
        if(frames == 920) { capture("polish-close-after");command("time set 11000"); }
        if(frames == 980) { capture("polish-golden");command("time set 18000");command("tp AsterCheck 56 68 11 180 14"); }
        if(frames == 1040) { capture("polish-night");Minecraft.getInstance().options.improvedTransparency().set(true); }
        if(frames == 1050) command("setblock 54 65 2 minecraft:torch");
        if(frames == 1080) capture("polish-blocklight");
        if(frames == 1100) { capture("polish-night-oit");command("time set 4000"); }
        if(frames == 1120) command("setblock 54 65 2 minecraft:air");
        if(frames == 1160) { capture("polish-day-oit");Minecraft.getInstance().options.improvedTransparency().set(false); }
        if(frames == 1170) command("tp @e[tag=shadow_move,limit=1] 54 65 0 0 0");
        if(frames == 1200) capture("polish-turned");
        if(frames >= 1210 && frames < 1330 && (frames-1210)%15 == 0) {
            int step=(frames-1210)/15;
            command("tp @e[tag=shadow_move,limit=1] "+(54+step*.3)+" 65 0 "+(step*25)+" 0");
        }
        if(frames >= 1224 && frames <= 1329 && (frames-1224)%15 == 0) capture("polish-motion-"+((frames-1224)/15));
        if(frames == 1340) {
            command("tp @e[tag=aster_shadow,tag=!shadow_move] 1000 65 0");
            command("fill 46 69 -3 51 69 3 minecraft:air");
            command("tp @e[tag=shadow_move,limit=1] 50 65 0 90 0");
            command("time set 11500");
            command("tp AsterCheck 52.5 66.5 0 -90 40");
            entityFlags(1048576,null);
        }
        if(frames == 1410) { capture("polish-offscreen-without-caster");entityFlags(0,null); }
        if(frames == 1470) { capture("polish-offscreen-caster");command("tp AsterCheck 54 67 7 160 15");command("time set 4000"); }
        if(frames == 1530) { capture("polish-return");Minecraft.getInstance().getWindow().setWindowed(1024,576); }
        if(frames == 1540) command("data merge entity @e[tag=shadow_move,limit=1] {Invulnerable:0b}");
        if(frames == 1545) command("damage @e[tag=shadow_move,limit=1] .1");
        if(frames == 1550) capture("polish-hurt");
        if(frames == 1590) { capture("polish-resized");resourceReload=Minecraft.getInstance().reloadResourcePacks(); }
        if(frames == 1650) { capture("polish-reloaded");toggleByKey();keyboardToggleVerified &= !AsterRenderer.INSTANCE.effectsEnabled(); }
        if(frames == 1700) { capture("polish-effects-off");toggleByKey();keyboardToggleVerified &= AsterRenderer.INSTANCE.effectsEnabled(); }
        if(frames == 1760) capture("polish-effects-return");
        if(frames == 1800) {
            Minecraft.getInstance().getWindow().setWindowed(1280,720);
            command("tp @e[tag=shadow_move,limit=1] 54 65 0 90 0");
            command("tp AsterCheck 54 67 5 180 15");
            command("time set 11000");entityFlags(67108864,null);
        }
        if(frames == 1860) {capture("polish-golden-before");entityFlags(0,null);}
        if(frames == 1920) {capture("polish-golden-after");command("time set 18000");entityFlags(67108864,null);}
        if(frames == 1980) {capture("polish-moon-before");entityFlags(0,null);}
        if(frames == 2040) capture("polish-moon-after");
        if(frames == 2050) {
            command("tp @e[tag=shadow_move,limit=1] 1000 65 0");
            command("setblock 47 65 0 minecraft:quartz_block");
            command("setblock 53 67 0 minecraft:quartz_block");
            command("setblock 59 71 0 minecraft:quartz_block");
            command("tp AsterCheck 53 76 14 180 38");
            command("time set 4000");entityFlags(201326592,null);
        }
        if(frames == 2130) {capture("polish-height-before");entityFlags(134217728,null);}
        if(frames == 2190) capture("polish-height-after");
        if(frames >= 2210 && frames <= 2255 && (frames-2210)%15==0) capture("polish-still-"+((frames-2210)/15));
        if(frames >= 2270 && frames < 2390 && (frames-2270)%15==0) {
            int step=(frames-2270)/15;
            command("tp AsterCheck "+(53+step*.035)+" 76 14 180 38");
        }
        if(frames >= 2284 && frames <= 2389 && (frames-2284)%15==0) capture("polish-pan-"+((frames-2284)/15));
        if(frames == 2400) {entityFlags(0,null);command("tp AsterCheck 53 76 14 180 38");}
        if(frames == 2460) capture("polish-height-beauty");
        if(frames == 2500) {FrameProfiler.save();finish();}
    }

    private static void playerShadowFrames() {
        var mc=Minecraft.getInstance();var renderer=AsterRenderer.INSTANCE;
        if(frames==350) {
            command("fill 48 63 -14 84 64 14 minecraft:smooth_stone");
            command("gamemode adventure AsterCheck");command("clear AsterCheck");
            command("time set 11800");command("tp AsterCheck 64.5 65 .5 -90 32");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            renderer.setVerificationCloudAngle(0);renderer.setVerificationWindTime(12f);
            renderer.playerShadow.setVerificationDisabled(true);
        }
        if(frames==420) FrameProfiler.setTag("player-shadow-before");
        if(frames==520) {FrameProfiler.setTag(null);capture("player-first-before");renderer.playerShadow.setVerificationDisabled(false);}
        if(frames==560) FrameProfiler.setTag("player-shadow-after");
        if(frames==660) {FrameProfiler.setTag(null);capture("player-first-after");}
        if(frames==670) {command("time set 4000");command("tp AsterCheck 64.5 65 .5 90 60");renderer.playerShadow.setVerificationDisabled(true);}
        if(frames==730) {capture("player-day-before");renderer.playerShadow.setVerificationDisabled(false);}
        if(frames==790) capture("player-day-after");
        if(frames==800) {command("time set 21000");command("tp AsterCheck 64.5 65 .5 -90 55");renderer.playerShadow.setVerificationDisabled(true);}
        if(frames==860) {capture("player-moon-before");renderer.playerShadow.setVerificationDisabled(false);}
        if(frames==920) capture("player-moon-after");
        if(frames==930) {
            command("time set 11800");
            for(var slot:new String[]{"head","chest","legs","feet"}) command("item replace entity AsterCheck armor."+slot+" with minecraft:diamond_"+(slot.equals("head") ? "helmet" : slot.equals("chest") ? "chestplate" : slot.equals("legs") ? "leggings" : "boots"));
            command("item replace entity AsterCheck weapon.mainhand with minecraft:diamond_sword");
            command("item replace entity AsterCheck weapon.offhand with minecraft:shield");
            renderer.playerShadow.setVerificationDisabled(true);
        }
        if(frames==990) {capture("player-armor-before");renderer.playerShadow.setVerificationDisabled(false);}
        if(frames==1050) capture("player-armor-after");
        // Native input exists only in this hidden client's simulation.
        if(frames==1060) mc.options.keyShift.setDown(true);
        if(frames==1120) {capture("player-crouch");mc.options.keyShift.setDown(false);}
        if(frames==1130) mc.options.keyUp.setDown(true);
        if(frames>=1160 && frames<=1235 && (frames-1160)%15==0) capture("player-walk-"+((frames-1160)/15));
        if(frames==1240) mc.options.keyUp.setDown(false);
        if(frames==1260) mc.options.keyJump.setDown(true);
        if(frames==1262) mc.options.keyJump.setDown(false);
        if(frames==1270) capture("player-jump");
        if(frames==1320) capture("player-landed");
        if(frames==1330) command("tp AsterCheck 64.5 65 .5 -45 38");
        if(frames==1390) capture("player-turned");
        if(frames==1400) {command("tp AsterCheck 64.5 65 .5 -90 25");mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);}
        if(frames==1460) capture("player-third-back");
        if(frames==1470) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        if(frames==1530) capture("player-third-front");
        if(frames==1540) {mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.improvedTransparency().set(true);command("tp AsterCheck 64.5 65 .5 -90 32");}
        if(frames==1600) capture("player-oit");
        if(frames==1610) {mc.options.improvedTransparency().set(false);command("clear AsterCheck");command("effect give AsterCheck minecraft:invisibility infinite 0 true");}
        if(frames==1680) capture("player-invisible");
        if(frames==1690) {command("item replace entity AsterCheck armor.head with minecraft:diamond_helmet");command("item replace entity AsterCheck weapon.mainhand with minecraft:diamond_sword");}
        if(frames==1740) capture("player-invisible-equipment");
        if(frames==1750) {command("effect clear AsterCheck minecraft:invisibility");command("clear AsterCheck");}
        if(frames==1760) command("gamemode spectator AsterCheck");
        if(frames==1820) capture("player-spectator");
        if(frames==1830) {command("gamemode adventure AsterCheck");command("tp AsterCheck 64.5 65 .5 -90 32");}
        if(frames==1880) capture("player-return");
        if(frames==1890) mc.getWindow().setWindowed(1024,576);
        if(frames==1950) capture("player-resized");
        if(frames==1960) resourceReload=mc.reloadResourcePacks();
        if(frames==2020) capture("player-reloaded");
        if(frames==2030) {toggleByKey();keyboardToggleVerified &= !renderer.effectsEnabled();}
        if(frames==2080) capture("player-effects-off");
        if(frames==2090) {toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        if(frames==2140) capture("player-effects-return");
        if(frames==2200) {FrameProfiler.save();finish();}
    }

    private static void lifecycleFrames() {
        if (frames == 350) command("tp AsterCheck 24 73 28 140 18");
        // Resizes only our hidden SDL window; it stays non-focusable and never becomes visible.
        if (frames == 355) Minecraft.getInstance().getWindow().setWindowed(1024,576);
        if (frames == 410) capture("resized");
        if (frames == 415) resourceReload = Minecraft.getInstance().reloadResourcePacks();
        if (frames == 475) capture("reloaded");
        if (frames == 480) command("execute in minecraft:the_nether run tp AsterCheck 0 80 0 140 18");
        if (frames == 520) {
            command("execute in minecraft:the_nether run fill -16 76 -16 16 96 16 minecraft:air");
            command("execute in minecraft:the_nether run fill -16 75 -16 16 75 16 minecraft:netherrack");
            command("execute in minecraft:the_nether run fill -6 76 -6 -3 83 -3 minecraft:glowstone");
        }
        if (frames == 570) capture("nether");
        if (frames == 570) AsterRenderer.INSTANCE.captureFogPhysicsProbe("nether");
        if (frames == 580) command("execute in minecraft:the_end run tp AsterCheck 20 82 24 140 18");
        if (frames == 680) capture("end");
        if (frames == 680) AsterRenderer.INSTANCE.captureFogPhysicsProbe("end");
        if (frames == 690) command("execute in minecraft:overworld run tp AsterCheck 24 73 28 140 18");
        if (frames == 800) capture("overworld-return");
        if (frames == 810) command("tp AsterCheck 48 74 -24 45 18");
        if (frames == 910) command("tp AsterCheck 24 73 28 140 18");
        if (frames == 970) capture("camera-move-return");
        if (frames == 1000) finish();
    }

    private static void finish() {
            try {
                var report = new LinkedHashMap<String,Object>();
                long flags = SDLVideo.SDL_GetWindowFlags(Minecraft.getInstance().getWindow().handle());
                report.put("hiddenWindow", (flags & SDLVideo.SDL_WINDOW_HIDDEN) != 0);
                report.put("notFocusable", (flags & SDLVideo.SDL_WINDOW_NOT_FOCUSABLE) != 0);
                report.put("inputCaptureDisabled", !Minecraft.getInstance().mouseHandler.isMouseGrabbed());
                report.put("strictRenderPearlValidation", FrontendGpuDevice.STRICT_VALIDATION);
                report.put("backend", RenderSystem.getDevice().getDeviceInfo().backendName());
                report.put("worldFramesRendered",frames);
                report.put("profile", PROFILE);
                report.put("captures",captures);
                report.put("scenes",scenes);
                report.put("gpuDebugMessages", gpuMessages);
                report.put("driverDebuggingEnabled", RenderSystem.getDevice().isDebuggingEnabled());
                report.put("shadowProbe",AsterRenderer.INSTANCE.shadowProbeResult());
                report.put("keyboardToggleVerified",keyboardToggleVerified);
                report.put("resourceReloadVerified",resourceReloadVerified);
                report.put("waterFixtureFloorStable",waterFloorCheck != null && waterFloorCheck.isDone() && waterFloorCheck.join());
                report.put("post",AsterRenderer.INSTANCE.postEvidence());
                report.put("materials",AsterRenderer.INSTANCE.materialEvidence());
                report.put("water",AsterRenderer.INSTANCE.waterEvidence());
                report.put("fog",AsterRenderer.INSTANCE.fogEvidence());
                report.put("localLightProbes",LocalLightProbe.evidence());
                report.put("renderDistanceChunks",Minecraft.getInstance().options.renderDistance().get());
                report.put("simulationDistanceChunks",Minecraft.getInstance().options.simulationDistance().get());
                var folder = FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
                AsterClient.LOGGER.info("Automated verification finished: {}",report);
            } catch (Exception ex) { throw new RuntimeException(ex); }
            Minecraft.getInstance().stop();
    }
    private static void solidReflectionFrames() {
        var mc=Minecraft.getInstance();var renderer=AsterRenderer.INSTANCE;var solid=renderer.solidReflections;
        @SuppressWarnings("unchecked") var evidence=(Map<String,Object>)solid.evidence();
        if(!((List<?>)evidence.get("lastResetReasons")).isEmpty() && solid.enabled())solid.captureProbe("solid-reset-"+frames);
        if(frames==1) {
            solid.setVerificationEnabled(false);renderer.setVerificationWindTime(12f);renderer.setVerificationWaterTime(12f);renderer.setVerificationCloudAngle(.02f);
            profileFixture=new CompletableFuture<>();var setup=profileFixture;var server=mc.getSingleplayerServer();
            server.execute(()->{
                try {
                    for(String c:List.of("gamerule minecraft:random_tick_speed 0",
                        "fill 40 64 -10 64 64 16 minecraft:stone_bricks","fill 40 65 -10 64 78 16 minecraft:air",
                        "fill 42 64 -7 48 64 12 minecraft:gold_block","fill 49 64 -7 55 64 12 minecraft:iron_block",
                        "fill 56 64 -7 62 64 12 minecraft:polished_andesite",
                        "fill 42 65 -6 48 70 -6 minecraft:red_concrete","fill 49 65 -6 55 70 -6 minecraft:blue_concrete",
                        "fill 56 65 -6 62 70 -6 minecraft:lime_concrete","setblock 45 69 -5 minecraft:sea_lantern",
                        "setblock 52 69 -5 minecraft:glowstone","fill 43 65 0 43 69 0 minecraft:oak_log",
                        "fill 41 69 -2 45 71 2 minecraft:oak_leaves[persistent=true]",
                        "summon minecraft:cow 53 65 0 {NoAI:1b}","tp AsterCheck 52 67 15 180 22")) {
                        prepareConstructionChunks(server.overworld(),c);
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),c);
                    }
                    setup.complete(null);
                }catch(Throwable ex){setup.completeExceptionally(ex);}
            });
        }
        if(frames>=130 && frames<=133)capture("solid-off-"+(frames-130));
        if(frames==140)solid.setVerificationEnabled(true);
        if(frames>=180 && frames<=183)capture("solid-on-"+(frames-180));
        if(frames==184)solid.captureProbe("solid-day");
        if(frames>=220 && frames<=251) {
            command(String.format(java.util.Locale.ROOT,"tp AsterCheck %.4f 67 15 %.4f 22",52+(frames-220)*.025,180+(frames-220)*.08));
            if(frames>=236 && frames<=251)capture("solid-motion-"+(frames-236));
        }
        if(frames==252)solid.captureProbe("solid-motion");
        if(frames==270)command("tp AsterCheck 52 67 15 90 22");
        if(frames==305){capture("solid-offscreen");solid.captureProbe("solid-offscreen");command("tp AsterCheck 52 67 15 180 22");}
        if(frames==345){capture("solid-return");solid.captureProbe("solid-return");}
        if(frames==350){command("weather rain");mc.level.setRainLevel(1);mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setRainLevel(1));}
        if(frames==410){capture("solid-rain");solid.captureProbe("solid-rain");}
        if(frames==420){command("weather clear");mc.level.setRainLevel(0);mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setRainLevel(0));}
        if(frames==435){command("fill 40 72 -9 64 72 16 minecraft:stone_bricks");command("fill 40 65 -9 64 71 -9 minecraft:stone_bricks");command("fill 40 65 -9 40 71 16 minecraft:stone_bricks");command("fill 64 65 -9 64 71 16 minecraft:stone_bricks");command("fill 40 65 16 64 71 16 minecraft:stone_bricks");}
        if(frames==510){capture("solid-room");solid.captureProbe("solid-room");}
        if(frames==520)command("fill 49 65 -6 55 70 -6 minecraft:air");
        if(frames>=521 && frames<=528)capture("solid-reveal-"+(frames-521));
        if(frames==560){capture("solid-revealed");solid.captureProbe("solid-revealed");mc.options.improvedTransparency().set(true);}
        if(frames==595){capture("solid-oit");mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577);}
        if(frames==630){capture("solid-resized");solid.captureProbe("solid-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames==680){capture("solid-reloaded");solid.captureProbe("solid-reloaded");renderer.temporalAa.setVerificationEnabled(false);}
        if(frames==720){capture("solid-no-taa");solid.captureProbe("solid-no-taa");renderer.temporalAa.setVerificationEnabled(true);toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames==745){capture("solid-effects-off");toggleByKey();keyboardToggleVerified&=renderer.effectsEnabled();}
        if(frames==780){capture("solid-effects-return");command("tp AsterCheck 17 63 8 150 0");}
        if(frames==820){capture("solid-underwater");command("tp AsterCheck 52 67 15 180 22");}
        if(frames==860){capture("solid-water-return");solid.captureProbe("solid-water-return");}
        if(frames==865)command("execute in minecraft:the_nether run tp AsterCheck 0 80 0 140 18");
        if(frames==910){capture("solid-nether");command("execute in minecraft:overworld run tp AsterCheck 52 67 15 180 22");}
        if(frames==970){capture("solid-dimension-return");solid.captureProbe("solid-dimension-return");}
        if(frames==990){FrameProfiler.save();finish();}
    }
    private static void temporalAaFrames() {
        var renderer=AsterRenderer.INSTANCE; var taa=renderer.temporalAa;var mc=Minecraft.getInstance();
        @SuppressWarnings("unchecked") var temporal=(Map<String,Object>)taa.evidence();
        if(!((List<?>)temporal.get("lastResetReasons")).isEmpty() && taa.enabled()) taa.captureProbe("taa-reset-"+frames);
        if(frames==1) {
            taa.setVerificationEnabled(false);renderer.setVerificationWindTime(12f);
            renderer.setVerificationWaterTime(12f);renderer.setVerificationCloudAngle(.02f);
            command("gamerule minecraft:random_tick_speed 0");
            command("fill -3 65 0 5 65 4 minecraft:short_grass");
            command("fill -3 65 6 5 65 6 minecraft:oak_fence");
            command("fill -1 65 8 4 69 8 minecraft:glass_pane");
            command("tp AsterCheck 6 69 20 165 12");
        }
        if(frames>=110 && frames<=117) capture("taa-off-still-"+(frames-110));
        if(frames==120) taa.setVerificationEnabled(true);
        if(frames>=150 && frames<=157) capture("taa-on-still-"+(frames-150));
        if(frames==158) taa.captureProbe("taa-still");
        if(frames>=180 && frames<220) renderer.setVerificationWindTime(12f+(frames-180)/30f);
        if(frames>=200 && frames<=215) capture("taa-wind-"+(frames-200));
        if(frames==216) {taa.captureProbe("taa-wind");taa.captureMotionProbe("taa-wind-motion");}
        if(frames==225) { renderer.setVerificationWindTime(12f);command("gamemode adventure AsterCheck");command("tp AsterCheck 6 65 20 165 12"); }
        if(frames==270) mc.options.keyLeft.setDown(true);
        if(frames>=282 && frames<=297) capture("taa-walk-"+(frames-282));
        if(frames==298) taa.captureProbe("taa-walk");
        if(frames==305) mc.options.keyLeft.setDown(false);
        if(frames==315) {command("gamemode spectator AsterCheck");command("tp AsterCheck 6 69 20 165 12");}
        // Native commands deliberately make a camera cut; first rendered frame
        // must reject every previous pixel, then ordinary reprojection resumes.
        if(frames==355) command("tp AsterCheck 6 69 20 75 12");
        if(frames==380) {capture("taa-camera-cut");taa.captureProbe("taa-cut-settled");command("tp AsterCheck 6 69 20 165 12");}
        if(frames==400) command("tp @e[type=minecraft:cow,limit=1,sort=nearest] 0 65 8");
        if(frames==420) {capture("taa-entity");taa.captureProbe("taa-entity");mc.options.improvedTransparency().set(true);}
        if(frames==450) {capture("taa-oit");mc.options.improvedTransparency().set(false);mc.getWindow().setWindowed(1025,577);}
        if(frames==480) {capture("taa-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames==520) {capture("taa-reloaded");toggleByKey();keyboardToggleVerified=!renderer.effectsEnabled();}
        if(frames==540) {capture("taa-effects-off");toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        if(frames==565) {capture("taa-effects-return");command("tp AsterCheck 17 63 8 150 0");}
        if(frames==595) {capture("taa-underwater");command("tp AsterCheck 6 69 20 165 12");}
        if(frames==630) capture("taa-water-return");
        if(frames==640) command("execute in minecraft:the_nether run tp AsterCheck 0 80 0 140 18");
        if(frames==680) {capture("taa-nether");command("execute in minecraft:overworld run tp AsterCheck 6 69 20 165 12");}
        if(frames==735) {capture("taa-dimension-return");FrameProfiler.save();}
        if(frames==750) finish();
    }

    private static void windFrames() {
        var renderer=AsterRenderer.INSTANCE;var mc=Minecraft.getInstance();
        if(frames == 350) {
            mc.options.improvedTransparency().set(false);renderer.setVerificationWindTime(12f);
            renderer.setVerificationWaterTime(12f);renderer.setVerificationCloudAngle(.02f);
            command("gamerule minecraft:random_tick_speed 0");
            command("fill 40 64 -10 64 64 16 minecraft:grass_block");
            command("fill 40 65 -10 64 78 16 minecraft:air");
            command("fill 43 65 5 47 65 10 minecraft:short_grass");
            command("fill 48 65 5 49 65 8 minecraft:fern");
            command("fill 50 65 5 51 65 8 minecraft:poppy");
            command("fill 52 65 5 53 65 8 minecraft:dandelion");
            for(int x=44;x<=53;x+=3) {
                command("setblock "+x+" 65 1 minecraft:tall_grass[half=lower]");
                command("setblock "+x+" 66 1 minecraft:tall_grass[half=upper]");
                command("setblock "+x+" 65 3 minecraft:sunflower[half=lower]");
                command("setblock "+x+" 66 3 minecraft:sunflower[half=upper]");
            }
            command("fill 43 64 -3 47 64 -1 minecraft:farmland[moisture=7]");
            command("fill 43 65 -3 47 65 -1 minecraft:wheat[age=7]");
            command("setblock 52 65 -1 minecraft:oak_sapling");
            command("fill 57 65 -3 57 69 -3 minecraft:oak_log");
            command("fill 54 69 -6 60 72 0 minecraft:oak_leaves[persistent=true]");
            command("fill 55 63 2 62 63 12 minecraft:sand");
            command("fill 55 64 2 62 64 12 minecraft:water");
            command("tp AsterCheck 53 68 16 180 15");
            renderer.setVerificationLightingFlags(268435456);
        }
        if(frames == 440) {captureWind("wind-static");renderer.setVerificationLightingFlags(0);}
        if(frames == 495) {captureWind("wind-phase-0");renderer.captureWindProbe();}
        // Deterministic 3-second motion sequence, frozen camera/light/water/clouds.
        if(frames >= 500 && frames <= 640 && (frames-500)%20 == 0)
            renderer.setVerificationWindTime(12f+(frames-500)/20*.4f);
        if(frames >= 515 && frames <= 655 && (frames-515)%20 == 0)
            captureWind("wind-motion-"+((frames-515)/20));
        if(frames == 670) renderer.setVerificationWindTime(12f);
        if(frames == 720) {captureWind("wind-classic");mc.options.improvedTransparency().set(true);}
        if(frames == 780) {captureWind("wind-oit");mc.options.improvedTransparency().set(false);command("weather rain");
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setRainLevel(1));mc.level.setRainLevel(1);}
        if(frames == 870) {captureWind("wind-rain");renderer.temporalAa.captureMotionProbe("taa-wind-rain-stationary");mc.options.improvedTransparency().set(true);}
        if(frames == 940) {captureWind("wind-rain-oit");mc.options.improvedTransparency().set(false);command("weather clear");}
        if(frames == 1030) mc.getWindow().setWindowed(1025,577);
        if(frames == 1100) {captureWind("wind-resized");resourceReload=mc.reloadResourcePacks();}
        if(frames == 1170) {captureWind("wind-reloaded");mc.getWindow().setWindowed(1280,720);renderer.setVerificationLightingFlags(262144);}
        if(frames == 1250) {captureWind("wind-direct");renderer.setVerificationLightingFlags(0);toggleByKey();keyboardToggleVerified &= !renderer.effectsEnabled();}
        if(frames == 1310) {capture("wind-effects-off");toggleByKey();keyboardToggleVerified &= renderer.effectsEnabled();}
        if(frames == 1370) {captureWind("wind-effects-return");renderer.setVerificationWindTime(null);}
        if(frames == 1430) captureWind("wind-live-clock");
        if(frames == 1450) finish();
    }
    private static void captureWind(String label) {capture(label);AsterRenderer.INSTANCE.captureWindMask(label);}

    private static void toggleByKey() {
        // Calls the isolated client's handler; no operating-system input is generated.
        var mc = Minecraft.getInstance();
        mc.keyboardHandler.keyPress(mc.getWindow().handle(),InputConstants.PRESS,new KeyEvent(InputConstants.KEY_F8,SDLKeycode.SDLK_F8,0));
    }
    private static void command(String text) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            prepareConstructionChunks(server.overworld(),text);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),text);
        });
    }
    private static void prepareConstructionChunks(net.minecraft.server.level.ServerLevel level,String text) {
        // Hidden fixture commands use absolute coordinates. Fill/setblock reject
        // unloaded positions, so construction must not depend on spawn tickets
        // or the speed at which a moved client's chunk ring catches up.
        String[] words=text.split("\\s+");
        boolean fill=words[0].equals("fill");
        if(!fill && !words[0].equals("setblock")) return;
        int x1=Integer.parseInt(words[1]),z1=Integer.parseInt(words[3]);
        int x2=fill ? Integer.parseInt(words[4]) : x1,z2=fill ? Integer.parseInt(words[6]) : z1;
        for(int x=Math.floorDiv(Math.min(x1,x2),16);x<=Math.floorDiv(Math.max(x1,x2),16);x++)
            for(int z=Math.floorDiv(Math.min(z1,z2),16);z<=Math.floorDiv(Math.max(z1,z2),16);z++) level.getChunk(x,z);
    }
    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        var metadata = new LinkedHashMap<String,Object>();
        metadata.put("renderer",AsterRenderer.INSTANCE.sceneEvidence());
        metadata.put("dimension",mc.level.dimension().identifier().toString());
        metadata.put("camera",new double[]{mc.player.getX(),mc.player.getY(),mc.player.getZ()});
        metadata.put("rain",mc.level.getRainLevel(1));
        metadata.put("gameTime",mc.level.getGameTime());
        scenes.put(name,metadata);
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try {
                var folder=FabricLoader.getInstance().getGameDir().resolve("aster-evidence");
                Files.createDirectories(folder);
                image.writeToFile(folder.resolve(name+".png"));
                captures.add(name+".png");
                AsterClient.LOGGER.info("Saved verification render {}",name);
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { image.close(); }
        });
    }
    private AutomatedVerification() {}
}
