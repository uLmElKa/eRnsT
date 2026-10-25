package net.nazu.ernst.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.nazu.ernst.client.mixin.LevelRendererInvoker;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Динамические тени от блоков: карта теней рисуется с точки зрения солнца,
// затем шейдеры рельефа сверяют с ней глубину каждого пикселя.
public final class ERnsTShadow {
    private ERnsTShadow() {}

    private static int res = 1024;

    // Пока true, рельеф рисуется шейдером теней вместо обычных
    public static boolean active = false;
    public static ShaderInstance shadowShader;

    private static TextureTarget target;
    private static boolean failed = false;

    // Запас по краям экрана: тени считаются и для того, что чуть за кадром, поэтому они не появляются рывком при повороте
    private static final double VIEW_MARGIN = 0.12;
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
        float forcedSky = 0.0f;      // 1 в Аду и Крае, где нет света неба
        float maxRel = 100000.0f;    // выше этой отметки (от камеры) тень не отбрасывается
        boolean on = false;

        if (level != null) {
            float theta = level.getTimeOfDay(pt) * ((float) Math.PI * 2.0f);
            sx = -(float) Math.sin(theta);
            sy = (float) Math.cos(theta);
            float h = Mth.clamp((sy + 0.02f) / 0.14f, 0.0f, 1.0f);
            h = h * h * (3.0f - 2.0f * h);
            sunLight = h * (1.0f - level.getRainLevel(pt));

            // Солнце в майнкрафте идёт ровно с востока на запад, и северные и южные грани
            // (и растения) никогда не получали бы тень. Наклоняем свет теней на юг.
            if (level.dimension() == Level.OVERWORLD) {
                float tilt = 0.25f;
                float tl = (float) Math.sqrt(sx * sx + sy * sy + tilt * tilt);
                sx /= tl;
                sy /= tl;
                sz = tilt / tl;
            }
            boolean overworld = level.dimension() == Level.OVERWORLD;
            boolean nether = level.dimension() == Level.NETHER;
            boolean end = level.dimension() == Level.END;
            boolean allowed = overworld ? sunLight > 0.02f : (nether ? c.shadowsNether : (end && c.shadowsEnd));

            if (nether || end) {
                // Солнца нет, поэтому берём фиксированный свет сверху под небольшим углом
                float ex = nether ? 0.25f : 0.45f;
                float ey = nether ? 1.00f : 0.80f;
                float ez = nether ? 0.15f : 0.30f;
                float len = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                sx = ex / len;
                sy = ey / len;
                sz = ez / len;
                sunLight = 1.0f;
                forcedSky = 1.0f;
            }
            if (nether) {
                // Потолок Ада тени не отбрасывает: берём только постройки не выше заданной высоты над игроком
                double camY = ctx.camera().getPosition().y;
                maxRel = (float) Math.min(c.netherShadowHeight, 100.0 - camY);
            }

            on = !failed && ERnsTClient.modEnabled && c.shadowsEnabled && shadowShader != null && allowed;
        }

