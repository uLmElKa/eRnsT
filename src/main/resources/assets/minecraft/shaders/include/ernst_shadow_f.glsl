uniform sampler2D ShadowMap;
uniform float ErnstShadowStrength;
uniform float ErnstSunLight;
uniform float ErnstShadowDebug;
uniform vec3 ErnstSunDir;
uniform float ErnstShadowSky;   // 1 в Аду и Крае

in vec3 ernstShadowPos;
in vec4 ernstNormalSky;
in float ernstBlockLight;

vec3 ernstShadowApply(vec3 col) {
    if (ErnstShadowStrength <= 0.0) {
        return col;
    }

    vec3 n = normalize(ernstNormalSky.xyz);
    float ndl = dot(n, normalize(ErnstSunDir));
    float facing = smoothstep(-0.05, 0.2, ndl);
    float sky = max(ernstNormalSky.w, ErnstShadowSky);

    // внутри ли карты теней
    vec2 uv = ernstShadowPos.xy;
    float inside = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0)
                 * step(ernstShadowPos.z, 1.0);
    vec2 e = min(uv, 1.0 - uv);
    float edge = smoothstep(0.0, 0.08, min(e.x, e.y));

    // смещение, чтобы поверхности не затеняли сами себя
    float bias = (0.15 + 0.35 * (1.0 - clamp(ndl, 0.0, 1.0))) / 400.0;
    float texel = 1.0 / float(textureSize(ShadowMap, 0).x);
    bias *= texel * 2048.0;   // при меньшем разрешении нужен больший запас

    float lit = 0.0;
    for (int x = 0; x < 2; x++) {
        for (int y = 0; y < 2; y++) {
            vec2 o = (vec2(float(x), float(y)) - 0.5) * texel;
            float d = texture(ShadowMap, uv + o).r;
            lit += step(ernstShadowPos.z - bias, d);
        }
    }
    lit *= 0.25;

    float shadow = (1.0 - lit) * inside * edge;

    if (ErnstShadowDebug > 0.5) {
        // диагностика: красное это тень, синее это вне карты
        vec3 dbg = mix(col, vec3(1.0, 0.0, 0.0), shadow * facing);
        dbg = mix(dbg, vec3(0.0, 0.0, 1.0), (1.0 - inside) * 0.5);
        return dbg;
    }

    float dark = shadow * facing * sky * ErnstSunLight * ErnstShadowStrength;
    dark *= 1.0 - 0.6 * ernstBlockLight * ErnstShadowSky;   // у лавы и факелов тень слабее
    // как у цветного AO: цвет блока сохраняется, тень холоднее и темнее, но не чёрная
    vec3 shaded = col * vec3(0.58, 0.68, 0.90);
    col = mix(col, shaded, clamp(dark, 0.0, 1.0));
    return col;
}
