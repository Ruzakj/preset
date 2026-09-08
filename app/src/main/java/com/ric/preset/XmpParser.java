package com.ric.preset;

import org.w3c.dom.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

public final class XmpParser {
    private static final String[] COLORS={"Red","Orange","Yellow","Green","Aqua","Blue","Purple","Magenta"};
    private static final Pattern NUMBER=Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    public static Preset parse(InputStream in,String fallbackName,String category)throws Exception{
        Preset p=new Preset(); p.name=cleanName(fallbackName); p.category=(category==null||category.isEmpty())?"Preset":category;
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        try{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Exception ignored){}
        Document doc=f.newDocumentBuilder().parse(in); Element root=doc.getDocumentElement(); walk(root,p);
        p.toneCurve=parseCurve(doc,"ToneCurvePV2012"); p.toneCurveRed=parseCurve(doc,"ToneCurvePV2012Red");
        p.toneCurveGreen=parseCurve(doc,"ToneCurvePV2012Green"); p.toneCurveBlue=parseCurve(doc,"ToneCurvePV2012Blue");
        return p;
    }

    private static void walk(Element e,Preset p){
        NamedNodeMap attrs=e.getAttributes();
        for(int i=0;i<attrs.getLength();i++){Attr a=(Attr)attrs.item(i);String k=a.getLocalName()!=null?a.getLocalName():a.getName();applyRaw(p,k,a.getValue());}
        if("Name".equals(e.getLocalName())){String t=e.getTextContent();if(t!=null&&!t.trim().isEmpty())p.name=t.trim();}
        NodeList nl=e.getChildNodes(); for(int i=0;i<nl.getLength();i++) if(nl.item(i) instanceof Element) walk((Element)nl.item(i),p);
    }

    private static float[][] parseCurve(Document doc,String local){
        NodeList all=doc.getElementsByTagNameNS("*",local); if(all.getLength()==0)return null;
        NodeList li=((Element)all.item(0)).getElementsByTagNameNS("*","li");
        ArrayList<Float> nums=new ArrayList<>();
        for(int i=0;i<li.getLength();i++){
            String s=li.item(i).getTextContent(); if(s==null)continue;
            Matcher m=NUMBER.matcher(s); while(m.find())try{nums.add(Float.parseFloat(m.group()));}catch(Exception ignored){}
        }
        if(nums.size()<4)return null;
        ArrayList<float[]> pts=new ArrayList<>();
        for(int i=0;i+1<nums.size();i+=2)pts.add(new float[]{nums.get(i),nums.get(i+1)});
        return pts.size()<2?null:pts.toArray(new float[pts.size()][]);
    }

    private static void applyRaw(Preset p,String k,String v){
        if(k==null||v==null)return;
        String s=v.trim();
        if(k.equals("Name")&&!s.isEmpty()){p.name=s;return;}
        if(k.equals("ProcessVersion")){p.processVersion=s;return;}
        if(k.equals("CameraProfile")){p.cameraProfile=s;return;}
        if(k.equals("ConvertToGrayscale")){p.monochrome=bool(s);return;}
        if(k.equals("RemoveChromaticAberration")){p.removeChromaticAberration=bool(s);return;}
        if(k.equals("LensProfileEnable")||k.equals("EnableProfileCorrections")){p.enableProfileCorrections=bool(s);return;}
        float x; try{x=Float.parseFloat(s.replace("+",""));}catch(Exception ex){return;}
        apply(p,k,x);
    }

    private static boolean bool(String s){return "true".equalsIgnoreCase(s)||"1".equals(s)||"yes".equalsIgnoreCase(s);}

