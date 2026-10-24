uniform sampler2D ShadowMap;
uniform float ErnstShadowStrength;
uniform float ErnstSunLight;
uniform float ErnstShadowDebug;
uniform vec3 ErnstSunDir;

in vec3 ernstShadowPos;
in vec4 ernstNormalSky;

const float ERNST_SHADOW_RES = 2048.0;

vec3 ernstShadowApply(vec3 col) {
    if (ErnstShadowStrength <= 0.0) {
        return col;
    }

    vec3 n = normalize(ernstNormalSky.xyz);
    float ndl = dot(n, normalize(ErnstSunDir));
    float facing = smoothstep(0.0, 0.25, ndl);
    float sky = ernstNormalSky.w;

    // внутри ли карты теней
    vec2 uv = ernstShadowPos.xy;
    float inside = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0)
                 * step(ernstShadowPos.z, 1.0);
    vec2 e = min(uv, 1.0 - uv);
    float edge = smoothstep(0.0, 0.08, min(e.x, e.y));

    // смещение, чтобы поверхности не затеняли сами себя
    float bias = (0.15 + 0.35 * (1.0 - clamp(ndl, 0.0, 1.0))) / 400.0;
    float texel = 1.0 / ERNST_SHADOW_RES;

    float lit = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float d = texture(ShadowMap, uv + vec2(float(x), float(y)) * texel).r;
            lit += step(ernstShadowPos.z - bias, d);
        }
    }
    lit /= 9.0;

    float shadow = (1.0 - lit) * inside * edge;

    if (ErnstShadowDebug > 0.5) {
        // диагностика: красное это тень, синее это вне карты
        vec3 dbg = mix(col, vec3(1.0, 0.0, 0.0), shadow * facing);
        dbg = mix(dbg, vec3(0.0, 0.0, 1.0), (1.0 - inside) * 0.5);
        return dbg;
    }

    float dark = shadow * facing * sky * ErnstSunLight * ErnstShadowStrength;
    col *= 1.0 - dark;
    col *= mix(vec3(1.0), vec3(0.86, 0.94, 1.08), dark);
    return col;
}
