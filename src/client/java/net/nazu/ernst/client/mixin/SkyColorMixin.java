package net.nazu.ernst.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.nazu.ernst.client.ERnsTClient;
import net.nazu.ernst.client.ERnsTConfig;
import net.nazu.ernst.client.ERnsTDay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class SkyColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void ernst$skyColor(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!ERnsTClient.modEnabled) return;
        ERnsTConfig c = ERnsTConfig.get();
        if (!c.skyEnabled) return;
        ClientLevel level = (ClientLevel) (Object) this;
        if (level.dimension() != Level.OVERWORLD) return;

        double d = ERnsTDay.dayness(level, partialTick);
        // В дождь дневное небо темнее
        double rain = 1.0 - c.rainDarken * level.getRainLevel(partialTick);

        double r = ERnsTConfig.red(c.nightZenith)   + (ERnsTConfig.red(c.dayZenith)   * rain - ERnsTConfig.red(c.nightZenith))   * d;
        double g = ERnsTConfig.green(c.nightZenith) + (ERnsTConfig.green(c.dayZenith) * rain - ERnsTConfig.green(c.nightZenith)) * d;
        double b = ERnsTConfig.blue(c.nightZenith)  + (ERnsTConfig.blue(c.dayZenith)  * rain - ERnsTConfig.blue(c.nightZenith))  * d;

        cir.setReturnValue(new Vec3(r, g, b));
    }
}
