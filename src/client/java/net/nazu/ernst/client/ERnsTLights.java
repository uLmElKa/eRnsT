package net.nazu.ernst.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Ищет рядом источники света, склеивает те, что ближе 3 блоков друг к другу,
// не давая склейке вырасти больше 16 блоков, и выбирает несколько самых заметных
public final class ERnsTLights {
    private ERnsTLights() {}

    public static final int MAX = 4;
    private static final int RADIUS = 16;
    private static final int LAYERS_PER_TICK = 3;
    private static final long NONE = Long.MIN_VALUE;

    private static final double MERGE_DISTANCE = 3.5;   // расстояние между источниками, при котором они склеиваются
    private static final double MAX_EXTENT = 16.0;      // склейка не больше этого размера, иначе свет окажется в середине зала

    private static final float[][] PALETTE = {
            {1.0f, 0.68f, 0.32f},   // 0: факелы, лава, магма, светокамень
            {0.35f, 0.85f, 1.0f},   // 1: огонь душ
            {0.55f, 1.0f, 0.85f},   // 2: порталы Края
            {0.70f, 0.35f, 1.0f},   // 3: портал в Нижний мир
            {0.70f, 0.95f, 1.0f},   // 4: морской фонарь
            {0.60f, 1.0f, 0.60f},   // 5: зелёный лягушачий светильник
    };

    // Маленькая ячейка 2x2x2 блока: сюда сначала собираются найденные источники
    private static final class Cell {
        double sx, sy, sz;
        int n;
        int color;
    }

    private static final class Cluster {
        double sx, sy, sz, x, y, z;
        int n;
        int bestN;
        int color;
        long id;
    }

    private static List<Cluster> ready = new ArrayList<>();
    private static final Map<Long, Cell> building = new HashMap<>();
    private static BlockPos origin = BlockPos.ZERO;
    private static int layer = -1;

    // Какой источник сидит в каком слоте шейдера (чтобы свечение не прыгало между слотами)
    private static final long[] slotId = new long[MAX];
    private static final float[] slotSmooth = new float[MAX];
    static {
        Arrays.fill(slotId, NONE);
    }

    // Результат для шейдера: положение на экране, расстояние, яркость и цвет
    public static final float[] outU = new float[MAX], outV = new float[MAX], outD = new float[MAX];
    public static final float[] outI = new float[MAX], outR = new float[MAX], outG = new float[MAX], outB = new float[MAX];

    private static int colorFor(BlockState st) {
        String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        if (id.contains("soul")) return 1;
        if (id.contains("end_portal") || id.contains("end_gateway")) return 2;
        if (id.contains("nether_portal")) return 3;
        if (id.contains("sea_lantern") || id.contains("pearlescent")) return 4;
        if (id.contains("verdant")) return 5;
        return 0;
    }

    // ===== склейка =====
    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static long cellKey(double x, double y, double z, double size) {
        return BlockPos.asLong((int) Math.floor(x / size), (int) Math.floor(y / size), (int) Math.floor(z / size));
    }

    private static List<Cluster> merge(Map<Long, Cell> cells) {
        int n = cells.size();
        double[] px = new double[n], py = new double[n], pz = new double[n];
        Cell[] cs = new Cell[n];
        int idx = 0;
        for (Cell c : cells.values()) {
            cs[idx] = c;
            px[idx] = c.sx / c.n;
            py[idx] = c.sy / c.n;
            pz[idx] = c.sz / c.n;
            idx++;
        }

        // Сетка для быстрого поиска соседей
        double gs = 4.0;
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < n; i++) {
            grid.computeIfAbsent(cellKey(px[i], py[i], pz[i], gs), k -> new ArrayList<>()).add(i);
        }

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        double lim2 = MERGE_DISTANCE * MERGE_DISTANCE;

