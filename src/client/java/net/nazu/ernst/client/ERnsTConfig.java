package net.nazu.ernst.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ERnsTConfig {

    public enum Preset { MIRRORS_EDGE, CATALYST, SOFT }
    public enum Quality { PERFORMANCE, BALANCED, QUALITY }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ERnsTConfig instance;

    // ===== Общие (пресет их не меняет) =====
    public boolean enabled = true;
    public Preset preset = Preset.MIRRORS_EDGE;
    public int shadowDistanceChunks = 8;
    public boolean showNotification = true;
    public boolean lightmapEnabled = true;
    public boolean skyEnabled = true;
    public boolean entityBloom = false;      // bloom на сущностях
    public Quality quality = Quality.BALANCED;
    public boolean bloomEnabled = true;      // выключенный bloom полностью пропускается
    public boolean raysEnabled = false;      // выключенные лучи полностью пропускаются
    public boolean shadowsEnabled = false;   // динамические тени от блоков
    public float shadowStrength = 0.6f;
    public float shadowRange = 64.0f;        // радиус карты теней в блоках
    public boolean shadowDebug = false;      // красным показывает, где тень
    public boolean shadowHighRes = false;    // карта теней 2048 вместо 1024
    public boolean sodiumTerrainEffects = true;   // эффекты блоков внутри шейдера Sodium
    public boolean lightRays = false;        // лучи от факелов, лавы, порталов
    public float lightRayStrength = 0.6f;
    public boolean shadowsNether = true;     // тени в Аду (только от высоких построек)
    public boolean shadowsEnd = true;        // тени в Крае
    public float netherShadowHeight = 24.0f; // постройки выше этой отметки над игроком тени не дают

    // ===== Блоки и освещение =====
    public float terrainEffect;               // сила цветного AO и упрощения текстур
    public float aoContrast;
    public float shadeUp, shadeNS, shadeWE, shadeDown;   // яркость граней по направлению
    public float shadeTint;                                // холодный оттенок затенённых мест
    public float lightCool, lightWarmth, lightContrast, lightSaturation;

    // ===== Небо и туман =====
    public int dayZenith, dayHorizon, nightZenith, nightHorizon;
    public float rainDarken;

    // ===== Цвет картинки =====
    public float saturation, vibrance, contrast, exposure, warmth, coolShadows, toneShoulder, vignette, sharpen;

    // ===== Bloom и дымка =====
    public float bloomStrength, bloomThreshold, bloomRadius, lightBoost;

    // ===== Объёмный туман и лучи света =====
    public float fogDensity, fogHeight, fogFalloff, fogSun;
    public float rayStrength, rayLength;

    public ERnsTConfig() {
        applyPreset(Preset.MIRRORS_EDGE);
        applyQuality(Quality.BALANCED);
    }

    // Пресет производительности меняет только то, что влияет на FPS
    public void applyQuality(Quality q) {
        switch (q) {
            case PERFORMANCE -> { bloomEnabled = false; raysEnabled = false; shadowsEnabled = false; lightRays = false; shadowHighRes = false; shadowDistanceChunks = 4; }
            case QUALITY -> { bloomEnabled = true; raysEnabled = true; shadowsEnabled = true; lightRays = true; shadowHighRes = true; shadowDistanceChunks = 12; }
            default -> { bloomEnabled = true; raysEnabled = false; shadowsEnabled = false; lightRays = false; shadowHighRes = false; shadowDistanceChunks = 8; }
        }
    }

    public void applyPreset(Preset p) {
        // Сначала базовые значения (Mirror's Edge), потом переопределяем нужные
        terrainEffect = 1.0f;
        aoContrast = 0.60f; shadeUp = 1.0f; shadeNS = 0.74f; shadeWE = 0.55f; shadeDown = 0.40f; shadeTint = 0.40f;
        lightCool = 1.0f; lightWarmth = 1.0f; lightContrast = 0.35f; lightSaturation = 1.12f;
        dayZenith = 0x4D85E6;   dayHorizon = 0x99C7FF;
        nightZenith = 0x04081C; nightHorizon = 0x122E85;
        rainDarken = 0.30f;
        saturation = 1.12f; vibrance = 0.0f; contrast = 1.06f; exposure = 1.12f;
        warmth = 0.0f; coolShadows = 1.0f; toneShoulder = 0.85f; vignette = 0.0f; sharpen = 0.0f;
        bloomStrength = 0.50f; bloomThreshold = 0.86f; bloomRadius = 1.0f; lightBoost = 1.5f;
        fogDensity = 0.20f; fogHeight = 80.0f; fogFalloff = 0.06f; fogSun = 0.50f;
        rayStrength = 0.35f; rayLength = 0.85f;

        switch (p) {
            case CATALYST -> {
                terrainEffect = 1.1f;
                aoContrast = 0.70f; shadeNS = 0.72f; shadeWE = 0.54f; shadeDown = 0.38f; shadeTint = 0.70f;
                lightCool = 1.4f; lightWarmth = 0.7f; lightContrast = 0.40f; lightSaturation = 1.18f;
                dayZenith = 0x1F52C7;   dayHorizon = 0x80B8FF;
                nightZenith = 0x030517; nightHorizon = 0x0D2680;
                rainDarken = 0.35f;
                saturation = 1.20f; vibrance = 0.35f; contrast = 1.12f; exposure = 1.05f;
                warmth = -0.015f; coolShadows = 1.4f; toneShoulder = 0.82f; vignette = 0.15f; sharpen = 0.20f;
                bloomStrength = 0.60f; bloomThreshold = 0.82f; bloomRadius = 1.2f; lightBoost = 1.8f;
                fogDensity = 0.30f; fogHeight = 90.0f; fogFalloff = 0.05f; fogSun = 0.70f;
                rayStrength = 0.55f; rayLength = 0.90f;
            }
            case SOFT -> {
                terrainEffect = 0.8f;
                aoContrast = 0.45f; shadeNS = 0.76f; shadeWE = 0.57f; shadeDown = 0.43f; shadeTint = 0.20f;
                lightCool = 0.7f; lightWarmth = 1.0f; lightContrast = 0.25f; lightSaturation = 1.05f;
                dayZenith = 0x669EF2;   dayHorizon = 0xADD6FF;
                nightZenith = 0x050A24; nightHorizon = 0x17368C;
                rainDarken = 0.25f;
                saturation = 1.05f; contrast = 1.02f; exposure = 1.00f;
                coolShadows = 0.7f; toneShoulder = 0.88f;
                bloomStrength = 0.30f; bloomThreshold = 0.90f; lightBoost = 1.2f;
                fogDensity = 0.10f; fogHeight = 70.0f; fogFalloff = 0.07f; fogSun = 0.30f;
                rayStrength = 0.20f; rayLength = 0.80f;
            }
            default -> { }
        }
    }

    public static float red(int rgb)   { return ((rgb >> 16) & 0xFF) / 255.0f; }
    public static float green(int rgb) { return ((rgb >> 8) & 0xFF) / 255.0f; }
    public static float blue(int rgb)  { return (rgb & 0xFF) / 255.0f; }

    public static ERnsTConfig get() {
        if (instance == null) load();
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("ernst.json");
    }

    public static void load() {
        ERnsTConfig loaded = null;
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                loaded = GSON.fromJson(r, ERnsTConfig.class);
            } catch (Exception e) {
                ERnsTClient.LOGGER.warn("[eRnsT] Не удалось прочитать конфиг, использую значения по умолчанию", e);
            }
        }
        boolean fresh = loaded == null;
        instance = fresh ? new ERnsTConfig() : loaded;
        if (fresh) save();
    }

    public static void save() {
        if (instance == null) return;
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            ERnsTClient.LOGGER.warn("[eRnsT] Не удалось сохранить конфиг", e);
        }
    }
}
