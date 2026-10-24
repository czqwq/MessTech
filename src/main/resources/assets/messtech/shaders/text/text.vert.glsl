#version 120

varying vec2 uv;

// Mask quad in the current model-view space (GUI pixels for HUD text); the fragment stage needs the mask coordinate.
void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    uv = gl_MultiTexCoord0.xy;
}
