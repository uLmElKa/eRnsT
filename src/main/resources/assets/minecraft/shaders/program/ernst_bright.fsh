#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float ErnstThreshold;
uniform float ErnstLight;
uniform sampler2D DepthSampler;
uniform sampler2D TerrainDepthSampler;
uniform float ErnstEntityBloom;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 o = 0.5 / InSize;
    vec3 c = 0.25 * (texture(DiffuseSampler, texCoord + vec2(-o.x, -o.y)).rgb
                   + texture(DiffuseSampler, texCoord + vec2( o.x, -o.y)).rgb
                   + texture(DiffuseSampler, texCoord + vec2(-o.x,  o.y)).rgb
                   + texture(DiffuseSampler, texCoord + vec2( o.x,  o.y)).rgb);
    float l = dot(c, vec3(0.299, 0.587, 0.114));

    // тёплые яркие пиксели (факелы, лампы, лава) светятся легче
    float warm = smoothstep(0.12, 0.45, c.r - c.b);
    float thr = mix(ErnstThreshold, 0.55, warm);
    float soft = smoothstep(thr, thr + 0.18, l);
    float boost = 1.0 + warm * ErnstLight;

    // всё, что нарисовано после рельефа (сущности и т.п.), не светится
    float dNow = texture(DepthSampler, texCoord).r;
    float dTer = texture(TerrainDepthSampler, texCoord).r;
    float entity = step(dNow + 0.00003, dTer);
    float keep = mix(1.0 - entity, 1.0, ErnstEntityBloom);

    fragColor = vec4(c * soft * boost * keep, 1.0);
}
