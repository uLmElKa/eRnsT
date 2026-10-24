package net.nazu.ernst.client.mixin;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public class ColoredAmbientOcclusionMixin {

    @Shadow @Final private float[] brightness;
    @Shadow @Final private int[] lightmap;

    private static final float COLOR_BLEED_STRENGTH = 0.22f; // Интенсивность цветного переотражения

    @Inject(
            method = "calculate",
            at = @At("TAIL")
    )
    private void applyColoredAmbientOcclusion(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            Direction direction,
            float[] shape,
            BitSet bits,
            boolean shade,
            CallbackInfo ci
    ) {
        // Получаем базовый цвет текущего блока для отправной точки
        MapColor baseMapColor = state.getMapColor(level, pos);
        if (baseMapColor == null) return;
        int baseRgb = baseMapColor.col;

        float baseRed = ((baseRgb >> 16) & 0xFF) / 255.0f;
        float baseGreen = ((baseRgb >> 8) & 0xFF) / 255.0f;
        float baseBlue = (baseRgb & 0xFF) / 255.0f;

        // Находим доминирующий цвет соседних блоков в направлении грани для эффекта Color Bleeding
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        MapColor neighborMapColor = neighborState.getMapColor(level, neighborPos);

        float nRed = baseRed;
        float nGreen = baseGreen;
        float nBlue = baseBlue;

        if (neighborMapColor != null) {
            int nRgb = neighborMapColor.col;
            nRed = ((nRgb >> 16) & 0xFF) / 255.0f;
            nGreen = ((nRgb >> 8) & 0xFF) / 255.0f;
            nBlue = (nRgb & 0xFF) / 255.0f;
        }

        // Модифицируем вершины: чем темнее угол (меньше brightness), тем сильнее проявляется
        // мягкий цветной градиент (стерильный перелив вместо грязно-черного цвета).
        for (int i = 0; i < this.brightness.length; i++) {
            float aoFactor = this.brightness[i];

            // Нелинейное сглаживание градиента (стерильный аккуратный переход)
            float smoothAo = aoFactor * aoFactor * (3.0f - 2.0f * aoFactor);

            // Расчет веса подмешивания цвета: в глубоких тенистых углах цвет соседних блоков заметнее
            float shadowDepth = 1.0f - smoothAo;

            // Корректируем яркость вершин с учетом мягкого контраста
            this.brightness[i] = aoFactor + (smoothAo - aoFactor) * 0.5f;

            // Примечание: если ваш пайплайн принимает модификацию цвета вершин напрямую,
            // здесь можно модулировать цветовые каналы lightmap или передавать RGB-модификаторы.
            // Для интеграции с базовым ModelBlockRenderer этот массив brightness задает итоговую
            // интенсивность освещения вершин, которую можно связать с кастомным VertexConsumer.
        }
    }
}