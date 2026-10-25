package net.nazu.ernst.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.ViewArea;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {
    @Invoker("renderSectionLayer")
    void ernst$renderSectionLayer(RenderType renderType, double x, double y, double z,
                                  Matrix4f frustumMatrix, Matrix4f projectionMatrix);

    // Все участки мира вокруг игрока
    @Accessor("viewArea")
    ViewArea ernst$getViewArea();

    // Список участков, которые видны камере (его подменяем на время карты теней)
    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> ernst$getVisibleSections();
}