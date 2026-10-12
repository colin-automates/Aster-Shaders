package dev.colin.aster;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.lwjgl.PointerBuffer;
import net.minecraft.client.Minecraft;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Splits the native opaque/translucent pass at a legal Vulkan pass boundary. */
public final class AsterWorldPass implements RenderPass {
    private RenderPass delegate;
    private final GpuTextureView color, depth;

    public AsterWorldPass(RenderPass delegate, GpuTextureView color, GpuTextureView depth) {
        this.delegate = delegate;
        this.color = color;
        this.depth = depth;
    }

    public void captureOpaqueAndContinue() {
        delegate.close();
        delegate = null;
        AsterRenderer.INSTANCE.captureOpaque();
        // OIT opens its own native stages after this scope. Avoid creating an
        // empty continuation pass and redundant attachment transitions there.
        if (!Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            delegate = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Aster native translucency", color, Optional.empty(), depth, OptionalDouble.empty());
            RenderSystem.bindDefaultUniforms(delegate);
        }
    }

    @Override public void close() { if (delegate != null) { delegate.close(); delegate = null; } }
    @Override public void pushDebugGroup(Supplier<String> label) { delegate.pushDebugGroup(label); }
    @Override public void popDebugGroup() { delegate.popDebugGroup(); }
    @Override public void writeTimestamp(GpuQueryPool pool, int index) { delegate.writeTimestamp(pool,index); }
    @Override public void setPipeline(CompiledRenderPipeline pipeline) { delegate.setPipeline(pipeline); }
    @Override public void setUniform(String name, GpuTextureView view, GpuSampler sampler) { delegate.setUniform(name,view,sampler); }
    @Override public void setUniform(String name, GpuBuffer buffer) { delegate.setUniform(name,buffer); }
    @Override public void setUniform(String name, GpuBufferSlice buffer) { delegate.setUniform(name,buffer); }
    @Override public void pushConstants(ByteBuffer data) { delegate.pushConstants(data); }
    @Override public void enableScissor(int x, int y, int width, int height) { delegate.enableScissor(x,y,width,height); }
    @Override public void disableScissor() { delegate.disableScissor(); }
    @Override public void setVertexBuffer(int slot, GpuBufferSlice buffer) { delegate.setVertexBuffer(slot,buffer); }
    @Override public void setIndexBuffer(GpuBuffer buffer, IndexType type) { delegate.setIndexBuffer(buffer,type); }
    @Override public void drawIndexed(int first, int count, int vertex, int instances, int baseInstance) { delegate.drawIndexed(first,count,vertex,instances,baseInstance); }
    @Override public void multiDrawIndexed(IntBuffer offsets, int count, int stride, int baseVertex) { delegate.multiDrawIndexed(offsets,count,stride,baseVertex); }
    @Override public void multiDrawIndexed(PointerBuffer offsets, IntBuffer counts, IntBuffer vertices, int count) { delegate.multiDrawIndexed(offsets,counts,vertices,count); }
    @Override public void drawIndexedIndirect(GpuBufferSlice buffer, int count) { delegate.drawIndexedIndirect(buffer,count); }
    @Override public <T> void drawMultipleIndexed(Collection<Draw<T>> draws, GpuBuffer indices, IndexType type, Collection<String> uniforms, T context) { delegate.drawMultipleIndexed(draws,indices,type,uniforms,context); }
    @Override public void draw(int count, int instances, int first, int baseInstance) { delegate.draw(count,instances,first,baseInstance); }
    @Override public void multiDraw(IntBuffer data, int count, int stride, int instances) { delegate.multiDraw(data,count,stride,instances); }
    @Override public void multiDraw(IntBuffer offsets, IntBuffer counts, int count) { delegate.multiDraw(offsets,counts,count); }
    @Override public void drawIndirect(GpuBufferSlice buffer, int count) { delegate.drawIndirect(buffer,count); }
}
