package net.nazu.ernst.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {
    @Invoker("renderSectionLayer")
    void ernst$renderSectionLayer(RenderType renderType, double x, double y, double z,
                                  Matrix4f frustumMatrix, Matrix4f projectionMatrix);
}
