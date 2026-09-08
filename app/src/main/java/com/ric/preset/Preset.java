package com.ric.preset;

public final class Preset {
    public String name = "Preset";
    public String category = "Preset";
    public float exposure, contrast, highlights, shadows, whites, blacks;
    public float temperature, tint, vibrance, saturation, clarity, dehaze;
    public float vignette, grain, grainSize;
    public float highlightHue, highlightSat, shadowHue, shadowSat;
    public final float[] hue = new float[8];
    public final float[] sat = new float[8];
    public final float[] lum = new float[8];
}
