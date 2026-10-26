#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;

uniform float ErnstFar;
uniform float ErnstSat;
uniform float ErnstVibrance;
uniform float ErnstContrast;
uniform float ErnstWarmth;
uniform float ErnstCool;
uniform float ErnstSharpen;

uniform float ErnstFwdX, ErnstFwdY, ErnstFwdZ;
uniform float ErnstRightX, ErnstRightY, ErnstRightZ;
uniform float ErnstUpX, ErnstUpY, ErnstUpZ;
uniform float ErnstSunDX, ErnstSunDY, ErnstSunDZ;
uniform float ErnstCamY, ErnstTanHalf, ErnstAspect, ErnstSunLight;
uniform float ErnstFogR, ErnstFogG, ErnstFogB;
uniform float ErnstFogDensity, ErnstFogHeight, ErnstFogFalloff, ErnstFogSun;

uniform sampler2D ShadowMap;
uniform mat4 ShadowMat;
uniform vec3 ErnstShadowDir;
uniform float ErnstScreenShadow, ErnstShadowStrength, ErnstShadowSun, ErnstShadowDebug;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 px = 1.0 / OutSize;
    float depth = texture(DepthSampler, texCoord).r;
    vec3 c = texture(DiffuseSampler, texCoord).rgb;

    // резкость (подчёркивание контуров)
    vec3 n = texture(DiffuseSampler, texCoord + vec2(px.x, 0.0)).rgb
           + texture(DiffuseSampler, texCoord - vec2(px.x, 0.0)).rgb
           + texture(DiffuseSampler, texCoord + vec2(0.0, px.y)).rgb
           + texture(DiffuseSampler, texCoord - vec2(0.0, px.y)).rgb;
    c += (c - n * 0.25) * ErnstSharpen;

    // ===== тени из карты теней (когда мир рисует Sodium) =====
    if (ErnstScreenShadow > 0.5) {
        float zs = depth * 2.0 - 1.0;
        float linS = (2.0 * 0.05 * ErnstFar) / (ErnstFar + 0.05 - zs * (ErnstFar - 0.05));
        vec2 ndcS = texCoord * 2.0 - 1.0;
        vec3 Fs = vec3(ErnstFwdX, ErnstFwdY, ErnstFwdZ);
        vec3 Rs = vec3(ErnstRightX, ErnstRightY, ErnstRightZ);
        vec3 Us = vec3(ErnstUpX, ErnstUpY, ErnstUpZ);
        vec3 P = (Fs + Rs * (ndcS.x * ErnstAspect * ErnstTanHalf) + Us * (ndcS.y * ErnstTanHalf)) * linS;

        // нормаль поверхности по соседним пикселям
        vec3 N = normalize(cross(dFdx(P), dFdy(P)) + vec3(0.000001));
        if (dot(N, P) > 0.0) N = -N;

        if (depth < 0.9999) {
            float ndl = dot(N, normalize(ErnstShadowDir));
            float facing = smoothstep(-0.05, 0.2, ndl);

            vec4 sc = ShadowMat * vec4(P + N * 0.06, 1.0);
            vec3 sp = sc.xyz * 0.5 + 0.5;
            vec2 em = min(sp.xy, 1.0 - sp.xy);
            float inside = step(0.0, min(em.x, em.y)) * step(sp.z, 1.0);
            float edge = smoothstep(0.0, 0.08, min(em.x, em.y));

            float texel = 1.0 / float(textureSize(ShadowMap, 0).x);
            float bias = (0.15 + 0.35 * (1.0 - clamp(ndl, 0.0, 1.0))) / 400.0 * texel * 2048.0;

            float lit = 0.0;
            for (int ix = 0; ix < 2; ix++) {
                for (int iy = 0; iy < 2; iy++) {
                    vec2 o = (vec2(float(ix), float(iy)) - 0.5) * texel;
                    lit += step(sp.z - bias, texture(ShadowMap, sp.xy + o).r);
                }
            }
            lit *= 0.25;

            // чем дальше тень от перекрывающего блока, тем слабее (глубокие пещеры не темнеют)
            float occ = max(sp.z - texture(ShadowMap, sp.xy).r, 0.0) * 400.0;
            float reach = 1.0 - smoothstep(12.0, 48.0, occ);

            float shadow = (1.0 - lit) * inside * edge * reach;
            float dark = shadow * facing * ErnstShadowSun * ErnstShadowStrength;

            if (ErnstShadowDebug > 0.5) {
                c = mix(c, vec3(1.0, 0.0, 0.0), shadow * facing);
            } else {
                c = mix(c, c * vec3(0.58, 0.68, 0.90), clamp(dark, 0.0, 1.0));
            }
        }
    }

    float l = dot(c, vec3(0.299, 0.587, 0.114));

    // насыщенность, акценты усиливаются сильнее серого и белого
    float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(l), c, ErnstSat + ErnstVibrance * chroma);

    c = (c - 0.5) * ErnstContrast + 0.5;                       // контраст
    c += vec3(-0.008, 0.0, 0.012) * (1.0 - l) * ErnstCool;    // холодные тени
    c.r += ErnstWarmth;                                         // баланс тепла
    c.b -= ErnstWarmth;

    // ===== объёмный туман: плотность падает с высотой =====
    if (depth < 0.9999 && ErnstFogDensity > 0.0) {
        float zz = depth * 2.0 - 1.0;
        float lin = (2.0 * 0.05 * ErnstFar) / (ErnstFar + 0.05 - zz * (ErnstFar - 0.05));

        vec2 ndc = texCoord * 2.0 - 1.0;
        vec3 F = vec3(ErnstFwdX, ErnstFwdY, ErnstFwdZ);
        vec3 R = vec3(ErnstRightX, ErnstRightY, ErnstRightZ);
        vec3 U = vec3(ErnstUpX, ErnstUpY, ErnstUpZ);
        vec3 ray = F + R * (ndc.x * ErnstAspect * ErnstTanHalf) + U * (ndc.y * ErnstTanHalf);
        float rl = length(ray);
        vec3 rd = ray / rl;
        float L = lin * rl;                       // длина луча до пикселя в блоках

        float k = max(ErnstFogFalloff, 0.001);
        float dy = rd.y;
        float base = ErnstFogDensity * exp(clamp(-k * (ErnstCamY - ErnstFogHeight), -30.0, 30.0));
        float e = clamp(-k * dy * L, -30.0, 30.0);
        float tau = (abs(k * dy) < 0.0001) ? base * L : base * (1.0 - exp(e)) / (k * dy);
        float fog = clamp(1.0 - exp(-max(tau, 0.0)), 0.0, 0.97);

        // свечение тумана в сторону солнца
        vec3 sunD = vec3(ErnstSunDX, ErnstSunDY, ErnstSunDZ);
        float scat = pow(max(dot(rd, sunD), 0.0), 6.0) * ErnstSunLight * ErnstFogSun;
        vec3 fogCol = mix(vec3(ErnstFogR, ErnstFogG, ErnstFogB), vec3(1.0, 0.86, 0.62), clamp(scat, 0.0, 1.0));

        c = mix(c, fogCol, fog);
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
