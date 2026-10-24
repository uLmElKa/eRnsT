#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;

uniform float ErnstFar;
uniform float ErnstDay;
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

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 px = 1.0 / OutSize;
    vec3 c = texture(DiffuseSampler, texCoord).rgb;

    // резкость (подчёркивание контуров)
    vec3 n = texture(DiffuseSampler, texCoord + vec2(px.x, 0.0)).rgb
           + texture(DiffuseSampler, texCoord - vec2(px.x, 0.0)).rgb
           + texture(DiffuseSampler, texCoord + vec2(0.0, px.y)).rgb
           + texture(DiffuseSampler, texCoord - vec2(0.0, px.y)).rgb;
    c += (c - n * 0.25) * ErnstSharpen;

    float l = dot(c, vec3(0.299, 0.587, 0.114));

    // насыщенность, акценты усиливаются сильнее серого и белого
    float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(l), c, ErnstSat + ErnstVibrance * chroma);

    c = (c - 0.5) * ErnstContrast + 0.5;                       // контраст
    c += vec3(-0.008, 0.0, 0.012) * (1.0 - l) * ErnstCool;    // холодные тени
    c.r += ErnstWarmth;                                         // баланс тепла
    c.b -= ErnstWarmth;

    // ===== объёмный туман: плотность падает с высотой =====
    float depth = texture(DepthSampler, texCoord).r;
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
