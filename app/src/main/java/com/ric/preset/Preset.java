package com.ric.preset;

public final class Preset {
    public String name = "Preset";
    public String category = "Preset";
    public String processVersion = "";
    public String cameraProfile = "";

    public float exposure, contrast, highlights, shadows, whites, blacks;
    public float temperature, tint, vibrance, saturation, clarity, texture, dehaze;

    public float parametricShadows, parametricDarks, parametricLights, parametricHighlights;
    public float parametricShadowSplit=25, parametricMidtoneSplit=50, parametricHighlightSplit=75;

    public float vignette, vignetteMidpoint=50, vignetteFeather=50, vignetteRoundness=0, vignetteHighlights=0;
    public float grain, grainSize=25, grainFrequency=50;

    public float highlightHue, highlightSat, highlightLum;
    public float shadowHue, shadowSat, shadowLum;
    public float midtoneHue, midtoneSat, midtoneLum;
    public float globalHue, globalSat, globalLum;
    public float colorGradeBlending=50, colorGradeBalance=0;

    public float splitShadowHue, splitShadowSat, splitHighlightHue, splitHighlightSat, splitBalance;

    public float sharpness, sharpenRadius=1f, sharpenDetail=25, sharpenMasking;
    public float luminanceNoiseReduction, luminanceNoiseDetail=50, luminanceNoiseContrast=0;
    public float colorNoiseReduction=25, colorNoiseDetail=50, colorNoiseSmoothness=50;

    public float redPrimaryHue, redPrimarySat, greenPrimaryHue, greenPrimarySat, bluePrimaryHue, bluePrimarySat;
    public float chromaticAberrationR, chromaticAberrationB;

    public boolean monochrome;
    public boolean removeChromaticAberration;
    public boolean enableProfileCorrections;

    public final float[] hue = new float[8];
    public final float[] sat = new float[8];
    public final float[] lum = new float[8];
    public final float[] bw = new float[8];

    public float[][] toneCurve;
    public float[][] toneCurveRed;
    public float[][] toneCurveGreen;
    public float[][] toneCurveBlue;
}
