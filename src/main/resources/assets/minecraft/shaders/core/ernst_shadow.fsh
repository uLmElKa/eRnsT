#version 150

uniform sampler2D Sampler0;
uniform float ErnstShadowMaxY;

in vec2 texCoord0;
in float relY;
out vec4 fragColor;

void main() {
    // слишком высоко над игроком (например, потолок Ада): тень не отбрасываем
    if (relY > ErnstShadowMaxY) {
        discard;
    }

    // прозрачные части (листва, стекло) тень не отбрасывают
    if (texture(Sampler0, texCoord0).a < 0.5) {
        discard;
    }
    fragColor = vec4(1.0);
}
