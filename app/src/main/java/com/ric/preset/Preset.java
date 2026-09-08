package com.ric.preset;

public final class Preset {
    public String name = "Preset";
    public String category = "Preset";
    public float exposure, contrast, highlights, shadows, whites, blacks;
    public float temperature, tint, vibrance, saturation, clarity, texture, dehaze;
    public float vignette, vignetteMidpoint=50, vignetteFeather=50, grain, grainSize, grainFrequency=50;
    public float highlightHue, highlightSat, shadowHue, shadowSat, midtoneHue, midtoneSat;
    public float colorGradeBlending=50, colorGradeBalance=0;
    public final float[] hue = new float[8];
    public final float[] sat = new float[8];
    public final float[] lum = new float[8];
    public float[][] toneCurve;
    public float[][] toneCurveRed;
    public float[][] toneCurveGreen;
    public float[][] toneCurveBlue;
}
