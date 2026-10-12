package dev.colin.aster;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.colin.aster.mixin.ChunkSectionsAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Uses the game's device, encoder, transient allocations and existing chunk meshes. */
public final class AsterRenderer implements AutoCloseable {
    public static final AsterRenderer INSTANCE = new AsterRenderer();
    private static final float SUN_PATH_RADIANS=(float)Math.toRadians(-30);
    private static Vector3f celestialDirection(float angle) {
        float elevation=(float)Math.cos(angle);
        return new Vector3f(-(float)Math.sin(angle),elevation*(float)Math.cos(SUN_PATH_RADIANS),elevation*(float)Math.sin(SUN_PATH_RADIANS));
    }
    private static final float[] RADII = {24, 72, 160};
    private static final int[] RESOLUTIONS = {2048, 2048, 1024};
    private final TextureTarget[] shadows = new TextureTarget[3];
    private final Matrix4f[] shadowMatrices = {new Matrix4f(), new Matrix4f(), new Matrix4f()};
    private final GpuQueryPool[] queries = new GpuQueryPool[8];
    private final boolean[] queryEnabled = new boolean[8];
    private final String[] queryTags = new String[8];
    private final java.util.Map<String,List<double[]>> groupTimings = new LinkedHashMap<>();
    private final List<double[]> enabledTimings = new ArrayList<>();
    private final List<double[]> disabledTimings = new ArrayList<>();
    private final List<SectionRenderDispatcher.RenderSection> casters = new ArrayList<>();
    private TextureTarget sceneCopy;
    private final AsterPostChain post = new AsterPostChain();
    public final AsterTemporalAa temporalAa = new AsterTemporalAa();
    public final AsterSolidReflections solidReflections = new AsterSolidReflections();
    public float uploadedWindTime() { return uploadedWindTime; }
    public float rainAmount() { return state == null ? 0 : 1-state.skyRenderState.rainBrightness; }
    private final MaterialAtlas materials = new MaterialAtlas();
    private final AsterWater water = new AsterWater();
    private final AsterWaterVolume waterVolume = new AsterWaterVolume();
    private final AsterWaterCaustics waterCaustics = new AsterWaterCaustics();
    public void setVerificationWaterLightingDisabled(boolean value) { waterVolume.setVerificationDisabled(value); }
    public void setVerificationWaterReceiverDiagnostic(int mode) { waterVolume.setReceiverDiagnostic(mode); }
    private final AsterFog fog = new AsterFog();
    private final AsterSky sky = new AsterSky();
    private final AsterLocalLighting localLighting = new AsterLocalLighting();
    private Float verificationWaterTime;
    private Float verificationCloudAngle;
    // Keep existing matched shader comparisons stationary; the wind profile
    // explicitly advances phases, and the natural profile uses the live clock.
    private Float verificationWindTime=AutomatedVerification.HIDDEN
        && !System.getProperty("aster.verificationProfile","").equals("wind")
        && !System.getProperty("aster.verificationProfile","").equals("natural") ? 12f : null;
    private float uploadedWindTime;
    private float uploadedWaterTime;
    private int verificationLightingFlags;
    private ChunkSectionsToRender mainChunks;
    private long lastPostTime;
    private net.minecraft.world.phys.Vec3 lastPostCamera;
    private Object lastDimension;
    private TextureTarget opaqueCopy;
    private int opaqueSnapshots;
    private GpuBufferSlice scene;
    private GpuQueryPool currentQuery;
    private boolean enabled = !Boolean.getBoolean("aster.baselineVerification");
    private boolean active;
    private int frames;
    private long lastCasterRefresh;
    private BlockPos lastCameraBlock;
    private ViewArea lastViewArea;
    private int viewAreaChanges;
    private final Vector4f atmosphereColor = new Vector4f();
    private final Vector3f lightingDirection = new Vector3f(0,1,0);
    private boolean directionalSky;
    private LevelRenderState state;
    private int shadowProbeOccluded = -1;
    private int shadowProbeLit = -1;

    public final AsterPlayerShadow playerShadow = new AsterPlayerShadow();
    public boolean directionalSkyActive() { return directionalSky; }
    private AsterRenderer() {}
    public boolean effectsEnabled() { return enabled; }
    public boolean underwaterCamera() { return state != null && state.cameraRenderState.fogType.name().equals("WATER"); }
    public int verificationLightingFlags() { return verificationLightingFlags; }
    public void setVerificationCloudAngle(float angle) {if(AutomatedVerification.HIDDEN) verificationCloudAngle=angle;}
    public void setVerificationWindTime(Float time) {if(AutomatedVerification.HIDDEN) verificationWindTime=time;}
    public void setVerificationReflectionResolution(int percent) { water.setVerificationResolution(percent); }
    public void setVerificationLightingFlags(int flags) {
        if(AutomatedVerification.HIDDEN && verificationLightingFlags != flags) {
            verificationLightingFlags=flags;post.resetHistory();fog.resetHistory("lighting-ablation");water.resetHistory("lighting-ablation");
        }
    }

