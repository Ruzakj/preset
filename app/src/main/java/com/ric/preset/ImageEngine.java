package com.ric.preset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;
import java.util.Random;

public final class ImageEngine {
    public static Bitmap decode(Context c, Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){ BitmapFactory.decodeStream(in,null,bounds); }
        int sample=1;
        if(maxSide>0) while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>maxSide) sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inSampleSize=Math.max(1,sample); o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){ Bitmap b=BitmapFactory.decodeStream(in,null,o); if(b==null)throw new Exception("Format gambar tidak didukung"); return b; }
    }

    public static Bitmap apply(Bitmap src, Preset p, float strength) {
        if(p==null || strength<=0f) return src.copy(Bitmap.Config.ARGB_8888,false);
        strength=Math.max(0f,Math.min(1f,strength));
        Bitmap out=src.copy(Bitmap.Config.ARGB_8888,true);
        int w=out.getWidth(),h=out.getHeight(); int[] px=new int[w*h]; out.getPixels(px,0,w,0,0,w,h);
        float exp=(float)Math.pow(2,p.exposure*strength);
        float cont=1f+(p.contrast/100f)*strength;
        float satBase=1f+(p.saturation/100f)*strength;
        float vibr=p.vibrance/100f*strength;
        float temp=normalizeTemp(p.temperature)*strength, tint=normalizeTint(p.tint)*strength;
        float shadow=p.shadows/100f*strength, high=p.highlights/100f*strength, white=p.whites/100f*strength, black=p.blacks/100f*strength;
        float dehaze=p.dehaze/100f*strength;
        Random rnd=new Random(1337);
        for(int i=0;i<px.length;i++) {
            int c=px[i], a=(c>>>24)&255; float r=((c>>>16)&255)/255f,g=((c>>>8)&255)/255f,b=(c&255)/255f;
            r*=exp; g*=exp; b*=exp;
            float y=.2126f*r+.7152f*g+.0722f*b;
            float lift=shadow*(1f-clamp(y))*0.32f + black*0.12f;
            float hi=high*clamp(y)*0.25f + white*0.12f;
            r+=lift+hi; g+=lift+hi; b+=lift+hi;
            r=(r-.5f)*cont+.5f; g=(g-.5f)*cont+.5f; b=(b-.5f)*cont+.5f;
            r+=temp*.10f + tint*.035f; b-=temp*.10f; g-=tint*.035f;
            float[] hsv=rgbToHsv(clamp(r),clamp(g),clamp(b));
            int band=band(hsv[0]);
            hsv[0]=(hsv[0]+p.hue[band]*strength*.30f+360f)%360f;
            float vibBoost=vibr*(1f-hsv[1]);
            hsv[1]=clamp(hsv[1]*satBase + vibBoost + p.sat[band]/100f*strength);
            hsv[2]=clamp(hsv[2] + p.lum[band]/100f*strength*.25f);
            float[] rgb=hsvToRgb(hsv[0],hsv[1],hsv[2]); r=rgb[0];g=rgb[1];b=rgb[2];
            y=.2126f*r+.7152f*g+.0722f*b;
            float hazeCont=1f+dehaze*.55f; r=(r-.5f)*hazeCont+.5f;g=(g-.5f)*hazeCont+.5f;b=(b-.5f)*hazeCont+.5f;
            if(p.highlightSat>0 && y>.55f) { float t=(y-.55f)/.45f*(p.highlightSat/100f)*strength*.35f; float[] cc=hsvToRgb(p.highlightHue,1,1); r=mix(r,cc[0],t);g=mix(g,cc[1],t);b=mix(b,cc[2],t); }
            if(p.shadowSat>0 && y<.45f) { float t=(.45f-y)/.45f*(p.shadowSat/100f)*strength*.35f; float[] cc=hsvToRgb(p.shadowHue,1,1); r=mix(r,cc[0],t);g=mix(g,cc[1],t);b=mix(b,cc[2],t); }
            if(p.vignette!=0){ int x=i%w, yy=i/w; float dx=(x-w*.5f)/(w*.5f),dy=(yy-h*.5f)/(h*.5f); float d=clamp((dx*dx+dy*dy)*.55f); float v=1f+(p.vignette/100f)*strength*d*.65f; r*=v;g*=v;b*=v; }
            if(p.grain>0){ float n=(rnd.nextFloat()-.5f)*(p.grain/100f)*strength*.12f; r+=n;g+=n;b+=n; }
            px[i]=(a<<24)|(to8(r)<<16)|(to8(g)<<8)|to8(b);
        }
        out.setPixels(px,0,w,0,0,w,h); return out;
    }

    private static float normalizeTemp(float v){ if(Math.abs(v)>500) return (v-5500f)/5000f; return v/100f; }
    private static float normalizeTint(float v){ return v/100f; }
    private static int band(float h){ int[] centers={0,30,60,120,180,240,280,320}; int best=0;float bd=999;for(int i=0;i<centers.length;i++){float d=Math.abs(h-centers[i]);d=Math.min(d,360-d);if(d<bd){bd=d;best=i;}}return best; }
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}
    private static int to8(float v){return Math.round(clamp(v)*255f);} private static float mix(float a,float b,float t){return a+(b-a)*clamp(t);}
    private static float[] rgbToHsv(float r,float g,float b){ float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0; if(d!=0){if(max==r)h=60f*(((g-b)/d)%6f);else if(max==g)h=60f*(((b-r)/d)+2f);else h=60f*(((r-g)/d)+4f);} if(h<0)h+=360; return new float[]{h,max==0?0:d/max,max}; }
    private static float[] hsvToRgb(float h,float s,float v){ h=((h%360)+360)%360; float c=v*s,x=c*(1-Math.abs((h/60f)%2-1)),m=v-c,r=0,g=0,b=0; if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else{r=c;b=x;} return new float[]{r+m,g+m,b+m}; }
}
