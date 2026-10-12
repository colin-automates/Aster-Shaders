package dev.colin.aster;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Diagnostics of actual native fluid vertices, including concurrent rebuilds. */
public final class WaterMeshMetadata {
    public record Shore(int x,int z,boolean[] pinned) {}
    private static final LongAdder water=new LongAdder(),pinned=new LongAdder();
    private WaterMeshMetadata() {}
    public static void record(boolean shore) { water.increment();if(shore) pinned.increment(); }
    public static Object evidence() { return Map.of("taggedVertices",water.sum(),"pinnedVertices",pinned.sum()); }
}
