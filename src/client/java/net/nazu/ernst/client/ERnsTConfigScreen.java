package net.nazu.ernst.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class ERnsTConfigScreen {
    private ERnsTConfigScreen() {}

    private static Component t(String s) {
        return Component.literal(s);
    }

    private static String presetName(ERnsTConfig.Preset p) {
        return switch (p) {
            case MIRRORS_EDGE -> "Mirror's Edge";
            case CATALYST -> "Mirror's Edge Catalyst";
            case SOFT -> "Мягкий";
        };
    }

    private static String qualityName(ERnsTConfig.Quality q) {
        return switch (q) {
            case PERFORMANCE -> "Производительность";
            case BALANCED -> "Баланс";
            case QUALITY -> "Качество";
        };
    }

    private static String fmt(int v) {
        return String.format("%.2f", v / 100.0);
    }

    // Ползунок: значение в сотых долях (112 = 1.12)
    private static void pct(ConfigEntryBuilder eb, ConfigCategory cat, String name, float value,
                            int min, int max, int def, String tip, Consumer<Float> c) {
        cat.addEntry(eb.startIntSlider(t(name), Math.round(value * 100), min, max)
                .setDefaultValue(def)
                .setTextGetter(v -> t(fmt(v)))
                .setTooltip(t(tip))
                .setSaveConsumer(v -> c.accept(v / 100.0f))
                .build());
    }

    private static void toggle(ConfigEntryBuilder eb, ConfigCategory cat, String name, boolean value,
                               boolean def, String tip, Consumer<Boolean> c) {
        cat.addEntry(eb.startBooleanToggle(t(name), value)
                .setDefaultValue(def)
                .setTooltip(t(tip))
                .setSaveConsumer(c)
                .build());
    }

    private static void color(ConfigEntryBuilder eb, ConfigCategory cat, String name, int value,
                              int def, Consumer<Integer> c) {
        cat.addEntry(eb.startColorField(t(name), value)
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
                .setTitle(t("eRnsT: настройки графики"))
                .setSavingRunnable(() -> {
                    // Если выбран другой пресет, его значения заменяют настройки на вкладках (кроме «Общих»)
                    if (chosen[0] != cfg.preset) {
                        cfg.preset = chosen[0];
                        cfg.applyPreset(chosen[0]);
                    }
                    // Пресет производительности: bloom, лучи и радиус света
                    if (chosenQ[0] != cfg.quality) {
                        cfg.quality = chosenQ[0];
                        cfg.applyQuality(chosenQ[0]);
                    }
                    ERnsTConfig.save();
                    ERnsTClient.applyConfig(true);
                });

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ===== Общие =====
        ConfigCategory general = builder.getOrCreateCategory(t("Общие"));

        toggle(eb, general, "Мод включён", cfg.enabled, true,
                "То же, что клавиша O.", v -> cfg.enabled = v);

        general.addEntry(eb.startEnumSelector(t("Пресет"), ERnsTConfig.Preset.class, cfg.preset)
                .setDefaultValue(ERnsTConfig.Preset.MIRRORS_EDGE)
                .setEnumNameProvider(e -> t(presetName((ERnsTConfig.Preset) e)))
                .setTooltip(t("После выбора нажми «Сохранить и выйти». Значения пресета заменят настройки на всех вкладках, кроме «Общих». Потом открой меню снова и подкрути."))
                .setSaveConsumer(v -> chosen[0] = v)
                .build());

        general.addEntry(eb.startEnumSelector(t("Производительность"), ERnsTConfig.Quality.class, cfg.quality)
                .setDefaultValue(ERnsTConfig.Quality.BALANCED)
                .setEnumNameProvider(e -> t(qualityName((ERnsTConfig.Quality) e)))
                .setTooltip(t("Производительность: без bloom и лучей, радиус света 4. Баланс: bloom без лучей, радиус 8. Качество: всё включено, включая тени, радиус 12. Меняет только bloom, лучи, тени и радиус света, остальные настройки не трогает."))
                .setSaveConsumer(v -> chosenQ[0] = v)
                .build());

        general.addEntry(eb.startIntSlider(t("Радиус кастомного света (чанки)"), cfg.shadowDistanceChunks, 2, 16)
                .setDefaultValue(8)
                .setTooltip(t("Дальше этого расстояния работает обычное освещение."))
                .setSaveConsumer(v -> cfg.shadowDistanceChunks = v)
                .build());

        toggle(eb, general, "Показывать уведомление при включении", cfg.showNotification, true,
                "Плашка в углу и сообщение в чате при нажатии O.", v -> cfg.showNotification = v);

        toggle(eb, general, "Своя карта освещения", cfg.lightmapEnabled, true,
                "Холодные тени и тёплый свет блоков.", v -> cfg.lightmapEnabled = v);

        toggle(eb, general, "Своё небо и туман", cfg.skyEnabled, true,
                "Выключи, чтобы вернуть ванильные цвета неба и тумана.", v -> cfg.skyEnabled = v);

        // ===== Блоки и освещение =====
        ConfigCategory blocks = builder.getOrCreateCategory(t("Блоки и освещение"));

        pct(eb, blocks, "Эффект на блоках (цветное AO и текстуры)", cfg.terrainEffect, 0, 180, 100,
                "Общая сила цветного затенения и упрощения текстур. 0 выключает.", v -> cfg.terrainEffect = v);
        pct(eb, blocks, "Контраст затенения углов (AO)", cfg.aoContrast, 0, 100, 60,
                "Больше значит резче затемнение в углах.", v -> cfg.aoContrast = v);
        pct(eb, blocks, "Яркость граней: верх", cfg.shadeUp, 70, 110, 100,
                "В ваниле 1.00. Выше 1.00 верхние грани светлее.", v -> cfg.shadeUp = v);
        pct(eb, blocks, "Яркость граней: север и юг", cfg.shadeNS, 40, 110, 74,
                "В ваниле 0.80. Ближе к верху значит чище и площе, меньше значит контрастнее.", v -> cfg.shadeNS = v);
        pct(eb, blocks, "Яркость граней: запад и восток", cfg.shadeWE, 30, 110, 55,
                "В ваниле 0.60.", v -> cfg.shadeWE = v);
        pct(eb, blocks, "Яркость граней: низ", cfg.shadeDown, 20, 110, 40,
                "В ваниле 0.50.", v -> cfg.shadeDown = v);
        pct(eb, blocks, "Холодный оттенок затенённых мест", cfg.shadeTint, 0, 100, 40,
                "Подкрашивает тёмные грани и углы в голубоватый. 0 выключает.", v -> cfg.shadeTint = v);
        pct(eb, blocks, "Холодность теней", cfg.lightCool, 0, 200, 100,
                "Насколько тени уходят в синий.", v -> cfg.lightCool = v);
        pct(eb, blocks, "Тепло света блоков", cfg.lightWarmth, 0, 200, 100,
                "Насколько тёплым выглядит свет факелов и ламп.", v -> cfg.lightWarmth = v);
        pct(eb, blocks, "Контраст освещения", cfg.lightContrast, 0, 100, 35,
                "Кривая яркости освещения.", v -> cfg.lightContrast = v);
        pct(eb, blocks, "Насыщенность освещения", cfg.lightSaturation, 80, 160, 112,
                "Насыщенность цвета света.", v -> cfg.lightSaturation = v);

        // ===== Небо и туман =====
        ConfigCategory sky = builder.getOrCreateCategory(t("Небо и туман"));

        color(eb, sky, "День: верх неба", cfg.dayZenith, 0x4D85E6, v -> cfg.dayZenith = v);
        color(eb, sky, "День: горизонт и туман", cfg.dayHorizon, 0x99C7FF, v -> cfg.dayHorizon = v);
        color(eb, sky, "Ночь: верх неба", cfg.nightZenith, 0x04081C, v -> cfg.nightZenith = v);
        color(eb, sky, "Ночь: горизонт и туман", cfg.nightHorizon, 0x122E85, v -> cfg.nightHorizon = v);
        pct(eb, sky, "Затемнение в дождь", cfg.rainDarken, 0, 60, 30,
                "Насколько темнеет дневное небо во время дождя.", v -> cfg.rainDarken = v);

        // ===== Цвет картинки =====
        ConfigCategory color = builder.getOrCreateCategory(t("Цвет картинки"));

        pct(eb, color, "Насыщенность", cfg.saturation, 80, 160, 112,
                "Общая насыщенность цветов.", v -> cfg.saturation = v);
        pct(eb, color, "Насыщенность акцентов", cfg.vibrance, 0, 100, 0,
                "Усиливает уже яркие цвета (красный, синий, оранжевый), а белое и серое не трогает.", v -> cfg.vibrance = v);
        pct(eb, color, "Контраст", cfg.contrast, 90, 130, 106,
                "Разница между светлым и тёмным.", v -> cfg.contrast = v);
        pct(eb, color, "Экспозиция", cfg.exposure, 80, 140, 112,
                "Общая яркость картинки.", v -> cfg.exposure = v);
        pct(eb, color, "Холодность теней (картинка)", cfg.coolShadows, 0, 200, 100,
                "Синеватый оттенок в тёмных местах.", v -> cfg.coolShadows = v);
        color.addEntry(eb.startIntSlider(t("Баланс тепла"), Math.round(cfg.warmth * 1000), -50, 50)
                .setDefaultValue(0)
                .setTextGetter(v -> t(String.format("%.3f", v / 1000.0)))
                .setTooltip(t("Меньше нуля холоднее и синее, больше нуля теплее."))
                .setSaveConsumer(v -> cfg.warmth = v / 1000.0f)
                .build());
        pct(eb, color, "Начало плавного перехода в белое", cfg.toneShoulder, 60, 95, 85,
                "Меньше значит яркие места сглаживаются раньше и мягче.", v -> cfg.toneShoulder = v);
        pct(eb, color, "Виньетка", cfg.vignette, 0, 60, 0,
                "Затемнение по краям экрана.", v -> cfg.vignette = v);
        pct(eb, color, "Резкость", cfg.sharpen, 0, 100, 0,
                "Подчёркивает контуры. Много резкости даёт ореолы.", v -> cfg.sharpen = v);

        // ===== Bloom и дымка =====
        ConfigCategory bloom = builder.getOrCreateCategory(t("Bloom, лучи и туман"));

        toggle(eb, bloom, "Bloom включён", cfg.bloomEnabled, true,
                "Выключенный bloom полностью пропускается и не тратит FPS. Ползунок силы на 0 этого не делает.",
                v -> cfg.bloomEnabled = v);
        toggle(eb, bloom, "Лучи света включены", cfg.raysEnabled, true,
                "Выключенные лучи полностью пропускаются и не тратят FPS.",
                v -> cfg.raysEnabled = v);

        pct(eb, bloom, "Bloom: сила", cfg.bloomStrength, 0, 150, 50,
                "Сила свечения. 0 выключает эффект.", v -> cfg.bloomStrength = v);
        pct(eb, bloom, "Bloom: порог яркости", cfg.bloomThreshold, 60, 100, 86,
                "Чем выше, тем меньше поверхностей светится.", v -> cfg.bloomThreshold = v);
        pct(eb, bloom, "Bloom: размер ореола", cfg.bloomRadius, 50, 200, 100,
                "Больше значит шире и мягче, но слишком большие значения дают зернистость.", v -> cfg.bloomRadius = v);
        pct(eb, bloom, "Bloom: усиление источников света", cfg.lightBoost, 0, 300, 150,
                "Насколько ярче светятся факелы, лампы и лава по сравнению с белыми поверхностями.", v -> cfg.lightBoost = v);
        toggle(eb, bloom, "Bloom на сущностях", cfg.entityBloom, false,
                "Выключено: мобы, игроки и предметы в мире не светятся. Заодно не светятся вода, частицы и облака.",
                v -> cfg.entityBloom = v);

        pct(eb, bloom, "Лучи света: сила", cfg.rayStrength, 0, 150, 35,
                "Лучи от солнца сквозь просветы. 0 выключает.", v -> cfg.rayStrength = v);
        pct(eb, bloom, "Лучи света: длина", cfg.rayLength, 30, 100, 85,
                "Как далеко от солнца тянутся лучи.", v -> cfg.rayLength = v);
        pct(eb, bloom, "Туман: плотность", cfg.fogDensity, 0, 100, 20,
                "Объёмный туман, который собирается внизу. 0 выключает.", v -> cfg.fogDensity = v);
        bloom.addEntry(eb.startIntSlider(t("Туман: высота (блоки)"), Math.round(cfg.fogHeight), 40, 200)
                .setDefaultValue(80)
                .setTooltip(t("Выше этой отметки туман редеет. Над ней он почти не виден."))
                .setSaveConsumer(v -> cfg.fogHeight = v)
                .build());
        pct(eb, bloom, "Туман: резкость границы", cfg.fogFalloff, 2, 15, 6,
                "Меньше значит туман поднимается выше и мягче, больше значит прижат к земле.", v -> cfg.fogFalloff = v);
        pct(eb, bloom, "Туман: свечение у солнца", cfg.fogSun, 0, 100, 50,
                "Насколько туман светится тёплым цветом в сторону солнца.", v -> cfg.fogSun = v);

        // ===== Тени =====
        ConfigCategory shadows = builder.getOrCreateCategory(t("Тени"));

        toggle(eb, shadows, "Динамические тени от блоков", cfg.shadowsEnabled, false,
                "Тени от солнца. Дорого: рельеф рисуется второй раз. Работают днём в Верхнем мире.",
                v -> cfg.shadowsEnabled = v);
        pct(eb, shadows, "Сила теней", cfg.shadowStrength, 0, 100, 60,
                "Насколько тёмной будет тень.", v -> cfg.shadowStrength = v);
        shadows.addEntry(eb.startIntSlider(t("Дальность теней (блоки)"), Math.round(cfg.shadowRange), 32, 128)
                .setDefaultValue(64)
                .setTooltip(t("Радиус вокруг тебя, где есть тени. Больше значит дальше, но тени резче всего вблизи."))
                .setSaveConsumer(v -> cfg.shadowRange = v)
                .build());
        toggle(eb, shadows, "Диагностика теней", cfg.shadowDebug, false,
                "Красным закрашивает места в тени, синим вне карты теней. Нужно только для проверки.",
                v -> cfg.shadowDebug = v);

        return builder.build();
    }
}