        for (int i = 0; i < n; i++) {
            int gx = (int) Math.floor(px[i] / gs), gy = (int) Math.floor(py[i] / gs), gz = (int) Math.floor(pz[i] / gs);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Integer> list = grid.get(BlockPos.asLong(gx + dx, gy + dy, gz + dz));
                        if (list == null) continue;
                        for (int j : list) {
                            if (j <= i) continue;
                            double ex = px[i] - px[j], ey = py[i] - py[j], ez = pz[i] - pz[j];
                            if (ex * ex + ey * ey + ez * ez <= lim2) {
                                int ri = find(parent, i), rj = find(parent, j);
                                if (ri != rj) parent[ri] = rj;
                            }
                        }
                    }
                }
            }
        }

        // Группы режем по крупной сетке, чтобы длинная цепочка ламп не слилась в один свет посреди зала
        Map<Long, Cluster> out = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long key = find(parent, i) * 1000003L + cellKey(px[i], py[i], pz[i], MAX_EXTENT);
            Cluster cl = out.get(key);
            if (cl == null) {
                cl = new Cluster();
                out.put(key, cl);
            }
            Cell c = cs[i];
            cl.sx += c.sx; cl.sy += c.sy; cl.sz += c.sz;
            cl.n += c.n;
            if (c.n > cl.bestN) {
                cl.bestN = c.n;
                cl.color = c.color;
            }
        }
        for (Cluster cl : out.values()) {
            cl.x = cl.sx / cl.n;
            cl.y = cl.sy / cl.n;
            cl.z = cl.sz / cl.n;
            cl.id = cellKey(cl.x, cl.y, cl.z, 2.0);   // стабильный номер по положению
        }
        return new ArrayList<>(out.values());
    }

    // Вызывается каждый тик: сканирует мир по несколько слоёв за раз, чтобы не было просадок
    public static void tick(Minecraft mc) {
        ERnsTConfig c = ERnsTConfig.get();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !ERnsTClient.modEnabled || !c.lightRays) {
            if (!ready.isEmpty()) ready = new ArrayList<>();
            building.clear();
            layer = -1;
            return;
        }

        if (layer < 0) {
            origin = mc.player.blockPosition();
            layer = 0;
            building.clear();
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < LAYERS_PER_TICK && layer <= 2 * RADIUS; n++, layer++) {
            int y = origin.getY() - RADIUS + layer;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    int bx = origin.getX() + x;
                    int bz = origin.getZ() + z;
                    pos.set(bx, y, bz);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) continue;
                    boolean magma = st.is(Blocks.MAGMA_BLOCK);
                    if (st.getLightEmission() < 10 && !magma) continue;

                    long key = BlockPos.asLong(bx >> 1, y >> 1, bz >> 1);
                    Cell cell = building.get(key);
                    if (cell == null) {
                        cell = new Cell();
                        cell.color = colorFor(st);
                        building.put(key, cell);
                    }
                    cell.sx += bx + 0.5;
                    cell.sy += y + 0.5;
                    cell.sz += bz + 0.5;
                    cell.n++;
                }
            }
        }

        if (layer > 2 * RADIUS) {
            ready = building.isEmpty() ? new ArrayList<>() : merge(building);
            layer = -1;
        }
    }

    private static boolean wasSelected(long id) {
        for (int i = 0; i < MAX; i++) if (slotId[i] == id) return true;
        return false;
    }

    // Вызывается каждый кадр: выбирает до четырёх самых заметных источников в кадре
    public static void select(Vec3 cam, Vector3f f, Vector3f rt, Vector3f up,
                              float tanHalf, float aspect, float strength) {
        for (int i = 0; i < MAX; i++) outI[i] = 0.0f;
        if (strength <= 0.0f || ready.isEmpty()) {
            Arrays.fill(slotId, NONE);
            Arrays.fill(slotSmooth, 0.0f);
            return;
        }

        double[] score = new double[MAX];
        Cluster[] best = new Cluster[MAX];
        double[] bu = new double[MAX], bv = new double[MAX], bd = new double[MAX], bdist = new double[MAX];

        for (Cluster c : ready) {
            double dx = c.x - cam.x, dy = c.y - cam.y, dz = c.z - cam.z;
            double dist2 = dx * dx + dy * dy + dz * dz;
            double dist = Math.sqrt(dist2);
            if (dist > 40.0) continue;

            double sz = dx * f.x + dy * f.y + dz * f.z;
            if (sz < 0.5) continue;
            double u = 0.5 + 0.5 * (dx * rt.x + dy * rt.y + dz * rt.z) / (sz * aspect * tanHalf);
            double v = 0.5 + 0.5 * (dx * up.x + dy * up.y + dz * up.z) / (sz * tanHalf);
            // Запас за краем экрана: источник чуть вне кадра ещё светит, поэтому нет полос при поворотах
            if (u < -0.5 || u > 1.5 || v < -0.5 || v > 1.5) continue;

            double s = Math.sqrt(c.n) / (dist2 + 16.0);
            if (wasSelected(c.id)) s *= 1.35;   // уже выбранный источник держится дольше, чтобы не мерцало

            int worst = 0;
            for (int i = 1; i < MAX; i++) if (score[i] < score[worst]) worst = i;
            if (s > score[worst]) {
                score[worst] = s;
                best[worst] = c;
                bu[worst] = u; bv[worst] = v; bd[worst] = sz; bdist[worst] = dist;
            }
        }

        // Раскладываем по слотам: источник остаётся в том же слоте, где был
        int[] slotOf = new int[MAX];
        boolean[] used = new boolean[MAX];
        Arrays.fill(slotOf, -1);
        for (int b = 0; b < MAX; b++) {
            if (best[b] == null) continue;
            for (int s = 0; s < MAX; s++) {
                if (slotId[s] == best[b].id && !used[s]) {
                    slotOf[b] = s;
                    used[s] = true;
                    break;
                }
            }
        }
        for (int b = 0; b < MAX; b++) {
            if (best[b] == null || slotOf[b] >= 0) continue;
            for (int s = 0; s < MAX; s++) {
                if (!used[s]) {
                    slotOf[b] = s;
                    used[s] = true;
                    slotId[s] = best[b].id;
                    slotSmooth[s] = 0.0f;
                    break;
                }
            }
        }
        for (int s = 0; s < MAX; s++) {
            if (!used[s]) {
                slotId[s] = NONE;
                slotSmooth[s] = 0.0f;
            }
        }

        for (int b = 0; b < MAX; b++) {
            Cluster c = best[b];
            if (c == null || slotOf[b] < 0) continue;
            int s = slotOf[b];

            double near = 1.0 - Math.min(1.0, Math.max(0.0, (bdist[b] - 8.0) / 32.0));
            double size = Math.min(1.5, 0.35 + 0.25 * Math.sqrt(c.n));
            float target = (float) (strength * size * near);
            slotSmooth[s] += (target - slotSmooth[s]) * 0.2f;   // плавное появление

            // Центр большого источника лежит внутри него, поэтому сдвигаем проверку ближе к камере
            double shift = Math.min(Math.sqrt(c.n) * 0.5, 8.0);

            float[] col = PALETTE[c.color];
            outU[s] = (float) bu[b];
            outV[s] = (float) bv[b];
            outD[s] = (float) Math.max(0.5, bd[b] - shift);
            outI[s] = slotSmooth[s];
            outR[s] = col[0]; outG[s] = col[1]; outB[s] = col[2];
        }
    }
}
