package net.nazu.ernst.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.nazu.ernst.client.ERnsTClient;
import net.nazu.ernst.client.ERnsTConfig;
import net.nazu.ernst.client.ERnsTDay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class FogColorMixin {

    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void ernst$fogColor(Camera camera, float partialTicks, ClientLevel level,
                                       int renderDistance, float darken, CallbackInfo ci) {
        if (!ERnsTClient.modEnabled) return;
        ERnsTConfig c = ERnsTConfig.get();
        if (!c.skyEnabled) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (camera.getFluidInCamera() != FogType.NONE) return;

        float d = ERnsTDay.dayness(level, partialTicks);
        float rain = 1.0f - c.rainDarken * level.getRainLevel(partialTicks);

        // Под землёй туман темнеет так же, как в ваниле
        float depth = Mth.clamp(((float) camera.getPosition().y - level.getMinBuildHeight()) * 0.03125f, 0.0f, 1.0f);
        depth *= depth;

        // В пещерах цвет тумана темнеет, чтобы дальше было видно, а не голубая пелена
        depth *= 0.10f + 0.90f * ERnsTDay.skyExposure;

        float nr = ERnsTConfig.red(c.nightHorizon),   dr = ERnsTConfig.red(c.dayHorizon) * rain;
        float ng = ERnsTConfig.green(c.nightHorizon), dg = ERnsTConfig.green(c.dayHorizon) * rain;
        float nb = ERnsTConfig.blue(c.nightHorizon),  db = ERnsTConfig.blue(c.dayHorizon) * rain;

        fogRed   = (nr + (dr - nr) * d) * depth;
        fogGreen = (ng + (dg - ng) * d) * depth;
        fogBlue  = (nb + (db - nb) * d) * depth;

        // Заливка кадра того же цвета, чтобы под горизонтом не было бледной полосы
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0f);
    }
}
