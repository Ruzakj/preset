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
        if(p==null||strength<=0f)return src.copy(Bitmap.Config.ARGB_8888,false); strength=clamp(strength);
        int w=src.getWidth(),h=src.getHeight(),n=w*h;int[] original=new int[n],base=new int[n];src.getPixels(original,0,w,0,0,w,h);
        float exp=(float)Math.pow(2.0,p.exposure*strength),cont=p.contrast*strength/100f;
        float satAll=1f+p.saturation*strength/100f,vibr=p.vibrance*strength/100f,temp=temperatureShift(p.temperature)*strength,tint=tintShift(p.tint)*strength;

        for(int i=0;i<n;i++){
            int c=original[i],a=(c>>>24)&255;float r=((c>>>16)&255)/255f,g=((c>>>8)&255)/255f,b=(c&255)/255f;
            r*=exp;g*=exp;b*=exp;
            float y=luma(r,g,b),target=tone(clamp(y),p,strength);float d=target-y;r+=d;g+=d;b+=d;
            r=contrast(r,cont);g=contrast(g,cont);b=contrast(b,cont);
            float[] wb=whiteBalance(r,g,b,temp,tint);r=wb[0];g=wb[1];b=wb[2];
            r=curve(r,p.toneCurve,strength);g=curve(g,p.toneCurve,strength);b=curve(b,p.toneCurve,strength);
            r=curve(r,p.toneCurveRed,strength);g=curve(g,p.toneCurveGreen,strength);b=curve(b,p.toneCurveBlue,strength);

            float[] hsv=rgbToHsv(clamp(r),clamp(g),clamp(b));float[] adj=hslAdjustment(hsv[0],p,strength);
            hsv[0]=(hsv[0]+adj[0]+360f)%360f;float vibBoost=vibr*(1f-hsv[1])*(0.72f+0.28f*(1f-hsv[1]));
            hsv[1]=clamp(hsv[1]*satAll+vibBoost+adj[1]);hsv[2]=clamp(hsv[2]+adj[2]);
            float[] rgb=hsvToRgb(hsv[0],hsv[1],hsv[2]);r=rgb[0];g=rgb[1];b=rgb[2];

            y=luma(r,g,b);float balance=p.colorGradeBalance*strength/100f;
            float sw=smooth(0.58f+balance*.18f,0.05f,y);float hw=smooth(0.42f+balance*.18f,0.95f,y);float mw=clamp(1f-Math.max(sw,hw));
            float blend=.18f+.34f*clamp(p.colorGradeBlending/100f);
            if(p.shadowSat!=0) {float t=sw*Math.abs(p.shadowSat)*strength/100f*blend;rgb=tintWithHue(r,g,b,p.shadowHue,p.shadowSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
            if(p.midtoneSat!=0){float t=mw*Math.abs(p.midtoneSat)*strength/100f*blend;rgb=tintWithHue(r,g,b,p.midtoneHue,p.midtoneSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}
            if(p.highlightSat!=0){float t=hw*Math.abs(p.highlightSat)*strength/100f*blend;rgb=tintWithHue(r,g,b,p.highlightHue,p.highlightSat,t);r=rgb[0];g=rgb[1];b=rgb[2];}

            float deh=p.dehaze*strength/100f;if(deh!=0){float yy=luma(r,g,b),k=1f+deh*.48f;r=(r-yy)*k+yy;g=(g-yy)*k+yy;b=(b-yy)*k+yy;float cc=deh*.10f;r=contrast(r,cc);g=contrast(g,cc);b=contrast(b,cc);}
            base[i]=(a<<24)|(to8(r)<<16)|(to8(g)<<8)|to8(b);
        }

        float clarity=p.clarity*strength/100f,texture=p.texture*strength/100f;Random rnd=new Random(0x4C52434CL);
        int[] out=new int[n];
        for(int i=0;i<n;i++){
            int c=base[i],a=(c>>>24)&255;float r=((c>>>16)&255)/255f,g=((c>>>8)&255)/255f,b=(c&255)/255f;
            if(clarity!=0||texture!=0){int x=i%w,y=i/w;float yc=luma(r,g,b);float near=neighborLuma(base,w,h,x,y,1),wide=neighborLuma(base,w,h,x,y,2);float detail=(yc-near)*texture*.75f+(yc-wide)*clarity*.55f;float mask=1f-Math.abs(yc-.5f)*1.15f;detail*=clamp(mask);r+=detail;g+=detail;b+=detail;}
            if(p.vignette!=0){int x=i%w,y=i/w;float dx=(x-w*.5f)/(w*.5f),dy=(y-h*.5f)/(h*.5f);float radius=(float)Math.sqrt(dx*dx+dy*dy);float mid=.25f+.55f*(1f-clamp(p.vignetteMidpoint/100f));float feather=.08f+.72f*clamp(p.vignetteFeather/100f);float m=smooth(mid,Math.min(1.45f,mid+feather),radius);float v=p.vignette*strength/100f;if(v<0){float gain=1f+v*m*.72f;r*=gain;g*=gain;b*=gain;}else{float add=v*m*.45f;r+=add*(1-r);g+=add*(1-g);b+=add*(1-b);}}
            if(p.grain>0){float size=.65f+clamp(p.grainSize/100f)*1.7f,freq=.65f+clamp(p.grainFrequency/100f)*.7f;float noise=(rnd.nextFloat()+rnd.nextFloat()+rnd.nextFloat()-1.5f)/1.5f;float amp=p.grain*strength/100f*.095f*size*freq;float midMask=.55f+.45f*(1f-Math.abs(luma(r,g,b)-.5f)*1.6f);float q=noise*amp*clamp(midMask);r+=q;g+=q;b+=q;}
            out[i]=(a<<24)|(to8(r)<<16)|(to8(g)<<8)|to8(b);
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);result.setPixels(out,0,w,0,0,w,h);return result;
    }

    private static float tone(float y,Preset p,float s){
        float shadowW=(1f-y)*(1f-y),highW=y*y;float mid=4f*y*(1f-y);float v=y;
        v+=p.shadows*s/100f*shadowW*.38f;v+=p.highlights*s/100f*highW*.32f;
        float whiteW=smooth(.58f,1f,y),blackW=1f-smooth(0f,.42f,y);v+=p.whites*s/100f*whiteW*.24f;v+=p.blacks*s/100f*blackW*.22f;
        if(p.highlights<0)v-=(-p.highlights*s/100f)*highW*mid*.08f;if(p.shadows<0)v-=(-p.shadows*s/100f)*shadowW*mid*.08f;return clamp(v);
    }
    private static float contrast(float v,float amount){float k=1f+amount*1.18f;float x=(v-.5f)*k;return clamp(.5f+x/(1f+Math.abs(x)*.32f));}
    private static float[] whiteBalance(float r,float g,float b,float temp,float tint){float rg=1f+temp*.12f+tint*.025f,gg=1f-tint*.055f,bg=1f-temp*.14f+tint*.018f;return new float[]{clamp(r*rg),clamp(g*gg),clamp(b*bg)};}
    private static float temperatureShift(float v){if(Math.abs(v)>1000f){float k=clamp((v-2000f)/48000f);return (k-.46f)*2.15f;}return clampSigned(v/100f);}
    private static float tintShift(float v){return clampSigned(v/150f);}

    private static float curve(float v,float[][] pts,float s){if(pts==null||pts.length<2)return clamp(v);float x=clamp(v)*255f,y=pts[0][1];if(x<=pts[0][0])y=pts[0][1];else if(x>=pts[pts.length-1][0])y=pts[pts.length-1][1];else for(int i=1;i<pts.length;i++)if(x<=pts[i][0]){float x0=pts[i-1][0],x1=pts[i][0],t=(x-x0)/Math.max(.001f,x1-x0);y=mix(pts[i-1][1],pts[i][1],t);break;}return clamp(mix(v,y/255f,s));}

    private static float[] hslAdjustment(float hue,Preset p,float s){float ah=0,as=0,al=0,sum=0;for(int i=0;i<8;i++){float d=hueDistance(hue,HSL_CENTERS[i]);float width=(i==0||i==7)?52f:48f;float w=clamp(1f-d/width);w=w*w*(3f-2f*w);if(w>0){sum+=w;ah+=p.hue[i]*w;as+=p.sat[i]*w;al+=p.lum[i]*w;}}if(sum>0){ah/=sum;as/=sum;al/=sum;}return new float[]{ah*s*.30f,as*s/100f*.65f,al*s/100f*.34f};}
    private static float hueDistance(float a,float b){float d=Math.abs(a-b);return Math.min(d,360f-d);}
    private static float[] tintWithHue(float r,float g,float b,float hue,float signedSat,float amount){float[] color=hsvToRgb(hue,Math.min(1f,Math.abs(signedSat)/100f+.25f),1f);float y=luma(r,g,b);float cr=color[0]-.2126f*color[0]-.7152f*color[1]-.0722f*color[2],cg=color[1]-.2126f*color[0]-.7152f*color[1]-.0722f*color[2],cb=color[2]-.2126f*color[0]-.7152f*color[1]-.0722f*color[2];float sign=signedSat<0?-1f:1f;return new float[]{clamp(r+cr*amount*sign*(.5f+.5f*y)),clamp(g+cg*amount*sign*(.5f+.5f*y)),clamp(b+cb*amount*sign*(.5f+.5f*y))};}
    private static float neighborLuma(int[] px,int w,int h,int x,int y,int radius){int x0=Math.max(0,x-radius),x1=Math.min(w-1,x+radius),y0=Math.max(0,y-radius),y1=Math.min(h-1,y+radius);float s=0;int n=0;for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){if(xx==x&&yy==y)continue;int c=px[yy*w+xx];s+=luma(((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f);n++;}return n==0?0:s/n;}
    private static float smooth(float edge0,float edge1,float x){if(edge0==edge1)return x<edge0?0f:1f;float t=clamp((x-edge0)/(edge1-edge0));return t*t*(3f-2f*t);}
    private static float luma(float r,float g,float b){return .2126f*r+.7152f*g+.0722f*b;}
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}private static float clampSigned(float v){return Math.max(-1f,Math.min(1f,v));}
    private static int to8(float v){return Math.round(clamp(v)*255f);}private static float mix(float a,float b,float t){return a+(b-a)*clamp(t);}
    private static float[] rgbToHsv(float r,float g,float b){float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0;if(d!=0){if(max==r)h=60f*(((g-b)/d)%6f);else if(max==g)h=60f*(((b-r)/d)+2f);else h=60f*(((r-g)/d)+4f);}if(h<0)h+=360;return new float[]{h,max==0?0:d/max,max};}
    private static float[] hsvToRgb(float h,float s,float v){h=((h%360)+360)%360;float c=v*s,x=c*(1-Math.abs((h/60f)%2-1)),m=v-c,r=0,g=0,b=0;if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else{r=c;b=x;}return new float[]{r+m,g+m,b+m};}
}
