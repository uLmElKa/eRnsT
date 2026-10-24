#version 150

#moj_import <fog.glsl>
#moj_import <ernst_shadow_f.glsl>

uniform float ErnstEnabled;

uniform sampler2D Sampler0;
uniform float ErnstShadeTint;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    vec3 soft = textureLod(Sampler0, texCoord0, 2.0).rgb * vertexColor.rgb * ColorModulator.rgb;
    color.rgb = mix(color.rgb, soft, 0.2 * ErnstEnabled);
    vec3 tex = texture(Sampler0, texCoord0).rgb;
    float lum = dot(vertexColor.rgb, vec3(0.299, 0.587, 0.114));
    float depth = clamp(1.0 - lum / 0.85, 0.0, 1.0);
    color.rgb *= mix(vec3(1.0), vec3(0.88, 0.95, 1.10), depth * ErnstShadeTint * ErnstEnabled);
    color.rgb = mix(color.rgb, tex * lum * 1.25, depth * 0.35 * ErnstEnabled);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
