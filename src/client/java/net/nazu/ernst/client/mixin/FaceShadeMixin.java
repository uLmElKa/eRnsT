package net.nazu.ernst.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.nazu.ernst.client.ERnsTClient;
import net.nazu.ernst.client.ERnsTConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Заменяет ванильную яркость граней (верх 1.0, север и юг 0.8, запад и восток 0.6, низ 0.5)
@Mixin(ClientLevel.class)
public class FaceShadeMixin {

    @Inject(method = "getShade", at = @At("RETURN"), cancellable = true)
    private void ernst$faceShade(Direction direction, boolean shade, CallbackInfoReturnable<Float> cir) {
        if (!ERnsTClient.modEnabled || !shade) return;
        ClientLevel level = (ClientLevel) (Object) this;
        if (level.dimension() != Level.OVERWORLD) return;

        ERnsTConfig c = ERnsTConfig.get();
        float v = switch (direction) {
            case UP -> c.shadeUp;
            case DOWN -> c.shadeDown;
            case NORTH, SOUTH -> c.shadeNS;
            case WEST, EAST -> c.shadeWE;
        };
        cir.setReturnValue(v);
    }
}
