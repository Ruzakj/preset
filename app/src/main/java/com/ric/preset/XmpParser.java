package com.ric.preset;

import org.w3c.dom.*;
import java.io.InputStream;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilderFactory;

public final class XmpParser {
    private static final String[] COLORS={"Red","Orange","Yellow","Green","Aqua","Blue","Purple","Magenta"};

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
        for(int i=0;i<attrs.getLength();i++){Attr a=(Attr)attrs.item(i);String k=a.getLocalName()!=null?a.getLocalName():a.getName();apply(p,k,a.getValue());}
        NodeList nl=e.getChildNodes(); for(int i=0;i<nl.getLength();i++) if(nl.item(i) instanceof Element) walk((Element)nl.item(i),p);
    }

    private static float[][] parseCurve(Document doc,String local){
        NodeList all=doc.getElementsByTagNameNS("*",local); if(all.getLength()==0)return null;
        ArrayList<float[]> pts=new ArrayList<>(); NodeList li=((Element)all.item(0)).getElementsByTagNameNS("*","li");
        for(int i=0;i<li.getLength();i++){String s=li.item(i).getTextContent(); if(s==null)continue; String[] a=s.trim().split(","); if(a.length<2)continue; try{pts.add(new float[]{Float.parseFloat(a[0].trim()),Float.parseFloat(a[1].trim())});}catch(Exception ignored){}}
        return pts.size()<2?null:pts.toArray(new float[pts.size()][]);
    }

    private static void apply(Preset p,String k,String v){
        if(k==null||v==null)return; if(k.equals("Name")&&!v.trim().isEmpty()){p.name=v.trim();return;}
        float x; try{x=Float.parseFloat(v.replace("+","").trim());}catch(Exception ex){return;}
        switch(k){
            case "Exposure2012":p.exposure=x;break; case "Contrast2012":p.contrast=x;break;
            case "Highlights2012":p.highlights=x;break; case "Shadows2012":p.shadows=x;break;
            case "Whites2012":p.whites=x;break; case "Blacks2012":p.blacks=x;break;
            case "Temperature":case "IncrementalTemperature":p.temperature=x;break;
            case "Tint":case "IncrementalTint":p.tint=x;break; case "Vibrance":p.vibrance=x;break; case "Saturation":p.saturation=x;break;
            case "Clarity2012":p.clarity=x;break; case "Texture":p.texture=x;break; case "Dehaze":p.dehaze=x;break;
            case "PostCropVignetteAmount":p.vignette=x;break; case "PostCropVignetteMidpoint":p.vignetteMidpoint=x;break; case "PostCropVignetteFeather":p.vignetteFeather=x;break;
            case "GrainAmount":p.grain=x;break; case "GrainSize":p.grainSize=x;break; case "GrainFrequency":p.grainFrequency=x;break;
            case "ColorGradeHighlightHue":p.highlightHue=x;break; case "ColorGradeHighlightSat":p.highlightSat=x;break;
            case "ColorGradeShadowHue":p.shadowHue=x;break; case "ColorGradeShadowSat":p.shadowSat=x;break;
            case "ColorGradeMidtoneHue":p.midtoneHue=x;break; case "ColorGradeMidtoneSat":p.midtoneSat=x;break;
            case "ColorGradeBlending":p.colorGradeBlending=x;break; case "ColorGradeBalance":p.colorGradeBalance=x;break;
            default: for(int i=0;i<COLORS.length;i++){if(k.equals("HueAdjustment"+COLORS[i]))p.hue[i]=x; else if(k.equals("SaturationAdjustment"+COLORS[i]))p.sat[i]=x; else if(k.equals("LuminanceAdjustment"+COLORS[i]))p.lum[i]=x;}
        }
    }

    private static String cleanName(String n){if(n==null)return"Preset";int slash=Math.max(n.lastIndexOf('/'),n.lastIndexOf('\\'));if(slash>=0)n=n.substring(slash+1);if(n.toLowerCase().endsWith(".xmp"))n=n.substring(0,n.length()-4);return n.replace('-',' ');}
}
