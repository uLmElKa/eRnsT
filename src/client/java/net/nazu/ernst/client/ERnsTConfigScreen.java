package net.nazu.ernst.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class ERnsTConfigScreen {
    private ERnsTConfigScreen() {}

    private static Component tr(String key) {
        return Component.translatable(key);
    }

    private static Component presetName(ERnsTConfig.Preset p) {
        return switch (p) {
            case MIRRORS_EDGE -> tr("text.ernst.preset.mirrors_edge");
            case CATALYST -> tr("text.ernst.preset.catalyst");
            case SOFT -> tr("text.ernst.preset.soft");
        };
    }

    private static Component qualityName(ERnsTConfig.Quality q) {
        return switch (q) {
            case PERFORMANCE -> tr("text.ernst.quality.performance");
            case BALANCED -> tr("text.ernst.quality.balanced");
            case QUALITY -> tr("text.ernst.quality.quality");
        };
    }

    private static String fmt(int v) {
        return String.format("%.2f", v / 100.0);
    }

    private static void pct(ConfigEntryBuilder eb, ConfigCategory cat, String key, float value,
                            int min, int max, int def, Consumer<Float> c) {
        cat.addEntry(eb.startIntSlider(tr("text.ernst." + key), Math.round(value * 100), min, max)
                .setDefaultValue(def)
                .setTextGetter(v -> Component.literal(fmt(v)))
                .setTooltip(tr("text.ernst." + key + ".tip"))
                .setSaveConsumer(v -> c.accept(v / 100.0f))
                .build());
    }

    private static void toggle(ConfigEntryBuilder eb, ConfigCategory cat, String key, boolean value,
                               boolean def, Consumer<Boolean> c) {
        cat.addEntry(eb.startBooleanToggle(tr("text.ernst." + key), value)
                .setDefaultValue(def)
                .setTooltip(tr("text.ernst." + key + ".tip"))
                .setSaveConsumer(c)
                .build());
    }

    private static void color(ConfigEntryBuilder eb, ConfigCategory cat, String key, int value,
                              int def, Consumer<Integer> c) {
        cat.addEntry(eb.startColorField(tr("text.ernst." + key), value)
                .setDefaultValue(def)
                .setSaveConsumer(v -> c.accept(v & 0xFFFFFF))
                .build());
    }

    public static Screen create(Screen parent) {
        ERnsTConfig cfg = ERnsTConfig.get();
        ERnsTConfig.Preset[] chosen = { cfg.preset };
        ERnsTConfig.Quality[] chosenQ = { cfg.quality };

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(tr("text.ernst.config.title"))
                .setSavingRunnable(() -> {
                    if (chosen[0] != cfg.preset) {
                        cfg.preset = chosen[0];
                        cfg.applyPreset(chosen[0]);
                    }
                    if (chosenQ[0] != cfg.quality) {
                        cfg.quality = chosenQ[0];
                        cfg.applyQuality(chosenQ[0]);
                    }
                    ERnsTConfig.save();
                    ERnsTClient.applyConfig(true);
                });

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ===== General =====
        ConfigCategory general = builder.getOrCreateCategory(tr("text.ernst.general"));

        toggle(eb, general, "enabled", cfg.enabled, true, v -> cfg.enabled = v);

        general.addEntry(eb.startEnumSelector(tr("text.ernst.preset"), ERnsTConfig.Preset.class, cfg.preset)
                .setDefaultValue(ERnsTConfig.Preset.MIRRORS_EDGE)
                .setEnumNameProvider(e -> presetName((ERnsTConfig.Preset) e))
                .setTooltip(tr("text.ernst.preset.tip"))
                .setSaveConsumer(v -> chosen[0] = v)
                .build());

        general.addEntry(eb.startEnumSelector(tr("text.ernst.quality"), ERnsTConfig.Quality.class, cfg.quality)
                .setDefaultValue(ERnsTConfig.Quality.BALANCED)
                .setEnumNameProvider(e -> qualityName((ERnsTConfig.Quality) e))
                .setTooltip(tr("text.ernst.quality.tip"))
                .setSaveConsumer(v -> chosenQ[0] = v)
                .build());

        general.addEntry(eb.startIntSlider(tr("text.ernst.light_radius"), cfg.shadowDistanceChunks, 2, 16)
                .setDefaultValue(8)
                .setTooltip(tr("text.ernst.light_radius.tip"))
                .setSaveConsumer(v -> cfg.shadowDistanceChunks = v)
                .build());

        toggle(eb, general, "notification", cfg.showNotification, true, v -> cfg.showNotification = v);
        toggle(eb, general, "lightmap", cfg.lightmapEnabled, true, v -> cfg.lightmapEnabled = v);
        toggle(eb, general, "sky", cfg.skyEnabled, true, v -> cfg.skyEnabled = v);

        // ===== Blocks & Lighting =====
        ConfigCategory blocks = builder.getOrCreateCategory(tr("text.ernst.blocks"));

        pct(eb, blocks, "terrain_effect", cfg.terrainEffect, 0, 180, 100, v -> cfg.terrainEffect = v);
        pct(eb, blocks, "ao_contrast", cfg.aoContrast, 0, 100, 60, v -> cfg.aoContrast = v);
        pct(eb, blocks, "shade_up", cfg.shadeUp, 70, 110, 100, v -> cfg.shadeUp = v);
        pct(eb, blocks, "shade_ns", cfg.shadeNS, 40, 110, 74, v -> cfg.shadeNS = v);
        pct(eb, blocks, "shade_we", cfg.shadeWE, 30, 110, 55, v -> cfg.shadeWE = v);
        pct(eb, blocks, "shade_down", cfg.shadeDown, 20, 110, 40, v -> cfg.shadeDown = v);
        pct(eb, blocks, "shade_tint", cfg.shadeTint, 0, 100, 40, v -> cfg.shadeTint = v);
        pct(eb, blocks, "light_cool", cfg.lightCool, 0, 200, 100, v -> cfg.lightCool = v);
        pct(eb, blocks, "light_warmth", cfg.lightWarmth, 0, 200, 100, v -> cfg.lightWarmth = v);
        pct(eb, blocks, "light_contrast", cfg.lightContrast, 0, 100, 35, v -> cfg.lightContrast = v);
        pct(eb, blocks, "light_saturation", cfg.lightSaturation, 80, 160, 112, v -> cfg.lightSaturation = v);

        // ===== Sky & Fog =====
        ConfigCategory sky = builder.getOrCreateCategory(tr("text.ernst.sky_cat"));

        color(eb, sky, "day_zenith", cfg.dayZenith, 0x4D85E6, v -> cfg.dayZenith = v);
        color(eb, sky, "day_horizon", cfg.dayHorizon, 0x99C7FF, v -> cfg.dayHorizon = v);
        color(eb, sky, "night_zenith", cfg.nightZenith, 0x04081C, v -> cfg.nightZenith = v);
        color(eb, sky, "night_horizon", cfg.nightHorizon, 0x122E85, v -> cfg.nightHorizon = v);
        pct(eb, sky, "rain_darken", cfg.rainDarken, 0, 60, 30, v -> cfg.rainDarken = v);

        // ===== Color Grading =====
        ConfigCategory colorCat = builder.getOrCreateCategory(tr("text.ernst.color_cat"));

        pct(eb, colorCat, "saturation", cfg.saturation, 80, 160, 112, v -> cfg.saturation = v);
        pct(eb, colorCat, "vibrance", cfg.vibrance, 0, 100, 0, v -> cfg.vibrance = v);
        pct(eb, colorCat, "contrast", cfg.contrast, 90, 130, 106, v -> cfg.contrast = v);
        pct(eb, colorCat, "exposure", cfg.exposure, 80, 140, 112, v -> cfg.exposure = v);
        pct(eb, colorCat, "cool_shadows", cfg.coolShadows, 0, 200, 100, v -> cfg.coolShadows = v);
        colorCat.addEntry(eb.startIntSlider(tr("text.ernst.warmth"), Math.round(cfg.warmth * 1000), -50, 50)
                .setDefaultValue(0)
                .setTextGetter(v -> Component.literal(String.format("%.3f", v / 1000.0)))
                .setTooltip(tr("text.ernst.warmth.tip"))
                .setSaveConsumer(v -> cfg.warmth = v / 1000.0f)
                .build());
        pct(eb, colorCat, "tone_shoulder", cfg.toneShoulder, 60, 95, 85, v -> cfg.toneShoulder = v);
        pct(eb, colorCat, "vignette", cfg.vignette, 0, 60, 0, v -> cfg.vignette = v);
        pct(eb, colorCat, "sharpen", cfg.sharpen, 0, 100, 0, v -> cfg.sharpen = v);

        // ===== Bloom & Rays =====
        ConfigCategory bloom = builder.getOrCreateCategory(tr("text.ernst.bloom_cat"));

        toggle(eb, bloom, "bloom_enabled", cfg.bloomEnabled, true, v -> cfg.bloomEnabled = v);
        toggle(eb, bloom, "rays_enabled", cfg.raysEnabled, true, v -> cfg.raysEnabled = v);
        toggle(eb, bloom, "light_rays", cfg.lightRays, false, v -> cfg.lightRays = v);
        pct(eb, bloom, "light_ray_strength", cfg.lightRayStrength, 0, 150, 60, v -> cfg.lightRayStrength = v);

        pct(eb, bloom, "bloom_strength", cfg.bloomStrength, 0, 150, 50, v -> cfg.bloomStrength = v);
        pct(eb, bloom, "bloom_threshold", cfg.bloomThreshold, 60, 100, 86, v -> cfg.bloomThreshold = v);
        pct(eb, bloom, "bloom_radius", cfg.bloomRadius, 50, 200, 100, v -> cfg.bloomRadius = v);
        pct(eb, bloom, "light_boost", cfg.lightBoost, 0, 300, 150, v -> cfg.lightBoost = v);
        toggle(eb, bloom, "entity_bloom", cfg.entityBloom, false, v -> cfg.entityBloom = v);

        pct(eb, bloom, "ray_strength", cfg.rayStrength, 0, 150, 35, v -> cfg.rayStrength = v);
        pct(eb, bloom, "ray_length", cfg.rayLength, 30, 100, 85, v -> cfg.rayLength = v);
        pct(eb, bloom, "fog_density", cfg.fogDensity, 0, 100, 20, v -> cfg.fogDensity = v);
        bloom.addEntry(eb.startIntSlider(tr("text.ernst.fog_height"), Math.round(cfg.fogHeight), 40, 200)
                .setDefaultValue(80)
                .setTooltip(tr("text.ernst.fog_height.tip"))
                .setSaveConsumer(v -> cfg.fogHeight = v)
                .build());
        pct(eb, bloom, "fog_falloff", cfg.fogFalloff, 2, 15, 6, v -> cfg.fogFalloff = v);
        pct(eb, bloom, "fog_sun", cfg.fogSun, 0, 100, 50, v -> cfg.fogSun = v);

        // ===== Shadows =====
        ConfigCategory shadows = builder.getOrCreateCategory(tr("text.ernst.shadows_cat"));

        toggle(eb, shadows, "shadows_enabled", cfg.shadowsEnabled, false, v -> cfg.shadowsEnabled = v);
        pct(eb, shadows, "shadow_strength", cfg.shadowStrength, 0, 100, 60, v -> cfg.shadowStrength = v);
        shadows.addEntry(eb.startIntSlider(tr("text.ernst.shadow_range"), Math.round(cfg.shadowRange), 32, 128)
                .setDefaultValue(64)
                .setTooltip(tr("text.ernst.shadow_range.tip"))
                .setSaveConsumer(v -> cfg.shadowRange = v)
                .build());
        toggle(eb, shadows, "shadow_high_res", cfg.shadowHighRes, false, v -> cfg.shadowHighRes = v);
        toggle(eb, shadows, "shadows_nether", cfg.shadowsNether, true, v -> cfg.shadowsNether = v);
        shadows.addEntry(eb.startIntSlider(tr("text.ernst.nether_height"), Math.round(cfg.netherShadowHeight), 8, 64)
                .setDefaultValue(24)
                .setTooltip(tr("text.ernst.nether_height.tip"))
                .setSaveConsumer(v -> cfg.netherShadowHeight = v)
                .build());
        toggle(eb, shadows, "shadows_end", cfg.shadowsEnd, true, v -> cfg.shadowsEnd = v);
        toggle(eb, shadows, "shadow_debug", cfg.shadowDebug, false, v -> cfg.shadowDebug = v);

        return builder.build();
    }
}