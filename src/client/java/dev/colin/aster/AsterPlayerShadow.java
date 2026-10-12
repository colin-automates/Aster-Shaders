package dev.colin.aster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.colin.aster.mixin.FeatureDispatcherAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import java.util.LinkedHashMap;

/** Replays Minecraft's already extracted first-person avatar only into depth. */
public final class AsterPlayerShadow implements AutoCloseable {
    private final SubmitNodeStorage submits = new SubmitNodeStorage();
    private FeatureRenderDispatcher dispatcher;
    private StagedVertexBuffer vertices;
    private FeatureRenderDispatcher.PreparedFrame prepared;
    private boolean verificationDisabled;
    private int draws, avatarId = -1;
    private String reason = "not-prepared", pose = "none", skinModel = "none";
    private float walkPosition, walkSpeed;
    private double[] position = new double[3];

    public void setVerificationDisabled(boolean disabled) {
        if(AutomatedVerification.HIDDEN) verificationDisabled=disabled;
    }

    public void prepare(LevelRenderer renderer, LevelRenderState state, RenderBuffers nativeBuffers) {
        endFrame();
        draws=0;avatarId=-1;reason="inactive";
        var mc=Minecraft.getInstance();
        var camera=state.cameraRenderState;
        var player=state.playerRenderState;
        if(!AsterRenderer.INSTANCE.effectsEnabled() || !AsterRenderer.INSTANCE.directionalSkyActive()) return;
        if(verificationDisabled) {reason="verification-disabled";return;}
        if(!camera.isFirstPerson) {reason="native-third-person";return;}
        if(mc.player == null || mc.getCameraEntity()!=mc.player || !player.hasPlayer || player.avatarRenderState==null) {reason="no-local-camera-player";return;}
        var avatar=player.avatarRenderState;
        // Sleeping first-person bodies are already in Minecraft's world list.
        if(avatar.isSpectator || avatar.bedOrientation!=null) {reason=avatar.isSpectator ? "spectator" : "native-sleeping-body";return;}
        avatarId=avatar.id;pose=avatar.pose.name();skinModel=avatar.skin.model().name();
        position=new double[]{avatar.x,avatar.y,avatar.z};
        walkPosition=avatar.walkAnimationPos;walkSpeed=avatar.walkAnimationSpeed;
        if(dispatcher==null) {
            vertices=new StagedVertexBuffer(() -> "Aster local player shadow",65536);
            dispatcher=new FeatureRenderDispatcher(nativeBuffers,mc.getModelManager(),mc.getAtlasManager(),mc.font,mc.gameRenderer.gameRenderState());
            ((FeatureDispatcherAccessor)dispatcher).aster$setStagedVertexBuffer(vertices);
        }
        // Use classic submission for the depth replay even when world OIT is on.
        submits.setUseImprovedTransparency(false);
        boolean invisibleToPlayer=avatar.isInvisibleToPlayer;
        try {
            // Native invisible bodies can be drawn faintly for their owner.
            // Omit that body from depth, while keeping visible armor/items.
            if(avatar.isInvisible) avatar.isInvisibleToPlayer=true;
            renderer.entityRenderDispatcher().submit(avatar,camera,avatar.x-camera.pos.x(),avatar.y-camera.pos.y(),avatar.z-camera.pos.z(),new PoseStack(),submits);
        } finally {avatar.isInvisibleToPlayer=invisibleToPlayer;}
        prepared=dispatcher.prepareFrame(submits);
        reason="shadow-only-first-person";
    }

    public void execute(RenderPass pass) {
        if(prepared==null) return;
        int before=AsterEntityShadows.drawCount();
        prepared.executeSolid(pass);
        prepared.executeTranslucent(pass);
        draws+=AsterEntityShadows.drawCount()-before;
    }

    public void endFrame() {
        if(prepared!=null) {prepared.close();prepared=null;vertices.endFrame();}
    }

    public Object evidence() {
        var result=new LinkedHashMap<String,Object>();
        result.put("reason",reason);result.put("drawsAcrossThreeCascades",draws);
        result.put("avatarId",avatarId);result.put("pose",pose);result.put("skinModel",skinModel);
        result.put("position",position);result.put("walkAnimationPosition",walkPosition);result.put("walkAnimationSpeed",walkSpeed);
        result.put("verificationDisabled",verificationDisabled);result.put("independentVertexBuffer",vertices!=null);
        result.put("usesExtractedNativeAvatar",true);result.put("mainViewBodySubmitted",false);
        return result;
    }

    @Override public void close() {
        endFrame();
        if(dispatcher!=null) {dispatcher.close();dispatcher=null;}
        if(vertices!=null) {vertices.close();vertices=null;}
    }
}
