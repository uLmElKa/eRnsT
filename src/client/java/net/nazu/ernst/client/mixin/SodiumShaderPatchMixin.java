package net.nazu.ernst.client.mixin;

import net.nazu.ernst.client.ERnsTClient;
import net.nazu.ernst.client.ERnsTConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Locale;

// Добавляет наши эффекты блоков в шейдер рельефа Sodium (оттенок затенения, упрощение текстур).
// Если у Sodium другие названия, миксин просто ничего не делает (require = 0).
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser", remap = false)
public class SodiumShaderPatchMixin {

    private static boolean logged = false;

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    @ModifyVariable(method = "parseShader", at = @At("HEAD"), argsOnly = true, require = 0, remap = false)
    private static String ernst$patchShader(String src) {
        try {
            ERnsTConfig c = ERnsTConfig.get();
            if (!ERnsTClient.modEnabled || !c.sodiumTerrainEffects) return src;

            String marker = "fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);";
            if (src == null || !src.contains("u_BlockTex") || !src.contains(marker)) return src;

            float e = c.terrainEffect;
            String patch = """
                    // eRnsT: упрощение текстур, цветное затенение и холодный оттенок теней
                    {
                        vec4 ernst_s = textureLod(u_BlockTex, v_TexCoord, 2.0);
                        vec3 ernst_soft = ernst_s.rgb * v_Color.rgb;
                        color.rgb = mix(color.rgb, ernst_soft, %s * step(0.99, ernst_s.a));
                        float ernst_lum = dot(v_Color.rgb, vec3(0.299, 0.587, 0.114));
                        float ernst_depth = clamp(1.0 - ernst_lum / 0.85, 0.0, 1.0);
                        color.rgb = mix(color.rgb, color.rgb * 1.25, ernst_depth * %s);
                        color.rgb *= mix(vec3(1.0), vec3(0.88, 0.95, 1.10), ernst_depth * %s);
                    }
                    """.formatted(f(0.2 * e), f(0.35 * e), f(c.shadeTint * e));

            if (!logged) {
                logged = true;
                ERnsTClient.LOGGER.info("[eRnsT] Шейдер блоков Sodium дополнен нашими эффектами");
            }
            return src.replace(marker, patch + "    " + marker);
        } catch (Throwable t) {
            ERnsTClient.LOGGER.warn("[eRnsT] Не удалось дополнить шейдер Sodium", t);
            return src;
        }
    }
}
