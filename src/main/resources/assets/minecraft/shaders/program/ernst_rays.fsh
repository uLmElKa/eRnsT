#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform float ErnstRays;
uniform float ErnstRayLen;
uniform float ErnstFwdX, ErnstFwdY, ErnstFwdZ;
uniform float ErnstRightX, ErnstRightY, ErnstRightZ;
uniform float ErnstUpX, ErnstUpY, ErnstUpZ;
uniform float ErnstSunDX, ErnstSunDY, ErnstSunDZ;
uniform float ErnstTanHalf, ErnstAspect, ErnstSunLight;

in vec2 texCoord;
out vec4 fragColor;

const int N = 32;

void main() {
    vec3 F = vec3(ErnstFwdX, ErnstFwdY, ErnstFwdZ);
    vec3 R = vec3(ErnstRightX, ErnstRightY, ErnstRightZ);
    vec3 U = vec3(ErnstUpX, ErnstUpY, ErnstUpZ);
    vec3 S = vec3(ErnstSunDX, ErnstSunDY, ErnstSunDZ);

    // положение солнца на экране
    float sz = dot(S, F);
    float front = step(0.05, sz);
    sz = max(sz, 0.05);
    vec2 sun = vec2(0.5 + 0.5 * dot(S, R) / (sz * ErnstAspect * ErnstTanHalf),
                    0.5 + 0.5 * dot(S, U) / (sz * ErnstTanHalf));

    // идём от пикселя к солнцу и собираем долю открытого неба
    vec2 delta = (sun - texCoord) * ErnstRayLen / float(N);
    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    vec2 uv = texCoord + delta * jitter;

    float acc = 0.0;
    float w = 1.0;
    float norm = 0.0;
    for (int i = 0; i < N; i++) {
        uv += delta;
        float d = textureLod(DepthSampler, clamp(uv, vec2(0.001), vec2(0.999)), 0.0).r;
        acc += step(0.9999, d) * w;
        norm += w;
        w *= 0.965;
    }
    acc /= norm;

    // лучи слабеют с расстоянием от солнца
    float dsun = length((texCoord - sun) * vec2(ErnstAspect, 1.0));
    float fall = exp(-dsun * 1.6);

    // цвет: белый днём, оранжевый у горизонта
    float height = clamp(ErnstSunDY * 3.0, 0.0, 1.0);
    vec3 col = mix(vec3(1.0, 0.62, 0.32), vec3(1.0, 0.95, 0.82), height);

    vec3 rays = col * acc * fall * front * ErnstSunLight * ErnstRays;
    vec3 keep = texture(DiffuseSampler, texCoord).rgb * 0.0;
    fragColor = vec4(rays + keep, 1.0);
}
