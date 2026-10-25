uniform mat4 ShadowMat;

out vec3 ernstShadowPos;
out vec4 ernstNormalSky;
out float ernstBlockLight;

// pos: положение вершины относительно камеры, n: нормаль, uv2: свет (блок, небо)
void ernstShadowVertex(vec3 pos, vec3 n, ivec2 uv2) {
    vec4 sc = ShadowMat * vec4(pos + n * 0.06, 1.0);
    ernstShadowPos = sc.xyz * 0.5 + 0.5;
    ernstNormalSky = vec4(n, float(uv2.y) / 240.0);
    ernstBlockLight = float(uv2.x) / 240.0;
}
