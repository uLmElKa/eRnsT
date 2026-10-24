package net.nazu.ernst.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.nazu.ernst.client.ERnsTClient;
import net.nazu.ernst.client.ERnsTConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public class LightTextureMixin {

	@Shadow @Final private DynamicTexture lightTexture;

	// Внедряемся в конец метода update, когда игра уже рассчитала базовые цвета пикселей
	@Inject(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
	private void onLightmapUpdate(float partialTicks, CallbackInfo ci) {
		if (!ERnsTClient.modEnabled) return;
		ERnsTConfig cfg = ERnsTConfig.get();
		if (!cfg.lightmapEnabled) return;
		NativeImage image = this.lightTexture.getPixels();
		if (image == null) return;

		// Проходимся по всем 16x16 пикселям световой карты (x = block light, y = sky light)
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {

				int color = image.getPixelRGBA(x, y);

				// Извлекаем каналы R, G, B, A (в формате ABGR)
				int a = (color >> 24) & 0xFF;
				int b = (color >> 16) & 0xFF;
				int g = (color >> 8) & 0xFF;
				int r = color & 0xFF;

				float rf = r / 255f, gf = g / 255f, bf = b / 255f;
				float lum = (rf + gf + bf) / 3f;
				float shadow = 1f - lum;
				float blockWeight = x / 15f; // x = свет от блоков, y = небо

				// 1. Холодные тени: слабее там, где доминирует свет блоков
				float cool = shadow * (1f - blockWeight * 0.7f) * cfg.lightCool;
				rf *= 1f - 0.10f * cool;
				gf *= 1f - 0.03f * cool;
				bf += 0.14f * cool;

				// 2. Тёплый свет блоков в светлых зонах
				float warm = blockWeight * lum * cfg.lightWarmth;
				rf += 0.03f * warm;
				bf -= 0.02f * warm;

				// 3. Мягкий контраст и насыщенность
				rf = curve(rf, cfg.lightContrast); gf = curve(gf, cfg.lightContrast); bf = curve(bf, cfg.lightContrast);
				float l2 = 0.299f * rf + 0.587f * gf + 0.114f * bf;
				rf = l2 + (rf - l2) * cfg.lightSaturation;
				gf = l2 + (gf - l2) * cfg.lightSaturation;
				bf = l2 + (bf - l2) * cfg.lightSaturation;

				r = clamp255(rf); g = clamp255(gf); b = clamp255(bf);
				image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
			}
		}
	}

	private static float curve(float v, float strength) {
		v = Math.max(0f, Math.min(1f, v));
		float smooth = v * v * (3f - 2f * v);
		return v + (smooth - v) * strength;
	}

	private static int clamp255(float v) {
		return Math.max(0, Math.min(255, (int) (v * 255f + 0.5f)));
	}
}