        if (on) {
            try {
                renderShadowMap(ctx, mc, c, sx, sy, sz, maxRel);
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

        apply(GameRenderer.getRendertypeSolidShader(), strength, sunLight, sx, sy, sz, texId, c.shadowDebug, forcedSky);
        apply(GameRenderer.getRendertypeCutoutMippedShader(), strength, sunLight, sx, sy, sz, texId, c.shadowDebug, forcedSky);
        apply(GameRenderer.getRendertypeCutoutShader(), strength, sunLight, sx, sy, sz, texId, c.shadowDebug, forcedSky);
    }

    private static void renderShadowMap(WorldRenderContext ctx, Minecraft mc, ERnsTConfig c,
                                        float sx, float sy, float sz, float maxRel) {
        Vec3 cp = ctx.camera().getPosition();

        // Вид из солнца: камера смотрит на игрока с расстояния 200 блоков
        float upX = 0.0f, upY = 1.0f, upZ = 0.0f;
        if (Math.abs(sy) > 0.98f) { upX = 1.0f; upY = 0.0f; }
        view.identity().lookAt(sx * 200.0f, sy * 200.0f, sz * 200.0f, 0.0f, 0.0f, 0.0f, upX, upY, upZ);

        // Привязка окна карты к сетке текселей, чтобы тени не дрожали при движении
        double r = c.shadowRange;
        double texel = (2.0 * r) / res;
        double lx = view.m00() * cp.x + view.m10() * cp.y + view.m20() * cp.z;
        double ly = view.m01() * cp.x + view.m11() * cp.y + view.m21() * cp.z;
        float offX = (float) (lx - Math.floor(lx / texel) * texel);
        float offY = (float) (ly - Math.floor(ly / texel) * texel);

        proj.identity().ortho((float) (-r - offX), (float) (r - offX),
                (float) (-r - offY), (float) (r - offY), 1.0f, 400.0f);
        shadowMat.set(proj).mul(view);

        // Разрешение карты: 1024 быстрее, 2048 чётче
        int wanted = c.shadowHighRes ? 2048 : 1024;
        if (target == null || res != wanted) {
            if (target != null) target.destroyBuffers();
            res = wanted;
            target = new TextureTarget(res, res, true, Minecraft.ON_OSX);
        }
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        LevelRendererInvoker inv = (LevelRendererInvoker) (Object) ctx.worldRenderer();

        // Ванильный список видимых участков зависит от поворота камеры, поэтому тени пропадали.
        // На время карты теней подменяем его списком всех участков, которые попадают в объём теней.
        ObjectArrayList<SectionRenderDispatcher.RenderSection> list = inv.ernst$getVisibleSections();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> backup = new ObjectArrayList<>(list);
        list.clear();
        Camera cam = ctx.camera();
        Vector3f lf = new Vector3f(cam.getLookVector());
        Vector3f lu = new Vector3f(cam.getUpVector());
        Vector3f lr = new Vector3f();
        lf.cross(lu, lr);
        float tanHalf = 1.0f / ctx.projectionMatrix().m11();
        float aspect = (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();
        collectSections(inv.ernst$getViewArea(), cp, r, offX, offY, list,
                sx, sy, sz, lf, lr, lu, tanHalf, aspect);

        Uniform mu = shadowShader.getUniform("ErnstShadowMaxY");
        if (mu != null) mu.set(maxRel);

        active = true;
        try {
            inv.ernst$renderSectionLayer(RenderType.solid(), cp.x, cp.y, cp.z, view, proj);
            inv.ernst$renderSectionLayer(RenderType.cutoutMipped(), cp.x, cp.y, cp.z, view, proj);
            inv.ernst$renderSectionLayer(RenderType.cutout(), cp.x, cp.y, cp.z, view, proj);
        } finally {
            active = false;
            list.clear();
            list.addAll(backup);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    // Выбирает участки мира, которые пересекают объём карты теней
    private static void collectSections(ViewArea area, Vec3 cp, double r, float offX, float offY,
                                        ObjectArrayList<SectionRenderDispatcher.RenderSection> out,
                                        float sx, float sy, float sz,
                                        Vector3f f, Vector3f rt, Vector3f up, float tanHalf, float aspect) {
        if (area == null || area.sections == null) return;
        double lim = r + 14.0;   // 14 блоков это половина диагонали участка
        for (SectionRenderDispatcher.RenderSection s : area.sections) {
            if (s == null) continue;
            BlockPos o = s.getOrigin();
            double px = o.getX() + 8.0 - cp.x;
            double py = o.getY() + 8.0 - cp.y;
            double pz = o.getZ() + 8.0 - cp.z;

            double lx = view.m00() * px + view.m10() * py + view.m20() * pz;
            double ly = view.m01() * px + view.m11() * py + view.m21() * pz;
            double lz = view.m02() * px + view.m12() * py + view.m22() * pz + view.m32();

            if (Math.abs(lx + offX) > lim) continue;
            if (Math.abs(ly + offY) > lim) continue;
            double depth = -lz;
            if (depth < 1.0 - 14.0 || depth > 400.0 + 14.0) continue;
            // Участок, тень от которого не попадёт в кадр (с запасом), в карту не берём
            if (!mayShadowView(px, py, pz, sx, sy, sz, f, rt, up, tanHalf, aspect)) continue;
            out.add(s);
        }
    }

    // Может ли участок отбросить тень туда, что видит камера (с запасом по краям)
    private static boolean mayShadowView(double px, double py, double pz, float sx, float sy, float sz,
                                         Vector3f f, Vector3f rt, Vector3f up, float tanHalf, float aspect) {
        double m = 1.0 + VIEW_MARGIN;
        double slack = 26.0;   // размер участка плюс мягкость края тени
        for (int k = 0; k < 4; k++) {
            double t = k * 24.0;   // тень падает от солнца: идём вдоль неё на 0, 24, 48 и 72 блока
            double x = px - sx * t, y = py - sy * t, z = pz - sz * t;
            double depth = x * f.x + y * f.y + z * f.z;
            if (depth < -slack) continue;
            double dz = Math.max(depth, 0.0);
            double rx = Math.abs(x * rt.x + y * rt.y + z * rt.z);
            double ry = Math.abs(x * up.x + y * up.y + z * up.z);
            if (rx <= m * aspect * tanHalf * dz + slack && ry <= m * tanHalf * dz + slack) return true;
        }
        return false;
    }

    private static void apply(ShaderInstance s, float strength, float sunLight,
                              float sx, float sy, float sz, int texId, boolean debug, float forcedSky) {
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
        u = s.getUniform("ErnstShadowSky");
        if (u != null) u.set(forcedSky);
        s.setSampler("ShadowMap", texId);
    }
}
