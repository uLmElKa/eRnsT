package net.nazu.ernst.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.nazu.ernst.client.mixin.LevelRendererInvoker;
import org.joml.Matrix4f;

// Динамические тени от блоков: карта теней рисуется с точки зрения солнца,
// затем шейдеры рельефа сверяют с ней глубину каждого пикселя.
public final class ERnsTShadow {
    private ERnsTShadow() {}

    public static final int RES = 2048;

    // Пока true, рельеф рисуется шейдером теней вместо обычных
    public static boolean active = false;
    public static ShaderInstance shadowShader;

    private static TextureTarget target;
    private static boolean failed = false;
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f proj = new Matrix4f();
    private static final Matrix4f shadowMat = new Matrix4f();

    public static void init() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                ResourceLocation.fromNamespaceAndPath("ernst", "ernst_shadow"),
                DefaultVertexFormat.BLOCK,
                shader -> shadowShader = shader));

        // После того как определены видимые участки мира, но до отрисовки рельефа
        WorldRenderEvents.AFTER_SETUP.register(ERnsTShadow::onSetup);
    }

    private static void onSetup(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        ERnsTConfig c = ERnsTConfig.get();
        RenderTarget main = mc.getMainRenderTarget();

        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        float sx = 0.0f, sy = 1.0f, sz = 0.0f, sunLight = 0.0f;
        boolean on = false;

        if (level != null) {
            float theta = level.getTimeOfDay(pt) * ((float) Math.PI * 2.0f);
            sx = -(float) Math.sin(theta);
            sy = (float) Math.cos(theta);
            float h = Mth.clamp((sy + 0.02f) / 0.14f, 0.0f, 1.0f);
            h = h * h * (3.0f - 2.0f * h);
            sunLight = h * (1.0f - level.getRainLevel(pt));
            on = !failed && ERnsTClient.modEnabled && c.shadowsEnabled && shadowShader != null
                    && level.dimension() == Level.OVERWORLD && sunLight > 0.02f;
        }

        if (on) {
            try {
                renderShadowMap(ctx, mc, c, sx, sy, sz);
            } catch (Exception e) {
                failed = true;
                active = false;
                on = false;
                main.bindWrite(true);
                ERnsTClient.LOGGER.error("[eRnsT] Ошибка при отрисовке карты теней", e);
            }
        }

        int texId = (on && target != null) ? target.getDepthTextureId() : main.getColorTextureId();
        float strength = on ? c.shadowStrength : 0.0f;

        apply(GameRenderer.getRendertypeSolidShader(), strength, sunLight, sx, sy, sz, texId, c.shadowDebug);
        apply(GameRenderer.getRendertypeCutoutMippedShader(), strength, sunLight, sx, sy, sz, texId, c.shadowDebug);
        apply(GameRenderer.getRendertypeCutoutShader(), strength, sunLight, sx, sy, sz, texId, c.shadowDebug);
    }

    private static void renderShadowMap(WorldRenderContext ctx, Minecraft mc, ERnsTConfig c,
                                        float sx, float sy, float sz) {
        Vec3 cp = ctx.camera().getPosition();

        // Вид из солнца: камера смотрит на игрока с расстояния 200 блоков
        float upX = 0.0f, upY = 1.0f, upZ = 0.0f;
        if (Math.abs(sy) > 0.98f) { upX = 1.0f; upY = 0.0f; }
        view.identity().lookAt(sx * 200.0f, sy * 200.0f, sz * 200.0f, 0.0f, 0.0f, 0.0f, upX, upY, upZ);

        // Привязка окна карты к сетке текселей, чтобы тени не дрожали при движении
        double r = c.shadowRange;
        double texel = (2.0 * r) / RES;
        double lx = view.m00() * cp.x + view.m10() * cp.y + view.m20() * cp.z;
        double ly = view.m01() * cp.x + view.m11() * cp.y + view.m21() * cp.z;
        float offX = (float) (lx - Math.floor(lx / texel) * texel);
        float offY = (float) (ly - Math.floor(ly / texel) * texel);

        proj.identity().ortho((float) (-r - offX), (float) (r - offX),
                (float) (-r - offY), (float) (r - offY), 1.0f, 400.0f);
        shadowMat.set(proj).mul(view);

        if (target == null) {
            target = new TextureTarget(RES, RES, true, Minecraft.ON_OSX);
        }
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        active = true;
        try {
            LevelRendererInvoker inv = (LevelRendererInvoker) (Object) ctx.worldRenderer();
            inv.ernst$renderSectionLayer(RenderType.solid(), cp.x, cp.y, cp.z, view, proj);
            inv.ernst$renderSectionLayer(RenderType.cutoutMipped(), cp.x, cp.y, cp.z, view, proj);
            inv.ernst$renderSectionLayer(RenderType.cutout(), cp.x, cp.y, cp.z, view, proj);
        } finally {
            active = false;
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void apply(ShaderInstance s, float strength, float sunLight,
                              float sx, float sy, float sz, int texId, boolean debug) {
        if (s == null) return;
        Uniform u = s.getUniform("ShadowMat");
        if (u != null) u.set(shadowMat);
        u = s.getUniform("ErnstShadowStrength");
        if (u != null) u.set(strength);
        u = s.getUniform("ErnstSunLight");
        if (u != null) u.set(sunLight);
        u = s.getUniform("ErnstShadowDebug");
        if (u != null) u.set(debug ? 1.0f : 0.0f);
        u = s.getUniform("ErnstSunDir");
        if (u != null) u.set(sx, sy, sz);
        s.setSampler("ShadowMap", texId);
    }
}
