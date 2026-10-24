#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform sampler2D RaysSampler;
uniform float ErnstBloom;
uniform float ErnstExposure;
uniform float ErnstShoulder;
uniform float ErnstVignette;
uniform sampler2D DepthSampler;
uniform sampler2D TerrainDepthSampler;
uniform float ErnstEntityBloom;
uniform float ErnstBloomOn;
uniform float ErnstRaysOn;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    vec3 rays = texture(RaysSampler, texCoord).rgb;
    bloom *= ErnstBloomOn;   // выключенный эффект не даёт вклада
    rays *= ErnstRaysOn;

    // на сущностях свечение не накладываем
    float dNow = texture(DepthSampler, texCoord).r;
    float dTer = texture(TerrainDepthSampler, texCoord).r;
    float entity = step(dNow + 0.00003, dTer);
    bloom *= mix(1.0 - entity, 1.0, ErnstEntityBloom);

    // tone mapping: ниже порога без изменений, выше плавно сходит к белому
    float t = clamp(ErnstShoulder, 0.5, 0.98);
    vec3 x = (scene + bloom * ErnstBloom + rays) * ErnstExposure;
    vec3 over = max(x - t, 0.0);
    vec3 outc = min(x, vec3(t)) + (1.0 - t) * (1.0 - exp(-over / (1.0 - t)));

    // виньетка
    float vig = 1.0 - smoothstep(0.25, 0.85, distance(texCoord, vec2(0.5)));
    outc *= mix(1.0, vig, ErnstVignette);

    fragColor = vec4(outc, 1.0);
}
