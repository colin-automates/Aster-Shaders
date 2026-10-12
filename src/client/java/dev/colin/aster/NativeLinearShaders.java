package dev.colin.aster;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.ShaderDefines;
import java.util.regex.Pattern;

/** Adapts native color outputs before float blending; alpha/depth phases stay intact. */
public final class NativeLinearShaders {
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*\\)");
    private NativeLinearShaders() {}
    public static String adapt(String name, String source, ShaderType type, ShaderDefines defines) {
        if (type != ShaderType.FRAGMENT || !defines.flags().contains("ASTER_LINEAR_WORLD") || !name.startsWith("minecraft:")) return source;
        if (name.endsWith("/oit_composite")) return source;
        boolean weather = defines.flags().contains("ASTER_WEATHER");
        boolean splash = defines.flags().contains("ASTER_RAIN_SPLASH");
        // The same opacity reaches depth bounds, transmittance and colour.
        // Changing colour alone leaves native rain as dark, opaque curtains.
        if(weather || splash) source=source.replace("if (color.a < 0.1)","if (color.a < 0.015)")
            .replace("#ifdef OIT_ALPHA_ONLY\n    executeAlphaOnlyPhase", "color.a *= "+(weather ? ".24" : ".28")+";\n    #ifdef OIT_ALPHA_ONLY\n    executeAlphaOnlyPhase");
        if (defines.flags().contains("OIT_ALPHA_ONLY")) return source;
        String helper = "\nvec3 asterDecode(vec3 c) { c=max(c,vec3(0)); return mix(c/12.92,pow((c+0.055)/1.055,vec3(2.4)),step(vec3(0.04045),c)); }\n";
        String outputDecode = weather || splash ? "asterWeatherDecode" : "asterDecode";
        if(weather || splash) helper += "\nvec3 asterWeatherDecode(vec3 c) { vec3 v=asterDecode(c); float l=dot(v,vec3(.2126,.7152,.0722)); return vec3(.87,.91,.96)*l; }\n";
        // Apply atmospheric transport to each fragment before alpha/OIT
        // weighting, so glass and particles use their own surface depth.
        String fogHelper = "\n#include <aster_vulkan:scene.glsl>\n#include <aster_vulkan:fog_forward.glsl>\n#include <aster_vulkan:lighting.glsl>\n";
        if (defines.flags().contains("OIT_ACCUMULATE")) {
            // Decode straight RGB before the native transmittance/alpha weighting.
            source = source.replace("sampleColorForAccumulation(color)","sampleColorForAccumulation(vec4("+outputDecode+"(color.rgb),color.a))")
                .replace("FogColor.rgb","lightingFogColor(asterDecode(FogColor.rgb))");
            int firstFunction = source.indexOf("vec4 calculateFinalColor");
            if (firstFunction < 0) throw new IllegalStateException("Unsupported native OIT color shader: "+name);
            source=source.substring(0,firstFunction)+helper+fogHelper+source.substring(firstFunction);
            source=source.replace("vec4 calculateFinalColor(","vec4 asterNativeFinalColor(");
            return MAIN.matcher(source).replaceFirst("vec4 calculateFinalColor(vec4 color) { return fogSurface(asterNativeFinalColor(color)); }\nvoid main()");
        }
        if (!source.contains("out vec4 fragColor")) throw new IllegalStateException("Unsupported native color shader: "+name);
        source = MAIN.matcher(source).replaceFirst("void asterNativeMain()");
        return source+helper+fogHelper+"\nvoid main() { asterNativeMain(); fragColor.rgb="+outputDecode+"(fragColor.rgb); fragColor=fogSurface(fragColor); }\n";
    }
}
