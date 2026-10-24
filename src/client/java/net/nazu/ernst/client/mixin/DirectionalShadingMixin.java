package net.nazu.ernst.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.nazu.ernst.client.ERnsTClient;
import net.nazu.ernst.client.ERnsTConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public class DirectionalShadingMixin {

    @Shadow
    @Final
    private float[] brightness;

    @Inject(method = "calculate", at = @At("TAIL"))
    private void modifyQuadBrightness(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                      Direction direction, float[] shape, BitSet bits,
                                      boolean shade, CallbackInfo ci) {

        if (!ERnsTClient.modEnabled) {
            return;
        }

        // Проверка радиуса чанков из конфига
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            BlockPos playerPos = client.player.blockPosition();

            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int pChunkX = playerPos.getX() >> 4;
            int pChunkZ = playerPos.getZ() >> 4;

            int distChunks = Math.max(Math.abs(chunkX - pChunkX), Math.abs(chunkZ - pChunkZ));

            if (distChunks > ERnsTClient.shadowDistanceChunks) {
                return;
            }
        }

        ERnsTConfig cfg = ERnsTConfig.get();

        for (int i = 0; i < this.brightness.length; i++) {
            float v = Math.min(1.0f, this.brightness[i]);
            float smooth = v * v * (3.0f - 2.0f * v);
            this.brightness[i] = v + (smooth - v) * cfg.aoContrast;
        }
    }
}