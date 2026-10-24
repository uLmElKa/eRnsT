package net.nazu.ernst.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.shaders.Uniform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ERnsTClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ernst");

    public static KeyMapping toggleKey;
    public static KeyMapping configKey;
    public static boolean modEnabled = true;
    public static int shadowDistanceChunks = 8;
    private static int notificationTicks = 0;

    // Переносит значения из конфига в рабочие переменные мода
    public static void applyConfig(boolean rebuild) {
        ERnsTConfig c = ERnsTConfig.get();
        modEnabled = c.enabled;
        shadowDistanceChunks = c.shadowDistanceChunks;
        if (rebuild) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
            }
        }
    }

    // Передаёт шейдеру блоков силу эффекта (0 = выключено)
    private static void setFlag(ShaderInstance shader) {
        if (shader == null) return;
        Uniform u = shader.getUniform("ErnstEnabled");
        if (u != null) u.set(modEnabled ? ERnsTConfig.get().terrainEffect : 0.0f);
        Uniform tint = shader.getUniform("ErnstShadeTint");
        if (tint != null) tint.set(ERnsTConfig.get().shadeTint);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[eRnsT] Инициализация графического движка...");

        ERnsTConfig.load();
        applyConfig(false);

        ERnsTPostEffect.init();
        ERnsTShadow.init();

        // Клавиша включения и выключения (O)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ernst.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.ernst.main"
        ));

        // Клавиша открытия настроек (K)
        configKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ernst.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.ernst.main"
        ));

        WorldRenderEvents.START.register(ctx -> {
            setFlag(GameRenderer.getRendertypeSolidShader());
            setFlag(GameRenderer.getRendertypeCutoutMippedShader());
            setFlag(GameRenderer.getRendertypeCutoutShader());
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                modEnabled = !modEnabled;
                ERnsTConfig.get().enabled = modEnabled;
                ERnsTConfig.save();

                if (client.levelRenderer != null) {
                    client.levelRenderer.allChanged();
                }

                if (ERnsTConfig.get().showNotification) {
                    notificationTicks = 80;
                    if (client.player != null) {
                        String status = modEnabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН";
                        client.player.sendSystemMessage(Component.literal("§b[eRnsT] §fГрафика: " + status));
                    }
                }
            }

            while (configKey.consumeClick()) {
                client.setScreen(ERnsTConfigScreen.create(client.screen));
            }

            if (notificationTicks > 0) {
                notificationTicks--;
            }
        });

        // Оверлей уведомлений
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            if (notificationTicks <= 0) return;

            Minecraft client = Minecraft.getInstance();
            if (client.options.hideGui) return;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int boxWidth = 160;
            int boxHeight = 28;
            int x = screenWidth - boxWidth - 12;
            int y = 12;

            guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xB0121212);

            int accentColor = modEnabled ? 0xFF00FF66 : 0xFFFF3333;
            guiGraphics.fill(x, y, x + 4, y + boxHeight, accentColor);

            guiGraphics.drawString(client.font, "eRnsT Renderer", x + 12, y + 5, 0xFFFFFF, true);

            String statusText = modEnabled ? "Шейдеры (радиус: " + shadowDistanceChunks + " ч.)" : "Статус: Выключен";
            int statusColor = modEnabled ? 0x55FF55 : 0xFF5555;
            guiGraphics.drawString(client.font, statusText, x + 12, y + 15, statusColor, true);
        });
    }
}
