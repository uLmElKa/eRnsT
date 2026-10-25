#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform float ErnstFar;
uniform float ErnstRays;
uniform float ErnstRayLen;
uniform float ErnstFwdX, ErnstFwdY, ErnstFwdZ;
uniform float ErnstRightX, ErnstRightY, ErnstRightZ;
uniform float ErnstUpX, ErnstUpY, ErnstUpZ;
uniform float ErnstSunDX, ErnstSunDY, ErnstSunDZ;
uniform float ErnstTanHalf, ErnstAspect, ErnstSunLight;
uniform float ErnstL0U, ErnstL0V, ErnstL0D, ErnstL0I, ErnstL0R, ErnstL0G, ErnstL0B;
uniform float ErnstL1U, ErnstL1V, ErnstL1D, ErnstL1I, ErnstL1R, ErnstL1G, ErnstL1B;
uniform float ErnstL2U, ErnstL2V, ErnstL2D, ErnstL2I, ErnstL2R, ErnstL2G, ErnstL2B;
uniform float ErnstL3U, ErnstL3V, ErnstL3D, ErnstL3I, ErnstL3R, ErnstL3G, ErnstL3B;

in vec2 texCoord;
out vec4 fragColor;

float linDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * 0.05 * ErnstFar) / (ErnstFar + 0.05 - z * (ErnstFar - 0.05));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// ===== лучи солнца =====
vec3 sunRays() {
    vec3 F = vec3(ErnstFwdX, ErnstFwdY, ErnstFwdZ);
    vec3 R = vec3(ErnstRightX, ErnstRightY, ErnstRightZ);
    vec3 U = vec3(ErnstUpX, ErnstUpY, ErnstUpZ);
    vec3 S = vec3(ErnstSunDX, ErnstSunDY, ErnstSunDZ);

    float sz = dot(S, F);
    float front = step(0.05, sz);
    sz = max(sz, 0.05);
    vec2 sun = vec2(0.5 + 0.5 * dot(S, R) / (sz * ErnstAspect * ErnstTanHalf),
                    0.5 + 0.5 * dot(S, U) / (sz * ErnstTanHalf));

    // пиксели далеко от солнца почти не светятся, считать их не нужно
    float dsun = length((texCoord - sun) * vec2(ErnstAspect, 1.0));
    float fall = exp(-dsun * 1.6);
    if (front < 0.5 || fall < 0.05) {
        return vec3(0.0);
    }

    const int N = 16;
    vec2 delta = (sun - texCoord) * ErnstRayLen / float(N);
    vec2 uv = texCoord + delta * hash(gl_FragCoord.xy);

    float acc = 0.0;
    float w = 1.0;
    float norm = 0.0;
    for (int i = 0; i < N; i++) {
        uv += delta;
        float d = textureLod(DepthSampler, clamp(uv, vec2(0.001), vec2(0.999)), 0.0).r;
        acc += step(0.9999, d) * w;
        norm += w;
        w *= 0.93;
    }
    acc /= norm;

    float height = clamp(ErnstSunDY * 3.0, 0.0, 1.0);
    vec3 col = mix(vec3(1.0, 0.62, 0.32), vec3(1.0, 0.95, 0.82), height);

    return col * acc * fall * front * ErnstSunLight * ErnstRays;
}

// ===== свечение и лучи от одного источника (факел, лава, портал) =====
vec3 oneLight(vec2 uv, vec2 lp, float ld, float li, vec3 lc) {
    if (li <= 0.0) return vec3(0.0);

    vec2 dv = (lp - uv) * vec2(ErnstAspect, 1.0);
    float fall = exp(-length(dv) * 4.0);
    if (fall < 0.02) return vec3(0.0);

    // источник закрыт стеной или блоком?
    vec2 lpc = clamp(lp, vec2(0.002), vec2(0.998));
    float cd = linDepth(textureLod(DepthSampler, lpc, 0.0).r);
    float vis = clamp((cd - (ld - 2.0)) / 1.5, 0.0, 1.0);
    if (vis <= 0.0) return vec3(0.0);

    // идём к источнику и считаем долю открытых пикселей (дальше источника)
    const int M = 8;
    vec2 delta = (lp - uv) * 0.9 / float(M);
    vec2 p = uv + delta * hash(gl_FragCoord.xy + 17.0);

    float acc = 0.0;
    float w = 1.0;
    float norm = 0.0;
    for (int i = 0; i < M; i++) {
        p += delta;
        float s = linDepth(textureLod(DepthSampler, clamp(p, vec2(0.001), vec2(0.999)), 0.0).r);
        acc += step(ld - 0.75, s) * w;
        norm += w;
        w *= 0.87;
    }
    acc /= norm;

    return lc * (acc * fall * vis * li);
}

void main() {
    vec3 rays = vec3(0.0);

    if (ErnstRays > 0.001) {
        rays += sunRays();
    }

    rays += oneLight(texCoord, vec2(ErnstL0U, ErnstL0V), ErnstL0D, ErnstL0I, vec3(ErnstL0R, ErnstL0G, ErnstL0B));
    rays += oneLight(texCoord, vec2(ErnstL1U, ErnstL1V), ErnstL1D, ErnstL1I, vec3(ErnstL1R, ErnstL1G, ErnstL1B));
    rays += oneLight(texCoord, vec2(ErnstL2U, ErnstL2V), ErnstL2D, ErnstL2I, vec3(ErnstL2R, ErnstL2G, ErnstL2B));
    rays += oneLight(texCoord, vec2(ErnstL3U, ErnstL3V), ErnstL3D, ErnstL3I, vec3(ErnstL3R, ErnstL3G, ErnstL3B));

    vec3 keep = texture(DiffuseSampler, texCoord).rgb * 0.0;
    fragColor = vec4(rays + keep, 1.0);
}
