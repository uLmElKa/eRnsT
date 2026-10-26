package net.nazu.ernst.client.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// Доступ к проходам цепочки, чтобы передавать в них матрицы и текстуру теней
@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> ernst$getPasses();
}
