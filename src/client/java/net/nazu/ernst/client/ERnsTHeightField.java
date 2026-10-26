package net.nazu.ernst.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// Упрощённый рельеф для карты теней: у каждого столбца блоков берётся высота самого верхнего блока.
// Работает независимо от того, чем рисуется сам мир (Sodium или ваниль).
public final class ERnsTHeightField {
    private ERnsTHeightField() {}

    public static ShaderInstance shader;

    private static VertexBuffer vbo;
    private static ClientLevel builtLevel;
    private static int ox, oz, builtR = -1;
    private static long lastTick = Long.MIN_VALUE / 2;

    // Пересобирает сетку, если игрок ушёл далеко или прошло много времени
    public static void update(ClientLevel level, Vec3 cp, int radius) {
        long tick = level.getGameTime();
        int px = Mth.floor(cp.x), pz = Mth.floor(cp.z);

        boolean first = vbo == null || builtLevel != level || radius != builtR;
        boolean moved = Math.abs(px - (ox + builtR)) > 6 || Math.abs(pz - (oz + builtR)) > 6;
        boolean stale = tick - lastTick >= 100;

        if (!first && !stale && !(moved && tick - lastTick >= 10)) return;

        build(level, px, pz, radius);
        lastTick = tick;
        builtLevel = level;
    }

    private static void build(ClientLevel level, int cx, int cz, int r) {
        int size = 2 * r + 1;
        int w = size + 2;
        int[] h = new int[w * w];
        ox = cx - r;
        oz = cz - r;
        builtR = r;

        for (int dz = -1; dz <= size; dz++) {
            for (int dx = -1; dx <= size; dx++) {
                h[(dz + 1) * w + (dx + 1)] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ox + dx, oz + dz);
            }
        }

        int min = level.getMinBuildHeight();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int top = h[(dz + 1) * w + (dx + 1)];
                if (top <= min) continue;

                float x0 = dx, x1 = dx + 1, z0 = dz, z1 = dz + 1, y = top;

                // верхняя грань столбца
                bb.addVertex(x0, y, z0);
                bb.addVertex(x0, y, z1);
                bb.addVertex(x1, y, z1);
                bb.addVertex(x1, y, z0);

                // боковые грани там, где сосед ниже (иначе между столбцами проходил бы свет)
                int west = h[(dz + 1) * w + dx];
                int east = h[(dz + 1) * w + dx + 2];
                int north = h[dz * w + dx + 1];
                int south = h[(dz + 2) * w + dx + 1];
                if (west < top) sideX(bb, x0, z0, z1, Math.max(west, top - 48), y);
                if (east < top) sideX(bb, x1, z0, z1, Math.max(east, top - 48), y);
                if (north < top) sideZ(bb, z0, x0, x1, Math.max(north, top - 48), y);
                if (south < top) sideZ(bb, z1, x0, x1, Math.max(south, top - 48), y);
            }
        }

        MeshData mesh = bb.build();
        if (vbo != null) {
            vbo.close();
            vbo = null;
        }
        if (mesh == null) return;

        vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(mesh);
        VertexBuffer.unbind();
    }

    private static void sideX(BufferBuilder bb, float x, float z0, float z1, float yb, float yt) {
        bb.addVertex(x, yb, z0);
        bb.addVertex(x, yt, z0);
        bb.addVertex(x, yt, z1);
        bb.addVertex(x, yb, z1);
    }

    private static void sideZ(BufferBuilder bb, float z, float x0, float x1, float yb, float yt) {
        bb.addVertex(x0, yb, z);
        bb.addVertex(x0, yt, z);
        bb.addVertex(x1, yt, z);
        bb.addVertex(x1, yb, z);
    }

    // Рисует сетку в текущую цель (карту теней) с точки зрения солнца
    public static void draw(Matrix4f view, Matrix4f proj, Vec3 cp) {
        if (vbo == null || shader == null) return;
        Matrix4f mv = new Matrix4f(view).translate((float) (ox - cp.x), (float) (-cp.y), (float) (oz - cp.z));
        RenderSystem.disableCull();
        vbo.bind();
        vbo.drawWithShader(mv, proj, shader);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }
}
