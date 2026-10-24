package net.nazu.ernst.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;

public final class ERnsTDay {
    private ERnsTDay() {}

    // 1.0 = открытое место, около 0 = глубоко под землёй (плавно меняется)
    public static float skyExposure = 1.0f;

    // 1.0 = день, 0.0 = ночь, между ними плавный переход на закате и рассвете
    public static float dayness(ClientLevel level, float partialTick) {
        float angle = level.getTimeOfDay(partialTick) * ((float) Math.PI * 2.0f);
        return Mth.clamp(Mth.cos(angle) * 2.0f + 0.5f, 0.0f, 1.0f);
    }

    // Обновляется каждый кадр. Выше 75 блоков всегда считается «снаружи»,
    // ниже 60 зависит от того, доходит ли сюда свет неба.
    public static void updateExposure(ClientLevel level, BlockPos pos, double y) {
        float sky = level.getBrightness(LightLayer.SKY, pos) / 15.0f;
        float surface = Mth.clamp((float) (y - 60.0) / 15.0f, 0.0f, 1.0f);
        float target = Math.max(sky, surface);
        skyExposure += (target - skyExposure) * 0.05f;
    }
}
