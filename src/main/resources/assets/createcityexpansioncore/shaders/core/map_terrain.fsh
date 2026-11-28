#version 150

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 position = texCoord0 * vec2(textureSize(Sampler0, 0));
    ivec2 slotOrigin = ivec2(position) / 34 * 34;
    ivec2 nearest = clamp(ivec2(floor(position)), slotOrigin + 1, slotOrigin + 32);
    vec4 cell = texelFetch(Sampler0, nearest, 0);
    // A partial overview texel cannot prove which of its finer cells have been surveyed.
    if (cell.a == 0.0 || (vertexColor.g > 0.5 && cell.a < 1.0)) discard;
    vec3 rgb = cell.rgb;
    if (vertexColor.r > 0.5) {
        // The gutter holds the true world neighbour, or replicates this edge until it arrives.
        vec2 samplePosition = position - 0.5;
        ivec2 origin = ivec2(floor(samplePosition));
        vec2 f = fract(samplePosition);
        vec4 sum = vec4(0.0);
        for (int y = 0; y < 2; ++y) for (int x = 0; x < 2; ++x) {
            vec4 p = texelFetch(Sampler0, origin + ivec2(x,y), 0);
            float weight = (x == 0 ? 1.0-f.x : f.x) * (y == 0 ? 1.0-f.y : f.y) * p.a;
            sum += vec4(p.rgb, 1.0) * weight;
        }
        if (sum.a > 0.0) rgb = sum.rgb / sum.a;
    }
    fragColor = vec4(rgb, cell.a * vertexColor.a);
}
