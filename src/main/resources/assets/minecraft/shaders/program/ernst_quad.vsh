#version 150

in vec4 Position;

uniform vec2 OutSize;

out vec2 texCoord;

// Рисует экран на цель любого размера, не завися от размера окна
void main() {
    gl_Position = vec4(Position.xy / OutSize * 2.0 - 1.0, 0.2, 1.0);
    texCoord = Position.xy / OutSize;
}
