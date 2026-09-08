package com.ric.preset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;
import java.util.Random;

public final class ImageEngine {
    private static final float[] HSL_CENTERS={0f,30f,60f,120f,180f,240f,280f,320f};

    public static Bitmap decode(Context c,Uri uri,int maxSide)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        int sample=1;if(maxSide>0)while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>maxSide)sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=Math.max(1,sample);o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in,null,o);if(b==null)throw new Exception("Format gambar tidak didukung");return b;}
    }

    public static Bitmap apply(Bitmap src,Preset p,float strength){
        if(p==null||strength<=0f)return src.copy(Bitmap.Config.ARGB_8888,false);
        strength=clamp(strength);
        int w=src.getWidth(),h=src.getHeight(),n=w*h;
        int[] original=new int[n],base=new int[n],detailStage=new int[n],out=new int[n];
        src.getPixels(original,0,w,0,0,w,h);

        float exp=(float)Math.pow(2.0,p.exposure*strength);
        float cont=p.contrast*strength/100f;
        float satAll=1f+p.saturation*strength/100f;
        float vibr=p.vibrance*strength/100f;
        float temp=temperatureShift(p.temperature)*strength;
        float tint=tintShift(p.tint)*strength;

        for(int i=0;i<n;i++){
            int c=original[i],a=(c>>>24)&255;
            float r=((c>>>16)&255)/255f,g=((c>>>8)&255)/255f,b=(c&255)/255f;

            r*=exp;g*=exp;b*=exp;
            float y=luma(r,g,b),target=tone(clamp(y),p,strength);float d=target-y;r+=d;g+=d;b+=d;
            r=contrast(r,cont);g=contrast(g,cont);b=contrast(b,cont);

            float[] wb=whiteBalance(r,g,b,temp,tint);r=wb[0];g=wb[1];b=wb[2];
            float[] cal=calibration(r,g,b,p,strength);r=cal[0];g=cal[1];b=cal[2];

            r=curve(r,p.toneCurve,strength);g=curve(g,p.toneCurve,strength);b=curve(b,p.toneCurve,strength);
            r=curve(r,p.toneCurveRed,strength);g=curve(g,p.toneCurveGreen,strength);b=curve(b,p.toneCurveBlue,strength);

            float[] hsv=rgbToHsv(clamp(r),clamp(g),clamp(b));
            float[] adj=hslAdjustment(hsv[0],p,strength);
            hsv[0]=(hsv[0]+adj[0]+360f)%360f;
            float vibBoost=vibr*(1f-hsv[1])*(0.72f+0.28f*(1f-hsv[1]));
            hsv[1]=clamp(hsv[1]*satAll+vibBoost+adj[1]);
            hsv[2]=clamp(hsv[2]+adj[2]);
            float[] rgb=hsvToRgb(hsv[0],hsv[1],hsv[2]);r=rgb[0];g=rgb[1];b=rgb[2];

            if(p.monochrome){float gray=blackWhiteMix(r,g,b,p,strength);r=gray;g=gray;b=gray;}

            y=luma(r,g,b);
            rgb=applyColorGrading(r,g,b,y,p,strength);r=rgb[0];g=rgb[1];b=rgb[2];

            float deh=p.dehaze*strength/100f;
            if(deh!=0){float yy=luma(r,g,b),k=1f+deh*.52f;r=(r-yy)*k+yy;g=(g-yy)*k+yy;b=(b-yy)*k+yy;float cc=deh*.12f;r=contrast(r,cc);g=contrast(g,cc);b=contrast(b,cc);float lift=-deh*.035f;r+=lift;g+=lift;b+=lift;}

            base[i]=(a<<24)|(to8(r)<<16)|(to8(g)<<8)|to8(b);
        }

        float clarity=p.clarity*strength/100f,texture=p.texture*strength/100f;
        float sharp=p.sharpness*strength/100f;
        float lnr=p.luminanceNoiseReduction*strength/100f;
        float cnr=p.colorNoiseReduction*strength/100f;

        for(int i=0;i<n;i++){
            int c=base[i],a=(c>>>24)&255,x=i%w,y=i/w;
            float r=((c>>>16)&255)/255f,g=((c>>>8)&255)/255f,b=(c&255)/255f;
            float yc=luma(r,g,b);
            float near=neighborLuma(base,w,h,x,y,1);
            float wide=neighborLuma(base,w,h,x,y,2);

            if(clarity!=0||texture!=0){
                float detail=(yc-near)*texture*.82f+(yc-wide)*clarity*.62f;
                float mask=clamp(1f-Math.abs(yc-.5f)*1.10f);
                detail*=mask;r+=detail;g+=detail;b+=detail;
            }

            if(lnr>0){
                float nrMask=clamp(lnr*(.30f+.70f*(1f-yc)));
                float targetY=mix(yc,wide,nrMask*.62f);
                float delta=targetY-yc;r+=delta;g+=delta;b+=delta;
            }

            if(cnr>0){
                float[] avg=neighborRgb(base,w,h,x,y,1);
                float yy=luma(r,g,b),ay=luma(avg[0],avg[1],avg[2]);
                float cr=r-yy,cg=g-yy,cb=b-yy;
                float acr=avg[0]-ay,acg=avg[1]-ay,acb=avg[2]-ay;
                float amt=clamp(cnr*.55f*(1f+p.colorNoiseDetail/200f));
                r=yy+mix(cr,acr,amt);g=yy+mix(cg,acg,amt);b=yy+mix(cb,acb,amt);
            }

            if(sharp>0){
                float edge=Math.abs(yc-near);
                float threshold=clamp(p.sharpenMasking/100f)*.08f;
                if(edge>threshold){
                    float amount=sharp*(.45f+.55f*clamp(p.sharpenDetail/100f));
                    float delta=(yc-near)*amount*.78f;r+=delta;g+=delta;b+=delta;
                }
            }

            detailStage[i]=(a<<24)|(to8(r)<<16)|(to8(g)<<8)|to8(b);
        }

        Random rnd=new Random(0x4C52434CL);
        for(int i=0;i<n;i++){
            int c=detailStage[i],a=(c>>>24)&255,x=i%w,y=i/w;
            float r=((c>>>16)&255)/255f,g=((c>>>8)&255)/255f,b=(c&255)/255f;

            if(p.removeChromaticAberration||p.chromaticAberrationR!=0||p.chromaticAberrationB!=0){
                float dx=(x-w*.5f)/(w*.5f),dy=(y-h*.5f)/(h*.5f),rad=(float)Math.sqrt(dx*dx+dy*dy);
                if(rad>.35f){
                    int sx=Math.max(0,Math.min(w-1,x+(dx>0?1:-1))),sy=Math.max(0,Math.min(h-1,y+(dy>0?1:-1)));
                    int nc=detailStage[sy*w+sx];float nr=((nc>>>16)&255)/255f,nb=(nc&255)/255f;
                    float amt=(p.removeChromaticAberration?.32f:.14f)+Math.min(.25f,(Math.abs(p.chromaticAberrationR)+Math.abs(p.chromaticAberrationB))/40f);
                    r=mix(r,nr,amt*rad);b=mix(b,nb,amt*rad);
                }
            }

            if(p.vignette!=0){
                float dx=(x-w*.5f)/(w*.5f),dy=(y-h*.5f)/(h*.5f);
                float round=clampSigned(p.vignetteRoundness/100f);
                float ax=Math.abs(dx),ay=Math.abs(dy);
                float radius=round>=0?(float)Math.sqrt(dx*dx+dy*dy):mix((float)Math.sqrt(dx*dx+dy*dy),Math.max(ax,ay),-round);
                float mid=.22f+.58f*(1f-clamp(p.vignetteMidpoint/100f));
                float feather=.06f+.78f*clamp(p.vignetteFeather/100f);
                float m=smooth(mid,Math.min(1.5f,mid+feather),radius);
                float v=p.vignette*strength/100f;
                if(v<0){
                    float lum=luma(r,g,b),protect=smooth(.65f,1f,lum)*clamp(p.vignetteHighlights/100f);
                    float gain=1f+v*m*.76f*(1f-protect*.65f);r*=gain;g*=gain;b*=gain;
                }else{
                    float add=v*m*.48f;r+=add*(1-r);g+=add*(1-g);b+=add*(1-b);
                }
            }

            if(p.grain>0){
                float size=.65f+clamp(p.grainSize/100f)*1.7f;
                float freq=.65f+clamp(p.grainFrequency/100f)*.75f;
                float noise=(rnd.nextFloat()+rnd.nextFloat()+rnd.nextFloat()-1.5f)/1.5f;
                float amp=p.grain*strength/100f*.10f*size*freq;
                float midMask=.52f+.48f*(1f-Math.abs(luma(r,g,b)-.5f)*1.55f);
                float q=noise*amp*clamp(midMask);r+=q;g+=q;b+=q;
            }

            out[i]=(a<<24)|(to8(r)<<16)|(to8(g)<<8)|to8(b);
        }

        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);result.setPixels(out,0,w,0,0,w,h);return result;
    }

    private static float tone(float y,Preset p,float s){
        float shadowW=(1f-y)*(1f-y),highW=y*y,mid=4f*y*(1f-y),v=y;
        v+=p.shadows*s/100f*shadowW*.40f;
        v+=p.highlights*s/100f*highW*.34f;
        float whiteW=smooth(.58f,1f,y),blackW=1f-smooth(0f,.42f,y);
        v+=p.whites*s/100f*whiteW*.26f;
        v+=p.blacks*s/100f*blackW*.24f;
        if(p.highlights<0)v-=(-p.highlights*s/100f)*highW*mid*.07f;
        if(p.shadows<0)v-=(-p.shadows*s/100f)*shadowW*mid*.07f;
        v=parametric(v,p,s);
        return clamp(v);
    }

    private static float parametric(float y,Preset p,float s){
        float s1=clamp(p.parametricShadowSplit/100f),s2=clamp(p.parametricMidtoneSplit/100f),s3=clamp(p.parametricHighlightSplit/100f);
        float ws=1f-smooth(s1*.5f,Math.max(.02f,s1),y);
        float wd=smooth(s1*.35f,s1,y)*(1f-smooth(s1,Math.max(s1+.02f,s2),y));
        float wl=smooth(s1,s2,y)*(1f-smooth(s2,Math.max(s2+.02f,s3),y));
        float wh=smooth(s2,s3,y);
        float delta=(p.parametricShadows*ws+p.parametricDarks*wd+p.parametricLights*wl+p.parametricHighlights*wh)*s/100f*.22f;
        return clamp(y+delta);
    }

    private static float[] calibration(float r,float g,float b,Preset p,float s){
        float total=Math.max(.001f,r+g+b),rw=r/total,gw=g/total,bw=b/total;
        float[] hsv=rgbToHsv(clamp(r),clamp(g),clamp(b));
        float hueShift=(p.redPrimaryHue*rw+p.greenPrimaryHue*gw+p.bluePrimaryHue*bw)*s*.18f;
        float satShift=(p.redPrimarySat*rw+p.greenPrimarySat*gw+p.bluePrimarySat*bw)*s/100f*.45f;
        hsv[0]=(hsv[0]+hueShift+360f)%360f;hsv[1]=clamp(hsv[1]*(1f+satShift));
        return hsvToRgb(hsv[0],hsv[1],hsv[2]);
    }

    private static float[] applyColorGrading(float r,float g,float b,float y,Preset p,float s){
        float balance=p.colorGradeBalance*s/100f;
        float sw=1f-smooth(.10f,.58f+balance*.20f,y);
        float hw=smooth(.42f+balance*.20f,.92f,y);
        float mw=clamp(1f-Math.max(sw,hw));
        float blend=.18f+.38f*clamp(p.colorGradeBlending/100f);
        boolean modern=p.shadowSat!=0||p.midtoneSat!=0||p.highlightSat!=0||p.globalSat!=0;
        float[] rgb;
        if(modern){
            if(p.shadowSat!=0){float t=sw*Math.abs(p.shadowSat)*s/100f*blend;rgb=tintWithHue(r,g,b,p.shadowHue,p.shadowSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
            if(p.midtoneSat!=0){float t=mw*Math.abs(p.midtoneSat)*s/100f*blend;rgb=tintWithHue(r,g,b,p.midtoneHue,p.midtoneSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
            if(p.highlightSat!=0){float t=hw*Math.abs(p.highlightSat)*s/100f*blend;rgb=tintWithHue(r,g,b,p.highlightHue,p.highlightSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
            if(p.globalSat!=0){float t=Math.abs(p.globalSat)*s/100f*.28f;rgb=tintWithHue(r,g,b,p.globalHue,p.globalSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
            float lum=(p.shadowLum*sw+p.midtoneLum*mw+p.highlightLum*hw+p.globalLum)*s/100f*.18f;r+=lum;g+=lum;b+=lum;
        }else if(p.splitShadowSat!=0||p.splitHighlightSat!=0){
            float sb=p.splitBalance*s/100f;
            float ssw=1f-smooth(.08f,.55f+sb*.2f,y),hhw=smooth(.45f+sb*.2f,.95f,y);
            if(p.splitShadowSat!=0){float t=ssw*p.splitShadowSat*s/100f*.28f;rgb=tintWithHue(r,g,b,p.splitShadowHue,p.splitShadowSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
            if(p.splitHighlightSat!=0){float t=hhw*p.splitHighlightSat*s/100f*.28f;rgb=tintWithHue(r,g,b,p.splitHighlightHue,p.splitHighlightSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
        }
        return new float[]{clamp(r),clamp(g),clamp(b)};
    }

    private static float blackWhiteMix(float r,float g,float b,Preset p,float s){
        float[] hsv=rgbToHsv(r,g,b);float[] adj=hslAdjustment(hsv[0],p,s);
        float weighted=.299f*r+.587f*g+.114f*b;
        float bwAdj=0,sum=0;
        for(int i=0;i<8;i++){
            float d=hueDistance(hsv[0],HSL_CENTERS[i]),ww=clamp(1f-d/50f);ww=ww*ww*(3f-2f*ww);bwAdj+=p.bw[i]*ww;sum+=ww;
        }
        if(sum>0)weighted+=bwAdj/sum*s/100f*.35f;
        weighted+=adj[2]*.25f;
        return clamp(weighted);
    }

    private static float contrast(float v,float amount){float k=1f+amount*1.20f;float x=(v-.5f)*k;return clamp(.5f+x/(1f+Math.abs(x)*.30f));}
    private static float[] whiteBalance(float r,float g,float b,float temp,float tint){float rg=1f+temp*.13f+tint*.025f,gg=1f-tint*.058f,bg=1f-temp*.15f+tint*.018f;return new float[]{clamp(r*rg),clamp(g*gg),clamp(b*bg)};}
    private static float temperatureShift(float v){if(Math.abs(v)>1000f){float mired=1000000f/Math.max(2000f,Math.min(50000f,v));float neutral=1000000f/5500f;return clampSigned((neutral-mired)/150f);}return clampSigned(v/100f);}
    private static float tintShift(float v){return clampSigned(v/150f);}

    private static float curve(float v,float[][] pts,float s){
        if(pts==null||pts.length<2)return clamp(v);
        float x=clamp(v)*255f,y=sampleCurve(x,pts);
        return clamp(mix(v,y/255f,s));
    }

    private static float sampleCurve(float x,float[][] p){
        if(x<=p[0][0])return p[0][1];if(x>=p[p.length-1][0])return p[p.length-1][1];
        int i=1;while(i<p.length&&x>p[i][0])i++;
        int i0=Math.max(0,i-2),i1=i-1,i2=i,i3=Math.min(p.length-1,i+1);
        float x1=p[i1][0],x2=p[i2][0],t=(x-x1)/Math.max(.001f,x2-x1);
        float y0=p[i0][1],y1=p[i1][1],y2=p[i2][1],y3=p[i3][1];
        float m1=(y2-y0)*.5f,m2=(y3-y1)*.5f,t2=t*t,t3=t2*t;
        float y=(2*t3-3*t2+1)*y1+(t3-2*t2+t)*m1+(-2*t3+3*t2)*y2+(t3-t2)*m2;
        float lo=Math.min(y1,y2),hi=Math.max(y1,y2);return Math.max(lo,Math.min(hi,y));
    }

    private static float[] hslAdjustment(float hue,Preset p,float s){
        float ah=0,as=0,al=0,sum=0;
        for(int i=0;i<8;i++){
            float d=hueDistance(hue,HSL_CENTERS[i]);float width=(i==0||i==7)?54f:50f;
            float w=clamp(1f-d/width);w=w*w*(3f-2f*w);
            if(w>0){sum+=w;ah+=p.hue[i]*w;as+=p.sat[i]*w;al+=p.lum[i]*w;}
        }
        if(sum>0){ah/=sum;as/=sum;al/=sum;}
        return new float[]{ah*s*.30f,as*s/100f*.66f,al*s/100f*.35f};
    }

    private static float hueDistance(float a,float b){float d=Math.abs(a-b);return Math.min(d,360f-d);}
    private static float[] tintWithHue(float r,float g,float b,float hue,float signedSat,float amount){
        float[] color=hsvToRgb(hue,Math.min(1f,Math.abs(signedSat)/100f+.25f),1f);
        float cy=luma(color[0],color[1],color[2]);float cr=color[0]-cy,cg=color[1]-cy,cb=color[2]-cy;
        float y=luma(r,g,b),sign=signedSat<0?-1f:1f;
        return new float[]{clamp(r+cr*amount*sign*(.5f+.5f*y)),clamp(g+cg*amount*sign*(.5f+.5f*y)),clamp(b+cb*amount*sign*(.5f+.5f*y))};
    }

    private static float neighborLuma(int[] px,int w,int h,int x,int y,int radius){
        int x0=Math.max(0,x-radius),x1=Math.min(w-1,x+radius),y0=Math.max(0,y-radius),y1=Math.min(h-1,y+radius);float sum=0;int count=0;
        for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){if(xx==x&&yy==y)continue;int c=px[yy*w+xx];sum+=luma(((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f);count++;}
        return count==0?0:sum/count;
    }

    private static float[] neighborRgb(int[] px,int w,int h,int x,int y,int radius){
        int x0=Math.max(0,x-radius),x1=Math.min(w-1,x+radius),y0=Math.max(0,y-radius),y1=Math.min(h-1,y+radius);float r=0,g=0,b=0;int count=0;
        for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){if(xx==x&&yy==y)continue;int c=px[yy*w+xx];r+=((c>>>16)&255)/255f;g+=((c>>>8)&255)/255f;b+=(c&255)/255f;count++;}
        if(count==0)return new float[]{0,0,0};return new float[]{r/count,g/count,b/count};
    }

    private static float smooth(float edge0,float edge1,float x){if(edge0==edge1)return x<edge0?0f:1f;float t=clamp((x-edge0)/(edge1-edge0));return t*t*(3f-2f*t);}
    private static float luma(float r,float g,float b){return .2126f*r+.7152f*g+.0722f*b;}
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}
    private static float clampSigned(float v){return Math.max(-1f,Math.min(1f,v));}
    private static int to8(float v){return Math.round(clamp(v)*255f);}
    private static float mix(float a,float b,float t){return a+(b-a)*clamp(t);}

    private static float[] rgbToHsv(float r,float g,float b){
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0;
        if(d!=0){if(max==r)h=60f*(((g-b)/d)%6f);else if(max==g)h=60f*(((b-r)/d)+2f);else h=60f*(((r-g)/d)+4f);}if(h<0)h+=360;
        return new float[]{h,max==0?0:d/max,max};
    }

    private static float[] hsvToRgb(float h,float s,float v){
        h=((h%360)+360)%360;float c=v*s,x=c*(1-Math.abs((h/60f)%2-1)),m=v-c,r=0,g=0,b=0;
        if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else{r=c;b=x;}
        return new float[]{r+m,g+m,b+m};
    }
}
