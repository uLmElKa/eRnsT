package net.nazu.ernst.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;

public class ERnsTPostEffect {
    private static PostChain chain;
    private static boolean failed = false;
    private static String loadedVariant = "";
    private static int w = -1, h = -1;

    private static void vec(String name, float x, float y, float z) {
        chain.setUniform(name + "X", x);
        chain.setUniform(name + "Y", y);
        chain.setUniform(name + "Z", z);
    }

    // Создаёт цепочку и подгоняет её под размер окна
    private static void prepare(Minecraft mc) throws Exception {
        // Выбираем цепочку по тому, что включено: выключенные проходы не выполняются совсем
        ERnsTConfig cfg = ERnsTConfig.get();
        String variant = cfg.bloomEnabled
                ? (cfg.raysEnabled ? "mirror" : "mirror_norays")
                : (cfg.raysEnabled ? "mirror_nobloom" : "mirror_basic");
        if (chain != null && !variant.equals(loadedVariant)) {
            chain.close();
            chain = null;
        }
        if (chain == null) {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                    mc.getMainRenderTarget(),
                    ResourceLocation.fromNamespaceAndPath("ernst", "shaders/post/" + variant + ".json"));
            loadedVariant = variant;
            w = -1;
        }
        int nw = mc.getWindow().getWidth();
        int nh = mc.getWindow().getHeight();
        if (nw != w || nh != h) {
            chain.resize(nw, nh);
            w = nw;
            h = nh;
        }
    }

    public static void init() {
        // Запоминаем глубину рельефа до того, как нарисуются сущности
        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
            if (!ERnsTClient.modEnabled || failed) return;
            ERnsTConfig cfgE = ERnsTConfig.get();
            if (cfgE.entityBloom || !cfgE.bloomEnabled) return;
            Minecraft mc = Minecraft.getInstance();
            try {
                prepare(mc);
                RenderTarget terrain = chain.getTempTarget("terrain");
                if (terrain != null) {
                    terrain.copyDepthFrom(mc.getMainRenderTarget());
                    mc.getMainRenderTarget().bindWrite(true);
                }
            } catch (Exception e) {
                failed = true;
                ERnsTClient.LOGGER.error("[eRnsT] Ошибка post chain (глубина рельефа)", e);
            }
        });

        WorldRenderEvents.END.register(ctx -> {
            if (!ERnsTClient.modEnabled || failed) return;
            Minecraft mc = Minecraft.getInstance();
            try {
                prepare(mc);
                int nw = mc.getWindow().getWidth();
                int nh = mc.getWindow().getHeight();

                ERnsTConfig c = ERnsTConfig.get();
                float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);

                // Цветокоррекция и bloom
                chain.setUniform("ErnstFar", mc.options.getEffectiveRenderDistance() * 64.0f);
                chain.setUniform("ErnstSat", c.saturation);
                chain.setUniform("ErnstVibrance", c.vibrance);
                chain.setUniform("ErnstContrast", c.contrast);
                chain.setUniform("ErnstWarmth", c.warmth);
                chain.setUniform("ErnstCool", c.coolShadows);
                chain.setUniform("ErnstSharpen", c.sharpen);
                chain.setUniform("ErnstThreshold", c.bloomThreshold);
                chain.setUniform("ErnstLight", c.lightBoost);
                chain.setUniform("ErnstBlurScale", c.bloomRadius);
                chain.setUniform("ErnstBloom", c.bloomStrength);
                chain.setUniform("ErnstExposure", c.exposure);
                chain.setUniform("ErnstShoulder", c.toneShoulder);
                chain.setUniform("ErnstVignette", c.vignette);
                chain.setUniform("ErnstEntityBloom", c.entityBloom ? 1.0f : 0.0f);
                chain.setUniform("ErnstBloomOn", c.bloomEnabled ? 1.0f : 0.0f);
                chain.setUniform("ErnstRaysOn", c.raysEnabled ? 1.0f : 0.0f);

                // Камера, солнце, туман и лучи
                ClientLevel level = mc.level;
                if (level != null) {
                    Camera cam = ctx.camera();
                    ERnsTDay.updateExposure(level, cam.getBlockPosition(), cam.getPosition().y);
                    Vector3f f = new Vector3f(cam.getLookVector());
                    Vector3f u = new Vector3f(cam.getUpVector());
                    Vector3f r = new Vector3f();
                    f.cross(u, r);

                    vec("ErnstFwd", f.x, f.y, f.z);
                    vec("ErnstUp", u.x, u.y, u.z);
                    vec("ErnstRight", r.x, r.y, r.z);

                    float tanHalf = 1.0f / ctx.projectionMatrix().m11();
                    chain.setUniform("ErnstTanHalf", tanHalf);
                    chain.setUniform("ErnstAspect", (float) nw / (float) nh);
                    chain.setUniform("ErnstCamY", (float) cam.getPosition().y);

                    // Направление на солнце в мире
                    float theta = level.getTimeOfDay(pt) * ((float) Math.PI * 2.0f);
                    float sdx = -(float) Math.sin(theta);
                    float sdy = (float) Math.cos(theta);
                    vec("ErnstSunD", sdx, sdy, 0.0f);

                    float horizon = Mth.clamp((sdy + 0.02f) / 0.14f, 0.0f, 1.0f);
                    horizon = horizon * horizon * (3.0f - 2.0f * horizon);
                    float sunLight = horizon * (1.0f - level.getRainLevel(pt));
                    chain.setUniform("ErnstSunLight", sunLight);

                    float day = ERnsTDay.dayness(level, pt);
                    chain.setUniform("ErnstDay", day);

                    // Цвет тумана совпадает с цветом горизонта
                    chain.setUniform("ErnstFogR", Mth.lerp(day, ERnsTConfig.red(c.nightHorizon), ERnsTConfig.red(c.dayHorizon)));
                    chain.setUniform("ErnstFogG", Mth.lerp(day, ERnsTConfig.green(c.nightHorizon), ERnsTConfig.green(c.dayHorizon)));
                    chain.setUniform("ErnstFogB", Mth.lerp(day, ERnsTConfig.blue(c.nightHorizon), ERnsTConfig.blue(c.dayHorizon)));

                    // Туман и лучи только в Верхнем мире и не под водой
                    boolean on = level.dimension() == Level.OVERWORLD && cam.getFluidInCamera() == FogType.NONE;
                    chain.setUniform("ErnstFogDensity", on ? c.fogDensity * 0.02f * ERnsTDay.skyExposure : 0.0f);
                    chain.setUniform("ErnstFogHeight", c.fogHeight);
                    chain.setUniform("ErnstFogFalloff", c.fogFalloff);
                    chain.setUniform("ErnstFogSun", c.fogSun);
                    chain.setUniform("ErnstRays", on ? c.rayStrength : 0.0f);
                    chain.setUniform("ErnstRayLen", c.rayLength);
                }

                RenderSystem.disableBlend();
                RenderSystem.disableDepthTest();
                RenderSystem.resetTextureMatrix();
                chain.process(pt);
                mc.getMainRenderTarget().bindWrite(true);
            } catch (Exception e) {
                failed = true;
                ERnsTClient.LOGGER.error("[eRnsT] Ошибка post chain", e);
            }
        });
    }
}