    public void setAtmosphereColor(Vector4f color) { atmosphereColor.set(color); }
    public Vector3f lightingDirection() { return new Vector3f(lightingDirection); }
    public void bindSky(RenderPass pass) {sky.bind(pass);}
    public void bindLocalLighting(RenderPass pass) {localLighting.bind(pass);}
    public void bindVerificationScene(RenderPass pass) {
        if(AutomatedVerification.HIDDEN && scene!=null) {
            pass.setUniform("AsterScene",scene);localLighting.bind(pass);
        }
    }
    private boolean skyLegacy() { return (verificationLightingFlags & 16384) != 0; }
    public boolean customSkyActive() { return active && enabled && directionalSky && !skyLegacy(); }
    public boolean renderSky() {
        if(!customSkyActive() || scene == null) return false;
        var target=Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Aster procedural atmosphere and celestial sky",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.SKY));
            pass.setUniform("AsterScene",scene);
            sky.bind(pass);fog.bindVolume(pass);
            waterVolume.bind(pass);waterCaustics.bind(pass);
            pass.draw(3,1,0,0);
        }
        return true;
    }

    public Object sceneEvidence() {
        var result = new LinkedHashMap<String,Object>();
        result.put("entityShadows",AsterEntityShadows.evidence());
        result.put("playerShadow",playerShadow.evidence());
        result.put("firstPerson",state.cameraRenderState.isFirstPerson);
        result.put("foliageMesh",FoliageMeshMetadata.evidence());
        result.put("waterVolume",waterVolume.evidence());result.put("waterCaustics",waterCaustics.evidence());
        result.put("pipelineDiagnostics",NativePipelineDiagnostics.evidence());
        result.put("windTimeSeconds",uploadedWindTime);
        result.put("skybox", state.skyRenderState.skybox.name());
        result.put("directionalSky", directionalSky);
        result.put("proceduralSky",enabled && directionalSky && !skyLegacy());
        result.put("verificationCloudAngle",verificationCloudAngle);
        result.put("solarElevation",celestialDirection(state.skyRenderState.sunAngle).y);
        result.put("sunPathDegrees",-30);
        result.put("moonPhase",state.skyRenderState.moonPhase == null ? "none" : state.skyRenderState.moonPhase.name());
        result.put("sky",sky.evidence());
        result.put("worldCamera",WorldCameraTransforms.evidence(state.cameraRenderState));
        result.put("fovDegrees",Minecraft.getInstance().options.fov().get());
        result.put("cameraFluid",state.cameraRenderState.fogType.name());
        result.put("cameraWorldPosition",new double[]{state.cameraRenderState.pos.x(),state.cameraRenderState.pos.y(),state.cameraRenderState.pos.z()});
        result.put("lightDirection",new float[]{lightingDirection.x,lightingDirection.y,lightingDirection.z});
        result.put("verificationLightingFlags",verificationLightingFlags);
        result.put("waterWaveTimeSeconds",uploadedWaterTime);
        result.put("nativeFogColor", new float[]{atmosphereColor.x, atmosphereColor.y, atmosphereColor.z});
        result.put("viewAreaChanges", viewAreaChanges);
        result.put("loadedCasterSections", directionalSky ? casters.size() : 0);
        result.put("effectsEnabled", enabled);
        result.put("opaqueSnapshots", opaqueSnapshots);
        result.put("post",post.evidence());
        result.put("world",WorldScene.evidence());
        result.put("materials",materials.evidence());
        result.put("localLighting",localLighting.evidence());
        result.put("water",water.evidence());
        result.put("fog",fog.evidence());
        result.put("renderDistanceChunks",Minecraft.getInstance().options.renderDistance().get());
        result.put("improvedTransparency",Minecraft.getInstance().gameRenderer.useImprovedTransparency());
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        result.put("width", target.width);
        result.put("height", target.height);
        return result;
    }

    public void toggle() {
        if (Boolean.getBoolean("aster.baselineVerification")) return;
        enabled = !enabled;
        temporalAa.resetHistory("effects-toggle");
        solidReflections.resetHistory("effects-toggle");
        if (enabled) { post.resetHistory(); water.resetHistory("effects-toggle"); fog.resetHistory("effects-toggle"); }
        AsterClient.LOGGER.info("Aster effects {}", enabled ? "enabled" : "disabled");
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("Aster Vulkan: " + (enabled ? "ON" : "OFF")), false);
        saveTimings();
    }

    public void begin(LevelRenderer renderer, LevelRenderState state, FeatureRenderDispatcher.PreparedFrame features, ChunkSectionsToRender chunks, net.minecraft.client.renderer.RenderBuffers nativeBuffers) {
        this.state = state;
        mainChunks = chunks;
        active = true;
        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (enabled) post.ensureSize(main.width,main.height);
        long now = System.nanoTime();
        float deltaSeconds = lastPostTime == 0 ? 1f/30 : Math.clamp((now-lastPostTime)/1_000_000_000f,0.001f,0.1f);
        lastPostTime = now;
        var dimension = Minecraft.getInstance().level.dimension();
        if (dimension != lastDimension || lastPostCamera == null || lastPostCamera.distanceToSqr(state.cameraRenderState.pos) > 64*64) post.resetHistory();
        lastDimension = dimension;
        lastPostCamera = state.cameraRenderState.pos;
        if (opaqueCopy == null || opaqueCopy.width != main.width || opaqueCopy.height != main.height) {
            if (opaqueCopy != null) opaqueCopy.destroyBuffers();
            opaqueCopy = new TextureTarget("Aster opaque scene", main.width, main.height, GpuFormat.RGBA16_FLOAT, GpuFormat.D32_FLOAT);
            encoder.clearColorAndDepthTextures(opaqueCopy.getColorTexture(), new Vector4f(), opaqueCopy.getDepthTexture(), 0);
        }
        for (int i = 0; i < 3; i++) {
            if (shadows[i] == null) shadows[i] = new TextureTarget("Aster shadow " + i, RESOLUTIONS[i], RESOLUTIONS[i], GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        }
        int ring = frames % queries.length;
        if (queries[ring] == null) queries[ring] = device.createTimestampQueryPool(6);
        else if (frames >= queries.length) {
            var values = queries[ring].getValues(0, 6);
            if (java.util.Arrays.stream(values).allMatch(java.util.OptionalLong::isPresent)) {
                double period = device.getDeviceInfo().timestampPeriod() / 1_000_000.0;
                var timings = new double[5];
                for (int i=0;i<5;i++) timings[i] = (values[i+1].getAsLong()-values[i].getAsLong())*period;
                var history = queryEnabled[ring] ? enabledTimings : disabledTimings;
                history.add(timings);
                if (queryTags[ring] != null) groupTimings.computeIfAbsent(queryTags[ring],key -> new ArrayList<>()).add(timings);
                if (history.size() > 1200) history.removeFirst();
            }
        }
        currentQuery = queries[ring];
        queryEnabled[ring] = enabled;
        queryTags[ring] = FrameProfiler.currentTag();
        encoder.writeTimestamp(currentQuery, 0);

        var camera = state.cameraRenderState;
        if(enabled) temporalAa.begin(main.width,main.height,camera,dimension,
            Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTexture());
        if(enabled) solidReflections.begin(main.width,main.height,camera,dimension,
            Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTexture());
        localLighting.update(camera.pos,enabled);
        directionalSky = state.skyRenderState.skybox == DimensionType.Skybox.OVERWORLD;
        float angle = state.skyRenderState.sunAngle;
        var sun = celestialDirection(angle);
        // The moon uses the opposite light direction after sunset.
        float daylight = Math.max(0, sun.y);
        float daylightRamp=Math.clamp(daylight/.18f,0,1);
        daylightRamp=daylightRamp*daylightRamp*(3-2*daylightRamp);
        float lightStrength=directionalSky ? (daylight > 0 ? .08f+.92f*daylightRamp : .025f)*state.skyRenderState.rainBrightness : 0;
        if (sun.y < 0) sun.negate();
        sun.y = Math.max(sun.y, 0.12f);
        sun.normalize();
        lightingDirection.set(sun);
        for (int i = 0; i < 3; i++) {
            float radius = RADII[i];
            var view = new Matrix4f().lookAt(new Vector3f(sun).mul(300), new Vector3f(), new Vector3f(0, 0, 1));
            // Snap in light space using absolute camera position to reduce shadow shimmer.
            var lightCamera = view.transformDirection(new Vector3f((float)camera.pos.x(), (float)camera.pos.y(), (float)camera.pos.z()));
            float texel = radius * 2 / RESOLUTIONS[i];
            float dx = Math.round(lightCamera.x / texel) * texel - lightCamera.x;
            float dy = Math.round(lightCamera.y / texel) * texel - lightCamera.y;
            view.m30(view.m30() - dx).m31(view.m31() - dy);
            shadowMatrices[i].identity().ortho(-radius, radius, -radius, radius, 600, 0, true).mul(view);
        }

        try (var stack = MemoryStack.stackPush()) {
            waterVolume.update(camera.blockPos);
            ByteBuffer data = stack.malloc(512);
            for (int i = 0; i < 3; i++) shadowMatrices[i].get(i * 64, data);
            new Matrix4f(WorldCameraTransforms.projection(camera)).mul(camera.viewRotationMatrix).invert().get(192, data);
            data.position(256);
            put(data, sun.x, sun.y, sun.z, lightStrength);
            uploadedWaterTime=verificationWaterTime == null ? (state.gameTime % 24000 + state.worldPartialTicks) / 20f : verificationWaterTime;
            put(data, (float)camera.pos.x(), (float)camera.pos.y(), (float)camera.pos.z(), uploadedWaterTime);
            var atlas = (TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            materials.ensure(atlas);
            rect(data, atlas.getSprite(Identifier.withDefaultNamespace("block/water_still")));
            rect(data, atlas.getSprite(Identifier.withDefaultNamespace("block/water_flow")));
            put(data, enabled ? 1 : 0, 0.09f, 0.0038f, verificationLightingFlags);
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            put(data, target.width, target.height, camera.fogType.name().equals("WATER") ? 1 : 0, directionalSky ? 1 : 0);
            put(data, atmosphereColor.x, atmosphereColor.y, atmosphereColor.z, state.skyRenderState.rainBrightness);
            for (var name : new String[]{"glowstone","sea_lantern","shroomlight","magma"}) rect(data,atlas.getSprite(Identifier.withDefaultNamespace("block/"+name)));
            String verificationProfile=System.getProperty("aster.verificationProfile","");
            boolean fixedExposure=AutomatedVerification.HIDDEN && ((verificationProfile.equals("solidreflections") || verificationProfile.equals("taa") || verificationProfile.equals("waterlighting") || verificationProfile.equals("fogredesign") || verificationProfile.equals("watergroups")) || verificationProfile.equals("wateredge") || verificationProfile.equals("wateredgecoast") || verificationProfile.equals("wind") || verificationProfile.equals("foliage") || verificationProfile.equals("leafgrain") || verificationProfile.equals("waterlook") || verificationProfile.equals("waves") || verificationProfile.equals("reflections") || verificationProfile.equals("watermotion") || verificationProfile.equals("waterhybrid") || verificationProfile.equals("entities") || verificationProfile.equals("entitypolish") || verificationProfile.equals("playershadow"));
            put(data,deltaSeconds,post.needsReset() ? 1 : 0,fixedExposure ? 1 : 0.6f,fixedExposure ? 1 : 2.0f);
            var solar=celestialDirection(angle);
            put(data,solar.x,solar.y,solar.z,state.skyRenderState.moonPhase == null ? 0 : state.skyRenderState.moonPhase.index()/8f);
            float moonAngle=state.skyRenderState.moonAngle;
            var lunar=celestialDirection(moonAngle);
            put(data,lunar.x,lunar.y,lunar.z,state.skyRenderState.starAngle);
            // A closed drift loop avoids a visible cloud jump at the water clock wrap.
            uploadedWindTime=verificationWindTime == null ? (state.gameTime % 24000 + state.worldPartialTicks)/20f : verificationWindTime;
            put(data,verificationCloudAngle == null ? ((state.gameTime % 240000L)+state.worldPartialTicks)*(float)(Math.PI*2/240000.0) : verificationCloudAngle,uploadedWindTime,
                temporalAa.previousWindTime(),temporalAa.previousRain());
            waterVolume.writeUniform(data);
            data.flip();
            scene = encoder.transientMemory().uploadGpu(data, device.getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
        }

        if(enabled) waterCaustics.render(scene);
        if(enabled) sky.ensure();
        if(enabled && directionalSky && !skyLegacy()) sky.render(scene);
        if(enabled) water.begin(main.width,main.height,camera,dimension,(TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS),scene);
        if(enabled) fog.begin(main.width,main.height,camera,dimension,(TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS),scene,sun,
            lightStrength,
            new Vector3f(atmosphereColor.x,atmosphereColor.y,atmosphereColor.z),state.skyRenderState.rainBrightness);

        playerShadow.prepare(renderer,state,nativeBuffers);
        if (enabled && directionalSky) renderShadows(renderer, features);
        if(enabled) fog.renderVolume(shadows);
        encoder.writeTimestamp(currentQuery, 1);
    }

    private void refreshCasters(LevelRenderer renderer) {
        var camera = state.cameraRenderState;
        var cameraSection = new BlockPos(camera.blockPos.getX()>>4,camera.blockPos.getY()>>4,camera.blockPos.getZ()>>4);
        var area = renderer.viewArea();
        long now = System.nanoTime();
        if (area == lastViewArea && lastCameraBlock != null && lastCameraBlock.equals(cameraSection) && now - lastCasterRefresh < 1_000_000_000L) return;
        if (area != lastViewArea) viewAreaChanges++;
        lastViewArea = area;
        lastCasterRefresh = now;
        lastCameraBlock = cameraSection;
        casters.clear();
        if (area == null) return;
        int cx = camera.blockPos.getX() >> 4, cz = camera.blockPos.getZ() >> 4;
        int range = Math.min(area.getViewDistance(), 10);
        for (int x = cx-range; x <= cx+range; x++) for (int z = cz-range; z <= cz+range; z++)
            for (int y = area.minSectionY(); y <= area.maxSectionY(); y++) {
                var section = area.getRenderSectionAt(new BlockPos(x*16, y*16, z*16));
                if (section != null && section.getSectionMesh() != null && section.getSectionMesh().hasRenderableLayers()) casters.add(section);
            }
    }

    private void renderShadows(LevelRenderer renderer, FeatureRenderDispatcher.PreparedFrame features) {
        AsterEntityShadows.beginFrame();
        refreshCasters(renderer);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var visible = renderer.visibleSections();
        var saved = new ArrayList<>(visible);
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        var sampler = (verificationLightingFlags&16) != 0 ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR,true);
        try {
            for (int cascade = 0; cascade < 3; cascade++) {
                visible.clear();
                var camera = state.cameraRenderState;
                float radius = RADII[cascade];
                for (var caster : casters) {
                    var origin = caster.getRenderOrigin();
                    var center = new Vector4f((float)(origin.getX()+8-camera.pos.x()), (float)(origin.getY()+8-camera.pos.y()), (float)(origin.getZ()+8-camera.pos.z()), 1);
                    shadowMatrices[cascade].transform(center);
                    float margin = 16/radius;
                    if (Math.abs(center.x) < 1+margin && Math.abs(center.y) < 1+margin) visible.add(caster);
                }
                var chunks = state.shouldUseMultiDrawIndirectForTerrain ? renderer.prepareChunkRendersIndirect(new Matrix4f(), false) : renderer.prepareChunkRenders(new Matrix4f(), false);
                RenderSystem.resizeAllAutoStorageIndexBuffers();
                try (var stack = MemoryStack.stackPush()) {
                    var matrixBytes = stack.malloc(64);
                    shadowMatrices[cascade].get(0, matrixBytes);
                    var matrix = encoder.transientMemory().uploadGpu(matrixBytes, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
                    var entityProjection = new Matrix4f(shadowMatrices[cascade]).mul(new Matrix4f(camera.viewRotationMatrix).invert());
                    entityProjection.get(0, matrixBytes);
                    var entityMatrix = encoder.transientMemory().uploadGpu(matrixBytes, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
                    int index = cascade;
                    try (var pass = encoder.createRenderPass(() -> "Aster shadow cascade " + index, shadows[cascade].getColorTextureView(), Optional.of(new Vector4f()), shadows[cascade].getDepthTextureView(), OptionalDouble.of(0))) {
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("ShadowProjection", matrix);
                        pass.setUniform("AsterScene",scene);
                        ((ChunkSectionsAccessor)chunks).aster$renderLayers(ChunkSectionLayerGroup.OPAQUE.layers(), sampler, pass, atlas, Minecraft.getInstance().gameRenderer.lightmap(), AsterPipelines.SHADOW, AsterPipelines.SHADOW_INDIRECT);
                        pass.setUniform("Projection", entityMatrix);
                        if((verificationLightingFlags&524288) != 0) features.executeSolid(pass);
                        else if((verificationLightingFlags&1048576) == 0) {
                            var entityPass=new AsterShadowPass(pass,entityMatrix);
                            features.executeSolid(entityPass);
                            features.executeTranslucent(entityPass);
                            playerShadow.execute(entityPass);
                        }
                    }
                }
            }
        } finally {
            visible.clear();
            visible.addAll(saved);
            playerShadow.endFrame();
        }
    }

    public void bindScene(RenderPass pass) {
        if (active && scene != null) {
            pass.setUniform("AsterScene", scene);
            localLighting.bind(pass);
            fog.bindVolume(pass);
            sky.bind(pass);
            waterVolume.bind(pass);waterCaustics.bind(pass);
            pass.setUniform("OpaqueColor", opaqueCopy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("OpaqueDepth", opaqueCopy.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            water.bindOutput(pass,opaqueCopy);
            if (materials.view() != null) {
                var sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR,true);
                pass.setUniform("MaterialAtlas",materials.view(),sampler);
                pass.setUniform("NormalAtlas",materials.normalView(),sampler);
            }
            for (int i=0;i<shadows.length;i++) if(shadows[i] != null) {
                pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                // Reuse the existing shadow colour attachment for leaf caster
                // identity; shadow depth, geometry and allocations stay intact.
                pass.setUniform("ShadowCaster"+i,shadows[i].getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }
        }
    }

    public void bindUnderwaterFocusedScatter(RenderPass pass) {
        waterCaustics.bindSourceFields(pass);
    }

    public void bindTemporalInputs(RenderPass pass) {
        water.bindOutput(pass,opaqueCopy);
        var sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        pass.setUniform("TaaOpaqueColor",opaqueCopy.getColorTextureView(),sampler);
        pass.setUniform("TaaSceneColor",sceneCopy.getColorTextureView(),sampler);
    }

    public void captureWaterLightingProbe() {
        if(!AutomatedVerification.HIDDEN) return;
        waterCaustics.captureAcceptance(scene);
        final int probeHeight=30;
        var target=new TextureTarget("Aster water lighting acceptance",512,probeHeight,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster water lighting acceptance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WATER_LIGHTING_PROBE));
            pass.setUniform("AsterScene",scene);waterVolume.bind(pass);waterCaustics.bind(pass);sky.bind(pass);waterCaustics.bindSourceFields(pass);
            for(int i=0;i<3;i++) pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3,1,0,0);
        }
        net.minecraft.client.Screenshot.takeScreenshot(target,image -> {
            try {
                int[] counts=new int[6];
                for(int x=0;x<512;x++) for(int y=0;y<2;y++) {
                    int pixel=image.getPixel(x,probeHeight-1-y);
                    if(net.minecraft.util.ARGB.red(pixel)==255) counts[y*3]++;
                    if(net.minecraft.util.ARGB.green(pixel)==255) counts[y*3+1]++;
                    if(net.minecraft.util.ARGB.blue(pixel)==255) counts[y*3+2]++;
                }
                var result=new LinkedHashMap<String,Object>();
                int minFocus=255,maxFocus=0;double meanFocus=0;
                for(int x=0;x<512;x++) {int f=net.minecraft.util.ARGB.red(image.getPixel(x,probeHeight-3));minFocus=Math.min(minFocus,f);maxFocus=Math.max(maxFocus,f);meanFocus+=f/512.0;}
                result.put("focusMin",minFocus/255.0*3.15-.65);result.put("focusMax",maxFocus/255.0*3.15-.65);result.put("focusMean",meanFocus/255.0*3.15-.65);
                result.put("solarCloudTransmittance",net.minecraft.util.ARGB.green(image.getPixel(0,probeHeight-3))/255.0);
                double cacheMean=0,gateMean=0,gainMean=0,gainMin=4,gainMax=0;double[] receivedMean=new double[3];
                for(int x=0;x<512;x++) {
                    int measured=image.getPixel(x,probeHeight-4),received=image.getPixel(x,probeHeight-5);
                    cacheMean+=(net.minecraft.util.ARGB.red(measured)/255.0*3.15-.65)/512.0;
                    gateMean+=net.minecraft.util.ARGB.green(measured)/255.0/512.0;
                    double gain=net.minecraft.util.ARGB.blue(measured)*4.0/255.0;gainMean+=gain/512.0;gainMin=Math.min(gainMin,gain);gainMax=Math.max(gainMax,gain);
                    receivedMean[0]+=net.minecraft.util.ARGB.red(received)*4.0/255.0/512.0;receivedMean[1]+=net.minecraft.util.ARGB.green(received)*4.0/255.0/512.0;receivedMean[2]+=net.minecraft.util.ARGB.blue(received)*4.0/255.0/512.0;
                }
                result.put("actualReceiverCacheFocusMean",cacheMean);result.put("actualReceiverNominalGateMean",gateMean);
                result.put("actualReceiverGainMean",gainMean);result.put("actualReceiverGainMin",gainMin);result.put("actualReceiverGainMax",gainMax);result.put("actualReceiverColorMean",receivedMean);
                var receiverBands=new LinkedHashMap<String,Object>();
                for(int band=0;band<2;band++) {
                    int row=band==0 ? 5 : 8;
                    double focus=0,gate=0,gain=0,depth=0,coverage=0;int wet=0;
                    double minimum=4,maximum=0;double[] color=new double[3];
                    for(int x=0;x<512;x++) {
                        int measured=image.getPixel(x,probeHeight-1-row),received=image.getPixel(x,probeHeight-2-row),geometry=image.getPixel(x,probeHeight-3-row);
                        focus+=(net.minecraft.util.ARGB.red(measured)/255.0*3.15-.65)/512.0;
                        gate+=net.minecraft.util.ARGB.green(measured)/255.0/512.0;
                        double value=net.minecraft.util.ARGB.blue(measured)*4.0/255.0;gain+=value/512.0;minimum=Math.min(minimum,value);maximum=Math.max(maximum,value);
                        color[0]+=net.minecraft.util.ARGB.red(received)*4.0/255.0/512.0;color[1]+=net.minecraft.util.ARGB.green(received)*4.0/255.0/512.0;color[2]+=net.minecraft.util.ARGB.blue(received)*4.0/255.0/512.0;
                        depth+=net.minecraft.util.ARGB.red(geometry)*8.0/255.0/512.0;coverage+=net.minecraft.util.ARGB.green(geometry)/255.0/512.0;
                        if(net.minecraft.util.ARGB.blue(geometry)==255) wet++;
                    }
                    var measured=new LinkedHashMap<String,Object>();
                    measured.put("bedTopY",band==0 ? 64 : 63);measured.put("depthMean",depth);measured.put("wetSamples",wet);measured.put("coverageMean",coverage);
                    measured.put("focusMean",focus);measured.put("nominalGateMean",gate);measured.put("gainMean",gain);measured.put("gainMin",minimum);measured.put("gainMax",maximum);measured.put("colorMean",color);
                    receiverBands.put(band==0 ? "shallow" : "intermediate",measured);
                }
                result.put("receiverBands",receiverBands);
                int[] orientationCounts=new int[3];double[] verticalColor=new double[3];
                for(int x=0;x<512;x++) {
                    int contract=image.getPixel(x,probeHeight-12),received=image.getPixel(x,probeHeight-13);
                    if(net.minecraft.util.ARGB.red(contract)==255) orientationCounts[0]++;
                    if(net.minecraft.util.ARGB.green(contract)==255) orientationCounts[1]++;
                    if(net.minecraft.util.ARGB.blue(contract)==255) orientationCounts[2]++;
                    verticalColor[0]+=net.minecraft.util.ARGB.red(received)/255.0/512;
                    verticalColor[1]+=net.minecraft.util.ARGB.green(received)/255.0/512;
                    verticalColor[2]+=net.minecraft.util.ARGB.blue(received)/255.0/512;
                }
                result.put("orientationContracts",List.of("wetVerticalAbsorptionWithoutBedFocus","dryVerticalIdentity","incidentIdentityAndMonotonicity"));
                result.put("orientationPassedCounts",orientationCounts);result.put("wetVerticalColorMean",verticalColor);
                int[] farCounts=new int[3];double farDepthMean=0;
                for(int x=0;x<512;x++) {
                    int contract=image.getPixel(x,probeHeight-14),geometry=image.getPixel(x,probeHeight-15);
                    if(net.minecraft.util.ARGB.red(contract)==255) farCounts[0]++;
                    if(net.minecraft.util.ARGB.green(contract)==255) farCounts[1]++;
                    if(net.minecraft.util.ARGB.blue(contract)==255) farCounts[2]++;
                    farDepthMean+=net.minecraft.util.ARGB.red(geometry)*8.0/255.0/512;
                }
                result.put("farContracts",List.of("nativeFarWetDepth","farAbsorptionWithoutFineAtlas","farDryRejected"));
                result.put("farPassedCounts",farCounts);result.put("farReceiverDepthMean",farDepthMean);
                int[] farBedCounts=new int[3];double farGainMin=4,farGainMax=0,farGainMean=0;
                for(int x=0;x<512;x++) {
                    int contract=image.getPixel(x,probeHeight-16),measured=image.getPixel(x,probeHeight-17);
                    if(net.minecraft.util.ARGB.red(contract)==255) farBedCounts[0]++;
                    if(net.minecraft.util.ARGB.green(contract)==255) farBedCounts[1]++;
                    if(net.minecraft.util.ARGB.blue(contract)==255) farBedCounts[2]++;
                    double gain=net.minecraft.util.ARGB.green(measured)*4.0/255.0;
                    farGainMin=Math.min(farGainMin,gain);farGainMax=Math.max(farGainMax,gain);farGainMean+=gain/512;
                }
                result.put("farBedContracts",List.of("actualWetFarBedCovered","farFocusAndGainBounded","solidBelowBedRejected"));
                result.put("farBedPassedCounts",farBedCounts);result.put("farBedGainMin",farGainMin);result.put("farBedGainMax",farGainMax);result.put("farBedGainMean",farGainMean);
                int[] volumeCounts=new int[5];int volumePositive=0,volumeSaturated=0;
                double[] volumeColor=new double[3];
                for(int x=0;x<512;x++) {
                    int contract=image.getPixel(x,probeHeight-18),radiance=image.getPixel(x,probeHeight-19),extra=image.getPixel(x,probeHeight-20);
                    if(net.minecraft.util.ARGB.red(contract)==255) volumeCounts[0]++;
                    if(net.minecraft.util.ARGB.green(contract)==255) volumeCounts[1]++;
                    if(net.minecraft.util.ARGB.blue(contract)==255) volumeCounts[2]++;
                    if(net.minecraft.util.ARGB.green(extra)==255) volumeCounts[3]++;
                    if(net.minecraft.util.ARGB.blue(extra)==255) volumeCounts[4]++;
                    if(net.minecraft.util.ARGB.red(extra)==255) volumePositive++;
                    int[] code={net.minecraft.util.ARGB.red(radiance),net.minecraft.util.ARGB.green(radiance),net.minecraft.util.ARGB.blue(radiance)};
                    for(int i=0;i<3;i++) {volumeColor[i]+=code[i]/4080.0/512;if(code[i]==255) volumeSaturated++;}
                }
                result.put("volumeContracts",List.of("zeroPathIdentity","actualDryPathRejected","nonnegativeFiniteBoundedRadiance","viewWeightPartition","boundedPhaseFunction"));
                result.put("volumePassedCounts",volumeCounts);result.put("volumePositiveSamples",volumePositive);
                result.put("volumeScatterColorMean",volumeColor);result.put("volumeRadianceCodeStep",1.0/4080);
                result.put("volumeRadianceCodeMaximum",.0625);result.put("volumeSaturatedChannelSamples",volumeSaturated);
                int[] planarCounts=new int[6];int oldOriginFailures=0,longPlanarPaths=0,oldCapFailures=0;
                double planarOriginErrorMax=0;
                for(int x=0;x<512;x++) {
                    for(int y=20;y<22;y++) {
                        int contract=image.getPixel(x,probeHeight-1-y);
                        if(net.minecraft.util.ARGB.red(contract)==255) planarCounts[(y-20)*3]++;
                        if(net.minecraft.util.ARGB.green(contract)==255) planarCounts[(y-20)*3+1]++;
                        if(net.minecraft.util.ARGB.blue(contract)==255) planarCounts[(y-20)*3+2]++;
                    }
                    int numeric=image.getPixel(x,probeHeight-23),control=image.getPixel(x,probeHeight-24);
                    planarOriginErrorMax=Math.max(planarOriginErrorMax,net.minecraft.util.ARGB.green(numeric)*.0001/255.0);
                    if(net.minecraft.util.ARGB.red(control)==255) oldOriginFailures++;
                    if(net.minecraft.util.ARGB.green(control)==255) longPlanarPaths++;
                    if(net.minecraft.util.ARGB.blue(control)==255) oldCapFailures++;
                }
                result.put("planarPathContracts",List.of("planeIntersection","forwardCollinearity","boundedFiniteAndZeroIdentity","fullPathBeerComposition","capturedFixtureRegression","orderedTransmissionAndZeroIdentity"));
                result.put("planarPathPassedCounts",planarCounts);result.put("legacyPlanarOriginFailures",oldOriginFailures);
                result.put("longPlanarPathsOver100Blocks",longPlanarPaths);result.put("legacyPlanarCapFailures",oldCapFailures);
                result.put("planarOriginErrorMaxQuantized",planarOriginErrorMax);
                result.put("planarPathProbeScope","512 GPU rays with camera-relative plane heights .2678856125/1.7678856125/3.2678856125, plus the measured shallow-fixture regression and old-formula failure controls. Sampled planar geometry, not curved water intersections or all-world bounds. Original20 diagnostic rows and thresholds unchanged.");
                int[] directionalCounts=new int[12];
                for(int x=0;x<512;x++) for(int y=24;y<28;y++) {
                    int contract=image.getPixel(x,probeHeight-1-y);
                    if(net.minecraft.util.ARGB.red(contract)==255) directionalCounts[(y-24)*3]++;
                    if(net.minecraft.util.ARGB.green(contract)==255) directionalCounts[(y-24)*3+1]++;
                    if(net.minecraft.util.ARGB.blue(contract)==255) directionalCounts[(y-24)*3+2]++;
                }
                result.put("directionalContracts",List.of("nativeWetGeometry","nativeDryGeometry","unitDirectionAndInterface","interfaceSnellLaw","orderedIncidentTransmission","boundedFocusGain","dryTransferIdentity","declaredDiffuseSkyPath","verticalReceiverNoFocus","directContributionTransfer","zeroDirectIdentity","incomingRayOrientation"));
                result.put("directionalPassedCounts",directionalCounts);
                result.put("directionalProbeScope","512 actual native wet/dry receiver samples of the production waterLightAt/waterDirectRadiance helpers. Existing24 rows and thresholds retained. Lightmap sky split and diffuse path are declared approximations; these contracts are not actual lamp-pixel invariance, all-world transport or a visual quality percentage.");
                int[] shaftCounts=new int[6];
                for(int x=0;x<512;x++) for(int y=0;y<2;y++) {
                    int pixel=image.getPixel(x,probeHeight-29-y);
                    if(net.minecraft.util.ARGB.red(pixel)==255) shaftCounts[y*3]++;
                    if(net.minecraft.util.ARGB.green(pixel)==255) shaftCounts[y*3+1]++;
                    if(net.minecraft.util.ARGB.blue(pixel)==255) shaftCounts[y*3+2]++;
                }
                result.put("shaftContracts",List.of("positiveQuadraticSegmentWeights","48BlockExtinctionIntegral","zeroSegmentIdentity","solarNightRejected","fullRainRejected","boundedAvailabilityAndForwardPhase"));
                result.put("shaftPassedCounts",shaftCounts);
                result.put("samples",512);result.put("contracts",List.of("boundedLens","clockLoop","lightDepthWeatherGates","absorptionIdentityAndMonotonicity","nativeWetReceiver","dryBankRejected"));
                result.put("passedCounts",counts);result.put("passed",java.util.Arrays.stream(counts).allMatch(v -> v==512)
                    && java.util.Arrays.stream(orientationCounts).allMatch(v -> v==512)
                    && java.util.Arrays.stream(farCounts).allMatch(v -> v==512)
                    && java.util.Arrays.stream(farBedCounts).allMatch(v -> v==512)
                    && java.util.Arrays.stream(volumeCounts).allMatch(v -> v==512) && volumePositive>0
                    && java.util.Arrays.stream(planarCounts).allMatch(v -> v==512) && oldOriginFailures==512
                    && longPlanarPaths>=32 && oldCapFailures==longPlanarPaths
                    && java.util.Arrays.stream(directionalCounts).allMatch(v -> v==512)
                    && java.util.Arrays.stream(shaftCounts).allMatch(v -> v==512));
                java.nio.file.Files.writeString(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/water-lighting-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/water-lighting-probe.png"));
            } catch(Exception ex) {throw new RuntimeException(ex);} finally {image.close();target.destroyBuffers();}
        });
    }

    public void captureOpaque() {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        opaqueCopy.copyColorFrom(main);
        opaqueCopy.copyDepthFrom(main);
        if(enabled && directionalSky && !state.cameraRenderState.fogType.name().equals("WATER")) fog.recoverOpaque(opaqueCopy);
        opaqueSnapshots++;
        if(enabled) water.render(mainChunks,opaqueCopy,shadows,state.shouldUseMultiDrawIndirectForTerrain);
    }

    public void captureShadowDebug() {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var opaquePreview = new TextureTarget("Aster opaque HDR preview",opaqueCopy.width,opaqueCopy.height,GpuFormat.RGBA8_UNORM,null);
        try (var pass=encoder.createRenderPass(() -> "Aster opaque HDR preview",opaquePreview.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.HDR_DEBUG));
            pass.setUniform("HdrInput",opaqueCopy.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3,1,0,0);
        }
        net.minecraft.client.Screenshot.takeScreenshot(opaquePreview, image -> {
            try { image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/opaque-scene.png")); }
            catch (Exception ex) { throw new RuntimeException(ex); }
            finally { image.close(); opaquePreview.destroyBuffers(); }
        });
        var preview = new TextureTarget("Aster shadow debug", 512, 512, GpuFormat.RGBA8_UNORM, null);
        try (var pass = encoder.createRenderPass(() -> "Aster shadow debug", preview.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.SHADOW_DEBUG));
            pass.setUniform("Shadow0",shadows[1].getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3,1,0,0);
        }
        net.minecraft.client.Screenshot.takeScreenshot(preview, image -> {
            try { image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/shadow-depth.png")); }
            catch (Exception ex) { throw new RuntimeException(ex); }
            finally { image.close(); preview.destroyBuffers(); }
        });
        var probe = new TextureTarget("Aster shadow acceptance probe",2,1,GpuFormat.RGBA8_UNORM,null);
        try (var pass = encoder.createRenderPass(() -> "Aster shadow acceptance probe",probe.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.SHADOW_PROBE));
            pass.setUniform("AsterScene",scene);
            pass.setUniform("Shadow0",shadows[1].getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3,1,0,0);
        }
        net.minecraft.client.Screenshot.takeScreenshot(probe,image -> {
            try {
                shadowProbeOccluded = image.getPixel(0,0)&255;
                shadowProbeLit = image.getPixel(1,0)&255;
                AsterClient.LOGGER.info("Physical shadow probe: occluded={}, lit={}",shadowProbeOccluded,shadowProbeLit);
            } finally { image.close(); probe.destroyBuffers(); }
        });
    }

    public Object shadowProbeResult() {
        var result = new LinkedHashMap<String,Object>();
        result.put("occluded",shadowProbeOccluded);
        result.put("lit",shadowProbeLit);
        result.put("passed",shadowProbeOccluded >= 0 && shadowProbeOccluded < 64 && shadowProbeLit > 192);
        return result;
    }
    public void captureHdrProbe() { post.captureProbe(); }
    public void captureFoliageMask(String label) {
        captureVegetationMask(label,false);
    }
    public void captureWindMask(String label) {
        captureVegetationMask(label,true);
    }
    private void captureVegetationMask(String label,boolean wind) {
        if(!AutomatedVerification.HIDDEN || mainChunks == null) return;
        var mc=Minecraft.getInstance();
        var world=WorldScene.target();
        var mask=new TextureTarget("Aster foliage coverage",world.width,world.height,GpuFormat.RGBA8_UNORM,null);
        var sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR,true);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        try(var pass=encoder.createRenderPass(() -> "Aster foliage coverage",mask.getColorTextureView(),Optional.of(new Vector4f()),world.getDepthTextureView(),OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("AsterScene",scene);
            pass.setUniform("MaterialAtlas",materials.view(),sampler);
            pass.setUniform("NormalAtlas",materials.normalView(),sampler);
            var atlas=mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
            ((ChunkSectionsAccessor)mainChunks).aster$renderLayers(ChunkSectionLayerGroup.OPAQUE.layers(),sampler,pass,atlas,mc.gameRenderer.lightmap(),wind ? AsterPipelines.WIND_MASK : AsterPipelines.FOLIAGE_MASK,wind ? AsterPipelines.WIND_MASK_INDIRECT : AsterPipelines.FOLIAGE_MASK_INDIRECT);
        }
        net.minecraft.client.Screenshot.takeScreenshot(mask,image -> {
            try { image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/"+label+(wind ? "-wind-mask.png" : "-foliage-mask.png"))); }
            catch(Exception ex) { throw new RuntimeException(ex); }
            finally { image.close();mask.destroyBuffers(); }
        });
    }
    public void captureMaterials() { post.captureMaterials(); }
    public void captureUnderwaterReceiverProbe(String label) { post.captureUnderwaterReceiverProbe(label); }
    public void bindUnderwaterReplayProbe(RenderPass pass) {
        bindUnderwaterReceiverProbe(pass);
        fog.bindVolume(pass);
    }
    public void bindUnderwaterReceiverProbe(RenderPass pass) {
        if(!AutomatedVerification.HIDDEN || scene==null) throw new IllegalStateException("Receiver diagnostic requires a captured private scene");
        // Verification runs after the world pass has ended. bindScene's active
        // guard intentionally excludes that time, so bind the snapshot directly.
        pass.setUniform("AsterScene",scene);
        waterVolume.bind(pass);waterCaustics.bind(pass);sky.bind(pass);
        water.bindOutput(pass,opaqueCopy);
        if(opaqueCopy==null) throw new IllegalStateException("Receiver diagnostic requires captured opaque depth");
        pass.setUniform("OpaqueDepth",opaqueCopy.getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        for(int i=0;i<shadows.length;i++) if(shadows[i]!=null)
            pass.setUniform("Shadow"+i,shadows[i].getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
    public void captureWindProbe() {
        if(!AutomatedVerification.HIDDEN) return;
        var target=new TextureTarget("Aster wind field acceptance",256,2,GpuFormat.RGBA8_UNORM,null);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Aster wind field acceptance",target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(AsterPipelines.WIND_PROBE));pass.setUniform("AsterScene",scene);pass.draw(3,1,0,0);
        }
        net.minecraft.client.Screenshot.takeScreenshot(target,image -> {
            try { image.writeToFile(FabricLoader.getInstance().getGameDir().resolve("aster-evidence/wind-probe.png")); }
            catch(Exception ex) {throw new RuntimeException(ex);}
            finally {image.close();target.destroyBuffers();}
        });
    }
    public void captureMaterialProbe(String label) { materials.captureProbe(label,((TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS)).getSprite(Identifier.withDefaultNamespace("block/gold_block"))); }
    public void captureMapProbe(String label,String sprite,float u,float v,int mip,boolean normal) {
        materials.captureProbe(label,((TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS)).getSprite(Identifier.parse(sprite)),u,v,mip,normal);
    }
    public Object materialEvidence() { return materials.evidence(); }
    public void captureBrdfProbe() { materials.captureBrdfProbe(); }
    public Object postEvidence() { return post.evidence(); }
    public Object waterEvidence() { return water.evidence(); }
    public void captureWater(String label,boolean colors) { water.capture(label,colors); }
    public void captureWaterOpticsProbe() { water.captureOpticsProbe(); }
    public void captureWaterWaveProbe() { water.captureWaveProbe(); }
    public void captureWaterEdgeProbe(String label) { water.captureEdgeProbe(label); }
    public void captureWaterContactProbe(String label) { water.captureContactProbe(label); }
    public void captureWaterSourceProbe(String label) { water.captureSourceProbe(label); }
    public void bindWater(RenderPass pass) { water.bindOutput(pass,opaqueCopy); }
    public Object fogEvidence() { return fog.evidence(); }
    public void captureFog(String label,boolean colors) { fog.capture(label,colors); }
    public void captureFogPhysicsProbe(String label) { fog.capturePhysicsProbe(label); }
    public void captureFogVolume(String label,boolean colors) { fog.captureVolume(label,colors); }
    public void setVerificationFogNoHistory(boolean value) {fog.setVerificationNoHistory(value);}
    public void captureFogShadowProbe() { fog.captureShadowProbe(); }
    public void setVerificationFogReference(boolean value) { fog.setVerificationReference(value); }
    public void setVerificationWaterTime(Float time) { if(AutomatedVerification.HIDDEN) verificationWaterTime=time; }

    public void composite() {
        if (!active) return;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeTimestamp(currentQuery, 2);
        if (enabled) {
            var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (sceneCopy == null || sceneCopy.width != main.width || sceneCopy.height != main.height) {
                if (sceneCopy != null) sceneCopy.destroyBuffers();
                sceneCopy = new TextureTarget("Aster scene copy", main.width, main.height, GpuFormat.RGBA16_FLOAT, null);
            }
            sceneCopy.copyColorFrom(main);
            post.render(mainChunks,sceneCopy,shadows,currentQuery,fog);
        } else {
            encoder.writeTimestamp(currentQuery,3);
            encoder.writeTimestamp(currentQuery,4);
        }
        encoder.writeTimestamp(currentQuery, 5);
        active = false;
        frames++;
        if (frames % 300 == 0) saveTimings();
    }

    private static void put(ByteBuffer data, float a, float b, float c, float d) { data.putFloat(a).putFloat(b).putFloat(c).putFloat(d); }
    private static void rect(ByteBuffer data, TextureAtlasSprite sprite) { put(data, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()); }

    private void saveTimings() {
        var report = new LinkedHashMap<String, Object>();
        report.put("frames", frames);
        report.put("effectsEnabled", enabled);
        report.put("cascadeRadiiBlocks", RADII);
        report.put("shadowResolutions", RESOLUTIONS);
        report.put("loadedCasterSections", casters.size());
        report.put("opaqueSnapshots", opaqueSnapshots);
        report.put("post",post.evidence());
        report.put("water",water.evidence());
        report.put("waterVolume",waterVolume.evidence());report.put("waterCaustics",waterCaustics.evidence());
        report.put("fog",fog.evidence());
        report.put("localLighting",localLighting.evidence());
        report.put("enabled", statistics(enabledTimings));
        report.put("disabled", statistics(disabledTimings));
        report.put("perSceneTags",groupTimingEvidence());
        report.put("timingSource", "Native GPU timestamp queries; milliseconds. World interval includes engine work between shadows and composite.");
        try {
            var file = FabricLoader.getInstance().getGameDir().resolve("aster-evidence/rendering.json");
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception ex) { AsterClient.LOGGER.error("Unable to write rendering evidence", ex); }
    }
    public Object groupTimingEvidence() {
        var result = new LinkedHashMap<String,Object>();
        for (var entry : groupTimings.entrySet()) result.put(entry.getKey(),statistics(entry.getValue()));
        return result;
    }

    private static Object statistics(List<double[]> values) {
        var report = new LinkedHashMap<String, Object>();
        report.put("samples", values.size());
        if (!values.isEmpty()) {
            String[] names = {"shadows", "world", "materials", "hdrBloomExposure", "tonemap"};
            for (int i=0; i<names.length; i++) {
                final int column = i;
                var sorted = values.stream().mapToDouble(v -> v[column]).sorted().toArray();
                report.put(names[i]+"MedianMs", sorted[sorted.length/2]);
                report.put(names[i]+"P95Ms", sorted[Math.min(sorted.length-1,(int)(sorted.length*0.95))]);
            }
        }
        return report;
    }

    @Override public void close() {
        saveTimings();
        for (int i=0; i<shadows.length; i++) if (shadows[i] != null) { shadows[i].destroyBuffers(); shadows[i] = null; }
        if (sceneCopy != null) { sceneCopy.destroyBuffers(); sceneCopy = null; }
        if (opaqueCopy != null) { opaqueCopy.destroyBuffers(); opaqueCopy = null; }
        playerShadow.close();
        post.close();
        temporalAa.close();
        solidReflections.close();
        materials.close();
        water.close();
        waterVolume.close();waterCaustics.close();
        fog.close();
        sky.close();
        localLighting.close();
        mainChunks = null;
        lastPostCamera = null;
        lastDimension = null;
        lastPostTime = 0;
        for (int i=0; i<queries.length; i++) if (queries[i] != null) { queries[i].close(); queries[i] = null; }
        casters.clear();
        lastCameraBlock = null;
        lastViewArea = null;
        lastCasterRefresh = 0;
        active = false;
        scene = null;
    }
}