    private static void apply(Preset p,String k,float x){
        switch(k){
            case "Exposure2012":p.exposure=x;break; case "Contrast2012":p.contrast=x;break;
            case "Highlights2012":p.highlights=x;break; case "Shadows2012":p.shadows=x;break;
            case "Whites2012":p.whites=x;break; case "Blacks2012":p.blacks=x;break;
            case "Temperature":case "IncrementalTemperature":p.temperature=x;break;
            case "Tint":case "IncrementalTint":p.tint=x;break; case "Vibrance":p.vibrance=x;break; case "Saturation":p.saturation=x;break;
            case "Clarity2012":p.clarity=x;break; case "Texture":p.texture=x;break; case "Dehaze":p.dehaze=x;break;

            case "ParametricShadows":p.parametricShadows=x;break; case "ParametricDarks":p.parametricDarks=x;break;
            case "ParametricLights":p.parametricLights=x;break; case "ParametricHighlights":p.parametricHighlights=x;break;
            case "ParametricShadowSplit":p.parametricShadowSplit=x;break; case "ParametricMidtoneSplit":p.parametricMidtoneSplit=x;break;
            case "ParametricHighlightSplit":p.parametricHighlightSplit=x;break;

            case "PostCropVignetteAmount":p.vignette=x;break; case "PostCropVignetteMidpoint":p.vignetteMidpoint=x;break;
            case "PostCropVignetteFeather":p.vignetteFeather=x;break; case "PostCropVignetteRoundness":p.vignetteRoundness=x;break;
            case "PostCropVignetteHighlightContrast":p.vignetteHighlights=x;break;
            case "GrainAmount":p.grain=x;break; case "GrainSize":p.grainSize=x;break; case "GrainFrequency":p.grainFrequency=x;break;

            case "ColorGradeHighlightHue":p.highlightHue=x;break; case "ColorGradeHighlightSat":p.highlightSat=x;break; case "ColorGradeHighlightLum":p.highlightLum=x;break;
            case "ColorGradeShadowHue":p.shadowHue=x;break; case "ColorGradeShadowSat":p.shadowSat=x;break; case "ColorGradeShadowLum":p.shadowLum=x;break;
            case "ColorGradeMidtoneHue":p.midtoneHue=x;break; case "ColorGradeMidtoneSat":p.midtoneSat=x;break; case "ColorGradeMidtoneLum":p.midtoneLum=x;break;
            case "ColorGradeGlobalHue":p.globalHue=x;break; case "ColorGradeGlobalSat":p.globalSat=x;break; case "ColorGradeGlobalLum":p.globalLum=x;break;
            case "ColorGradeBlending":p.colorGradeBlending=x;break; case "ColorGradeBalance":p.colorGradeBalance=x;break;

            case "SplitToningShadowHue":p.splitShadowHue=x;break; case "SplitToningShadowSaturation":p.splitShadowSat=x;break;
            case "SplitToningHighlightHue":p.splitHighlightHue=x;break; case "SplitToningHighlightSaturation":p.splitHighlightSat=x;break;
            case "SplitToningBalance":p.splitBalance=x;break;

            case "Sharpness":p.sharpness=x;break; case "SharpenRadius":p.sharpenRadius=x;break; case "SharpenDetail":p.sharpenDetail=x;break; case "SharpenEdgeMasking":p.sharpenMasking=x;break;
            case "LuminanceSmoothing":p.luminanceNoiseReduction=x;break; case "LuminanceNoiseReductionDetail":p.luminanceNoiseDetail=x;break; case "LuminanceNoiseReductionContrast":p.luminanceNoiseContrast=x;break;
            case "ColorNoiseReduction":p.colorNoiseReduction=x;break; case "ColorNoiseReductionDetail":p.colorNoiseDetail=x;break; case "ColorNoiseReductionSmoothness":p.colorNoiseSmoothness=x;break;

            case "RedHue":case "RedPrimaryHue":p.redPrimaryHue=x;break; case "RedSaturation":case "RedPrimarySaturation":p.redPrimarySat=x;break;
            case "GreenHue":case "GreenPrimaryHue":p.greenPrimaryHue=x;break; case "GreenSaturation":case "GreenPrimarySaturation":p.greenPrimarySat=x;break;
            case "BlueHue":case "BluePrimaryHue":p.bluePrimaryHue=x;break; case "BlueSaturation":case "BluePrimarySaturation":p.bluePrimarySat=x;break;
            case "ChromaticAberrationR":p.chromaticAberrationR=x;break; case "ChromaticAberrationB":p.chromaticAberrationB=x;break;

            default:
                for(int i=0;i<COLORS.length;i++){
                    if(k.equals("HueAdjustment"+COLORS[i]))p.hue[i]=x;
                    else if(k.equals("SaturationAdjustment"+COLORS[i]))p.sat[i]=x;
                    else if(k.equals("LuminanceAdjustment"+COLORS[i]))p.lum[i]=x;
                    else if(k.equals("GrayMixer"+COLORS[i]))p.bw[i]=x;
                }
        }
    }

    private static String cleanName(String n){if(n==null)return"Preset";int slash=Math.max(n.lastIndexOf('/'),n.lastIndexOf('\\'));if(slash>=0)n=n.substring(slash+1);if(n.toLowerCase().endsWith(".xmp"))n=n.substring(0,n.length()-4);return n.replace('-',' ');}
}
