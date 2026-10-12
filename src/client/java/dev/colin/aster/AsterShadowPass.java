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

/** Protects the light projection against per-draw native default uniform binds. */
public final class AsterShadowPass implements RenderPass {
    private final RenderPass delegate;
    private final GpuBufferSlice projection;
    private boolean casting;
    public AsterShadowPass(RenderPass delegate,GpuBufferSlice projection) {
        this.delegate=delegate;this.projection=projection;
    }

    @Override public void close() { /* Owner closes the shared cascade pass. */ }
    @Override public void pushDebugGroup(Supplier<String> label) { delegate.pushDebugGroup(label); }
    @Override public void popDebugGroup() { delegate.popDebugGroup(); }
    @Override public void writeTimestamp(GpuQueryPool pool, int index) { delegate.writeTimestamp(pool,index); }
    @Override public void setPipeline(CompiledRenderPipeline pipeline) { var selected=AsterEntityShadows.select(pipeline);casting=selected != null;if(casting) delegate.setPipeline(selected); }
    @Override public void setUniform(String name, GpuTextureView view, GpuSampler sampler) { if(!name.startsWith("Shadow")) delegate.setUniform(name,view,sampler); }
    @Override public void setUniform(String name, GpuBuffer buffer) { if(name.equals("Projection")) delegate.setUniform(name,projection);else delegate.setUniform(name,buffer); }
    @Override public void setUniform(String name, GpuBufferSlice buffer) { delegate.setUniform(name,name.equals("Projection") ? projection : buffer); }
    @Override public void pushConstants(ByteBuffer data) { delegate.pushConstants(data); }
    @Override public void enableScissor(int x, int y, int width, int height) { delegate.enableScissor(x,y,width,height); }
    @Override public void disableScissor() { delegate.disableScissor(); }
    @Override public void setVertexBuffer(int slot, GpuBufferSlice buffer) { delegate.setVertexBuffer(slot,buffer); }
    @Override public void setIndexBuffer(GpuBuffer buffer, IndexType type) { delegate.setIndexBuffer(buffer,type); }
    @Override public void drawIndexed(int first, int count, int vertex, int instances, int baseInstance) { if(casting) { AsterEntityShadows.drew();delegate.drawIndexed(first,count,vertex,instances,baseInstance); } }
    @Override public void multiDrawIndexed(IntBuffer offsets, int count, int stride, int baseVertex) { if(casting) { AsterEntityShadows.drew();delegate.multiDrawIndexed(offsets,count,stride,baseVertex); } }
    @Override public void multiDrawIndexed(PointerBuffer offsets, IntBuffer counts, IntBuffer vertices, int count) { if(casting) { AsterEntityShadows.drew();delegate.multiDrawIndexed(offsets,counts,vertices,count); } }
    @Override public void drawIndexedIndirect(GpuBufferSlice buffer, int count) { if(casting) { AsterEntityShadows.drew();delegate.drawIndexedIndirect(buffer,count); } }
    @Override public <T> void drawMultipleIndexed(Collection<Draw<T>> draws, GpuBuffer indices, IndexType type, Collection<String> uniforms, T context) { if(casting) { AsterEntityShadows.drew();delegate.drawMultipleIndexed(draws,indices,type,uniforms,context); } }
    @Override public void draw(int count, int instances, int first, int baseInstance) { if(casting) { AsterEntityShadows.drew();delegate.draw(count,instances,first,baseInstance); } }
    @Override public void multiDraw(IntBuffer data, int count, int stride, int instances) { if(casting) { AsterEntityShadows.drew();delegate.multiDraw(data,count,stride,instances); } }
    @Override public void multiDraw(IntBuffer offsets, IntBuffer counts, int count) { if(casting) { AsterEntityShadows.drew();delegate.multiDraw(offsets,counts,count); } }
    @Override public void drawIndirect(GpuBufferSlice buffer, int count) { if(casting) { AsterEntityShadows.drew();delegate.drawIndirect(buffer,count); } }
}
