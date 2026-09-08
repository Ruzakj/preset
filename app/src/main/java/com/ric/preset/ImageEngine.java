package com.ric.preset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;
import java.util.Random;

public final class ImageEngine {
    private static final float[] HUE_CENTERS={0,30,60,120,180,240,280,320};

    public static Bitmap decode(Context c,Uri uri,int maxSide)throws Exception{
        BitmapFactory.Options b=new BitmapFactory.Options(); b.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,b);}
        int sample=1; while(maxSide>0&&Math.max(b.outWidth/sample,b.outHeight/sample)>maxSide)sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){Bitmap x=BitmapFactory.decodeStream(in,null,o);if(x==null)throw new Exception("Format gambar tidak didukung");return x;}
    }

    public static Bitmap apply(Bitmap src,Preset p,float strength){
        if(p==null||strength<=0)return src.copy(Bitmap.Config.ARGB_8888,false);
        float s=clamp(strength);int w=src.getWidth(),h=src.getHeight(),n=w*h;
        int[] input=new int[n],stage=new int[n],out=new int[n];src.getPixels(input,0,w,0,0,w,h);
        for(int i=0;i<n;i++){
            int c=input[i],a=c>>>24;float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f;
            float[] v=develop(r,g,b,p,s);stage[i]=(a<<24)|(q(v[0])<<16)|(q(v[1])<<8)|q(v[2]);
        }
        Random rnd=new Random(0x524943L);
        for(int i=0;i<n;i++){
            int c=stage[i],a=c>>>24,x=i%w,y=i/w;float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f;
            float lum=luma(r,g,b),near=avgLuma(stage,w,h,x,y,1),wide=avgLuma(stage,w,h,x,y,2);
            float texture=p.texture*s/100f,clarity=p.clarity*s/100f;
            float local=(lum-near)*texture*.55f+(lum-wide)*clarity*.42f;
            local*=1f-smooth(.72f,1f,Math.abs(lum-.5f)*2f);r+=local;g+=local;b+=local;
            if(p.luminanceNoiseReduction>0){float k=p.luminanceNoiseReduction*s/100f*.42f;float d=(wide-lum)*k;r+=d;g+=d;b+=d;}
            if(p.sharpness>0){float edge=lum-near,mask=smooth(p.sharpenMasking/100f*.08f,.14f,Math.abs(edge));float d=edge*p.sharpness*s/100f*.42f*mask;r+=d;g+=d;b+=d;}
            if(p.vignette!=0){float dx=(x-w*.5f)/(w*.5f),dy=(y-h*.5f)/(h*.5f),rad=(float)Math.sqrt(dx*dx+dy*dy);float m=smooth(.35f+.35f*p.vignetteMidpoint/100f,1.15f,rad);float k=p.vignette*s/100f*.55f*m;r+=k*(p.vignette>0?1-r:r);g+=k*(p.vignette>0?1-g:g);b+=k*(p.vignette>0?1-b:b);}
            if(p.grain>0){float noise=(rnd.nextFloat()+rnd.nextFloat()+rnd.nextFloat()-1.5f)/1.5f;float amp=p.grain*s/100f*.055f*(.75f+p.grainSize/200f);float z=noise*amp*(.65f+.35f*(1-Math.abs(luma(r,g,b)-.5f)*2));r+=z;g+=z;b+=z;}
            out[i]=(a<<24)|(q(soft(r))<<16)|(q(soft(g))<<8)|q(soft(b));
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);result.setPixels(out,0,w,0,0,w,h);return result;
    }

    private static float[] develop(float r,float g,float b,Preset p,float s){
        // Work in linear light for exposure/WB, then return to perceptual RGB for ACR-like controls.
        r=toLinear(r);g=toLinear(g);b=toLinear(b);float ex=(float)Math.pow(2,p.exposure*s);r*=ex;g*=ex;b*=ex;
        float temp=tempShift(p.temperature)*s,tint=clampSigned(p.tint/150f)*s;
        r*=1+temp*.105f+tint*.018f;g*=1-tint*.045f;b*=1-temp*.125f+tint*.012f;
        r=toSrgb(r);g=toSrgb(g);b=toSrgb(b);

        float y=luma(r,g,b),t=tone(y,p,s),scale=y>.0001f?t/y:1; // preserve highlight chroma better than additive RGB tone shifts
        r*=scale;g*=scale;b*=scale;
        float cont=p.contrast*s/100f;r=contrast(r,cont);g=contrast(g,cont);b=contrast(b,cont);

        r=curve(r,p.toneCurve,s);g=curve(g,p.toneCurve,s);b=curve(b,p.toneCurve,s);
        r=curve(r,p.toneCurveRed,s);g=curve(g,p.toneCurveGreen,s);b=curve(b,p.toneCurveBlue,s);

        float[] hsv=rgbToHsv(soft(r),soft(g),soft(b));float[] hsl=hsl(hsv[0],p,s);
        hsv[0]=(hsv[0]+hsl[0]+360)%360;float globalSat=p.saturation*s/100f;
        float vib=p.vibrance*s/100f*(1-hsv[1])*.78f;hsv[1]=clamp(hsv[1]*(1+globalSat)+vib+hsl[1]);hsv[2]=clamp(hsv[2]+hsl[2]);
        float[] rgb=hsvToRgb(hsv[0],hsv[1],hsv[2]);r=rgb[0];g=rgb[1];b=rgb[2];

        rgb=calibrate(r,g,b,p,s);r=rgb[0];g=rgb[1];b=rgb[2];
        if(p.monochrome){float gray=luma(r,g,b);r=g=b=gray;}
        rgb=grade(r,g,b,p,s);r=rgb[0];g=rgb[1];b=rgb[2];
        float deh=p.dehaze*s/100f;if(deh!=0){float yy=luma(r,g,b),k=1+deh*.34f;r=(r-yy)*k+yy;g=(g-yy)*k+yy;b=(b-yy)*k+yy;float lift=-deh*.025f;r+=lift;g+=lift;b+=lift;}
        return gamut(r,g,b);
    }

    private static float tone(float y,Preset p,float s){
        y=clamp(y);float sh=1-smooth(.18f,.66f,y),hi=smooth(.34f,.92f,y),wh=smooth(.66f,.98f,y),bl=1-smooth(.02f,.34f,y);
        float v=y;
        v+=p.shadows*s/100f*sh*.29f;v+=p.highlights*s/100f*hi*.25f;v+=p.whites*s/100f*wh*.17f;v+=p.blacks*s/100f*bl*.16f;
        // Process Version 2012-style shoulder/toe: negative highlights recover instead of simply darkening whites.
        if(p.highlights<0){float recover=-p.highlights*s/100f;v-=recover*smooth(.62f,1f,y)*(y-.62f)*.38f;}
        if(p.shadows>0){float open=p.shadows*s/100f;v+=open*(.5f-y)*smooth(.03f,.45f,.5f-y)*.08f;}
        float s1=p.parametricShadowSplit/100f,s2=p.parametricMidtoneSplit/100f,s3=p.parametricHighlightSplit/100f;
        float ps=(1-smooth(s1*.55f,s1,y))*p.parametricShadows;float pd=smooth(s1*.5f,s1,y)*(1-smooth(s1,s2,y))*p.parametricDarks;
        float pl=smooth(s1,s2,y)*(1-smooth(s2,s3,y))*p.parametricLights;float ph=smooth(s2,s3,y)*p.parametricHighlights;
        v+=(ps+pd+pl+ph)*s/100f*.14f;return clamp(v);
    }

    private static float[] hsl(float hue,Preset p,float s){float hh=0,ss=0,ll=0,sum=0;for(int i=0;i<8;i++){float d=hueDist(hue,HUE_CENTERS[i]),w=1-d/48f;if(w>0){w=w*w*(3-2*w);sum+=w;hh+=p.hue[i]*w;ss+=p.sat[i]*w;ll+=p.lum[i]*w;}}if(sum>0){hh/=sum;ss/=sum;ll/=sum;}return new float[]{hh*s*.34f,ss*s/100f*.82f,ll*s/100f*.28f};}

    private static float[] calibrate(float r,float g,float b,Preset p,float s){float[] h=rgbToHsv(r,g,b);float total=Math.max(.001f,r+g+b),rw=r/total,gw=g/total,bw=b/total;h[0]=(h[0]+(p.redPrimaryHue*rw+p.greenPrimaryHue*gw+p.bluePrimaryHue*bw)*s*.22f+360)%360;h[1]=clamp(h[1]*(1+(p.redPrimarySat*rw+p.greenPrimarySat*gw+p.bluePrimarySat*bw)*s/100f*.5f));return hsvToRgb(h[0],h[1],h[2]);}

    private static float[] grade(float r,float g,float b,Preset p,float s){float y=luma(r,g,b),bal=p.colorGradeBalance*s/100f;float sw=1-smooth(.12f,.58f+bal*.15f,y),hw=smooth(.42f+bal*.15f,.9f,y),mw=clamp(1-Math.max(sw,hw));float blend=.20f+.32f*p.colorGradeBlending/100f;float[] z;
        if(p.shadowSat!=0){z=tint(r,g,b,p.shadowHue,p.shadowSat,sw*Math.abs(p.shadowSat)*s/100f*blend);r=z[0];g=z[1];b=z[2];}
        if(p.midtoneSat!=0){z=tint(r,g,b,p.midtoneHue,p.midtoneSat,mw*Math.abs(p.midtoneSat)*s/100f*blend);r=z[0];g=z[1];b=z[2];}
        if(p.highlightSat!=0){z=tint(r,g,b,p.highlightHue,p.highlightSat,hw*Math.abs(p.highlightSat)*s/100f*blend);r=z[0];g=z[1];b=z[2];}
        if(p.globalSat!=0){z=tint(r,g,b,p.globalHue,p.globalSat,Math.abs(p.globalSat)*s/100f*.22f);r=z[0];g=z[1];b=z[2];}
        if(p.shadowSat==0&&p.highlightSat==0&&(p.splitShadowSat!=0||p.splitHighlightSat!=0)){if(p.splitShadowSat!=0){z=tint(r,g,b,p.splitShadowHue,p.splitShadowSat,sw*p.splitShadowSat*s/100f*.25f);r=z[0];g=z[1];b=z[2];}if(p.splitHighlightSat!=0){z=tint(r,g,b,p.splitHighlightHue,p.splitHighlightSat,hw*p.splitHighlightSat*s/100f*.25f);r=z[0];g=z[1];b=z[2];}}
        float dl=(p.shadowLum*sw+p.midtoneLum*mw+p.highlightLum*hw+p.globalLum)*s/100f*.12f;return gamut(r+dl,g+dl,b+dl);
    }

    private static float[] tint(float r,float g,float b,float hue,float sat,float amount){float[] col=hsvToRgb(hue,Math.min(.8f,.22f+Math.abs(sat)/125f),1);float cy=luma(col[0],col[1],col[2]),sign=sat<0?-1:1;float y=luma(r,g,b);return gamut(r+(col[0]-cy)*amount*sign*(.55f+.45f*y),g+(col[1]-cy)*amount*sign*(.55f+.45f*y),b+(col[2]-cy)*amount*sign*(.55f+.45f*y));}

    private static float curve(float v,float[][] p,float s){if(p==null||p.length<2)return soft(v);float x=clamp(v)*255;if(x<=p[0][0])return mix(v,p[0][1]/255f,s);if(x>=p[p.length-1][0])return mix(v,p[p.length-1][1]/255f,s);int j=1;while(j<p.length&&x>p[j][0])j++;float x0=p[j-1][0],x1=p[j][0],t=(x-x0)/Math.max(.001f,x1-x0);t=t*t*(3-2*t);float yy=p[j-1][1]+(p[j][1]-p[j-1][1])*t;return clamp(mix(v,yy/255f,s));}
    private static float contrast(float v,float a){float x=v-.5f,k=1+a*.82f;return .5f+x*k/(1+Math.abs(x)*Math.max(0,a)*.32f);}
    private static float tempShift(float v){if(Math.abs(v)>1000){float m=1000000f/Math.max(2000,Math.min(50000,v));return clampSigned((181.8f-m)/145f);}return clampSigned(v/100f);}
    private static float[] gamut(float r,float g,float b){float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));if(max>1){float y=luma(r,g,b),k=(1-y)/Math.max(.0001f,max-y);k=clamp(k);r=y+(r-y)*k;g=y+(g-y)*k;b=y+(b-y)*k;}if(min<0){float y=luma(r,g,b),k=y/Math.max(.0001f,y-min);k=clamp(k);r=y+(r-y)*k;g=y+(g-y)*k;b=y+(b-y)*k;}return new float[]{clamp(r),clamp(g),clamp(b)};}
    private static float soft(float v){if(v<=0)return 0;if(v<=1)return v;return 1-(float)Math.exp(-(v-1)*2.5f)*.015f;}
    private static float toLinear(float v){return v<=.04045f?v/12.92f:(float)Math.pow((v+.055f)/1.055f,2.4);}
    private static float toSrgb(float v){v=Math.max(0,v);return v<=.0031308f?v*12.92f:1.055f*(float)Math.pow(v,1/2.4)-.055f;}
    private static float avgLuma(int[] px,int w,int h,int x,int y,int rad){float sum=0;int count=0;for(int yy=Math.max(0,y-rad);yy<=Math.min(h-1,y+rad);yy++)for(int xx=Math.max(0,x-rad);xx<=Math.min(w-1,x+rad);xx++){if(xx==x&&yy==y)continue;int c=px[yy*w+xx];sum+=luma(((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f);count++;}return count==0?0:sum/count;}
    private static float hueDist(float a,float b){float d=Math.abs(a-b);return Math.min(d,360-d);}
    private static float smooth(float a,float b,float x){if(a==b)return x<a?0:1;float t=clamp((x-a)/(b-a));return t*t*(3-2*t);}
    private static float luma(float r,float g,float b){return .2126f*r+.7152f*g+.0722f*b;}
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}private static float clampSigned(float v){return Math.max(-1,Math.min(1,v));}private static float mix(float a,float b,float t){return a+(b-a)*clamp(t);}private static int q(float v){return Math.round(clamp(v)*255);}
    private static float[] rgbToHsv(float r,float g,float b){float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0;if(d!=0){if(max==r)h=60*(((g-b)/d)%6);else if(max==g)h=60*(((b-r)/d)+2);else h=60*(((r-g)/d)+4);}if(h<0)h+=360;return new float[]{h,max==0?0:d/max,max};}
    private static float[] hsvToRgb(float h,float s,float v){h=((h%360)+360)%360;float c=v*s,x=c*(1-Math.abs((h/60)%2-1)),m=v-c,r=0,g=0,b=0;if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else{r=c;b=x;}return new float[]{r+m,g+m,b+m};}
}
