package net.nazu.ernst.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.nazu.ernst.client.ERnsTShadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Пока рисуется карта теней, все три слоя рельефа используют шейдер теней
@Mixin(GameRenderer.class)
public class GameRendererShadowMixin {

    @Inject(method = "getRendertypeSolidShader", at = @At("HEAD"), cancellable = true)
    private static void ernst$solid(CallbackInfoReturnable<ShaderInstance> cir) {
        if (ERnsTShadow.active && ERnsTShadow.shadowShader != null) cir.setReturnValue(ERnsTShadow.shadowShader);
    }

    @Inject(method = "getRendertypeCutoutMippedShader", at = @At("HEAD"), cancellable = true)
    private static void ernst$cutoutMipped(CallbackInfoReturnable<ShaderInstance> cir) {
        if (ERnsTShadow.active && ERnsTShadow.shadowShader != null) cir.setReturnValue(ERnsTShadow.shadowShader);
    }

    @Inject(method = "getRendertypeCutoutShader", at = @At("HEAD"), cancellable = true)
    private static void ernst$cutout(CallbackInfoReturnable<ShaderInstance> cir) {
        if (ERnsTShadow.active && ERnsTShadow.shadowShader != null) cir.setReturnValue(ERnsTShadow.shadowShader);
    }
}
