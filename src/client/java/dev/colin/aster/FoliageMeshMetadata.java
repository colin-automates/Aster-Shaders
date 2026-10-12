package dev.colin.aster;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Bend height/type and native face brightness in unused terrain tint alpha. */
public final class FoliageMeshMetadata {
    private static final LongAdder plants=new LongAdder(),leaves=new LongAdder(),roots=new LongAdder(),tall=new LongAdder();
    private FoliageMeshMetadata() {}

    public static void tag(BlockState state,BakedQuad quad,QuadInstance instance) {
        if(Boolean.getBoolean("aster.baselineVerification"))return;
        int kind=kind(state);
        var override=quad.materialInfo().shadeDirectionOverride();
        float shade=CardinalLighting.DEFAULT.byFace(override==null?quad.direction():override);
        for(int i=0;i<4;i++){
            int color=instance.getColor(i);if(kind==0 && (color>>>24)!=255)continue;
            instance.setColor(i,(color&0x00ffffff)|(alpha(state,quad.position(i).y(),kind,shade)<<24));
        }
    }
    public static void tag(BlockState state,MutableQuadView quad) {
        if(Boolean.getBoolean("aster.baselineVerification"))return;
        int kind=kind(state);
        var override=quad.shadeDirectionOverride();
        // Enhanced mod meshes can use a custom non-cardinal shade. Leave their
        // tint intact rather than inventing an inverse from fragment normals.
        float shade=override!=null?CardinalLighting.DEFAULT.byFace(override):quad.shadeMode()==ShadeMode.VANILLA
            ?CardinalLighting.DEFAULT.byFace(quad.lightFace()):1f;
        for(int i=0;i<4;i++){
            int color=quad.color(i);if(kind==0 && (color>>>24)!=255)continue;
            quad.color(i,(color&0x00ffffff)|(alpha(state,quad.y(i),kind,shade)<<24));
        }
    }
    private static int kind(BlockState state) {
        if(Boolean.getBoolean("aster.baselineVerification")) return 0;
        if(state.getBlock() instanceof LeavesBlock) return 2;
        // Aquatic vegetation keeps its native geometry; wind is an air effect.
        if(!(state.getBlock() instanceof VegetationBlock) || !state.getFluidState().isEmpty()
            || state.getBlock() instanceof LeafLitterBlock || state.getBlock() instanceof LilyPadBlock) return 0;
        return 1;
    }
    private static int alpha(BlockState state,float y,int kind,float shade) {
        boolean upper=state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
        float height=Math.clamp(y+(upper ? 1f : 0f),0f,2f);
        // Plants 1..63 retain bend height; solid/leaf ranges encode the exact
        // native cardinal multiplier. Fluid metadata stays at 128..254.
        int face=shade<.55f?0:shade<.7f?1:shade<.9f?2:3;
        int alpha=kind == 2 ? 96+face : kind == 1 ? 1+Math.round(height*31f) : 64+face;
        if(kind==0)return alpha;
        if(kind == 2) leaves.increment();else {
            plants.increment();if(alpha == 1) roots.increment();if(upper) tall.increment();
        }
        return alpha;
    }

    public static Object evidence() {
        return Map.of("plantVertices",plants.sum(),"leafVertices",leaves.sum(),"anchoredRootVertices",roots.sum(),
            "upperPlantVertices",tall.sum(),"plantHeightStepsPerBlock",31,"leafAlphaRange",java.util.List.of(96,99),
            "faceShadeAlphaRange",java.util.List.of(64,67));
    }
}
