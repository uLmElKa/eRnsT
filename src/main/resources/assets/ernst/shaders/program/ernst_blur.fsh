#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform vec2 BlurDir;
uniform float ErnstBlurScale;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 st = BlurDir * ErnstBlurScale / OutSize;
    float w[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

    vec3 sum = texture(DiffuseSampler, texCoord).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        sum += texture(DiffuseSampler, texCoord + st * float(i)).rgb * w[i];
        sum += texture(DiffuseSampler, texCoord - st * float(i)).rgb * w[i];
    }
    fragColor = vec4(sum, 1.0);
}
