package com.ric.preset;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_PHOTO=10, IMPORT_PRESET=11, SAVE_PHOTO=12;
    private ImageView preview;
    private LinearLayout presetList;
    private TextView status, strengthLabel;
    private SeekBar strength;
    private Uri photoUri;
    private Bitmap previewOriginal;
    private List<Preset> presets = new ArrayList<>();
    private Preset selected;
    private int generation=0;

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); reloadPresets(); }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView title=new TextView(this); title.setText("RIC PRESET"); title.setTextSize(22); title.setTypeface(null,Typeface.BOLD); root.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button open=button("Pilih Foto"), imp=button("Import XMP/ZIP"), save=button("Export");
        actions.addView(open,new LinearLayout.LayoutParams(0,dp(52),1)); actions.addView(imp,new LinearLayout.LayoutParams(0,dp(52),1)); actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1)); root.addView(actions);
        preview=new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.setBackgroundColor(Color.rgb(240,240,240)); root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        strengthLabel=new TextView(this); strengthLabel.setText("Strength 100%"); strengthLabel.setPadding(0,dp(8),0,0); root.addView(strengthLabel);
        strength=new SeekBar(this); strength.setMax(100); strength.setProgress(100); root.addView(strength);
        status=new TextView(this); status.setTextSize(13); status.setPadding(0,dp(4),0,dp(4)); root.addView(status);
        HorizontalScrollView scroll=new HorizontalScrollView(this); presetList=new LinearLayout(this); presetList.setOrientation(LinearLayout.HORIZONTAL); scroll.addView(presetList); root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(82)));
        setContentView(root);
        open.setOnClickListener(v->pickPhoto()); imp.setOnClickListener(v->importPresets()); save.setOnClickListener(v->chooseExport());
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean f){strengthLabel.setText("Strength "+p+"%"); if(f)renderPreview();} public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){renderPreview();}});
        preview.setOnTouchListener((v,e)->{ if(previewOriginal==null)return false; if(e.getAction()==MotionEvent.ACTION_DOWN){preview.setImageBitmap(previewOriginal);return true;} if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){renderPreview();return true;} return true; });
    }

    private Button button(String t){ Button b=new Button(this); b.setText(t); b.setTextSize(11); return b; }
    private int dp(int n){ return Math.round(n*getResources().getDisplayMetrics().density); }

    private void pickPhoto(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); startActivityForResult(i,PICK_PHOTO); }
    private void importPresets(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); startActivityForResult(i,IMPORT_PRESET); }
    private void chooseExport(){ if(photoUri==null){toast("Pilih foto dulu");return;} Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/jpeg");i.putExtra(Intent.EXTRA_TITLE,"RIC-Preset.jpg");startActivityForResult(i,SAVE_PHOTO); }

    @Override protected void onActivityResult(int r,int result,Intent data){ super.onActivityResult(r,result,data); if(result!=RESULT_OK||data==null)return;
        if(r==PICK_PHOTO){ photoUri=data.getData(); loadPreview(); }
        else if(r==IMPORT_PRESET){ ArrayList<Uri> uris=new ArrayList<>(); if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri()); else if(data.getData()!=null)uris.add(data.getData()); importUris(uris); }
        else if(r==SAVE_PHOTO) exportTo(data.getData());
    }

    private void reloadPresets(){ presets=PresetStore.loadAll(this); presetList.removeAllViews(); String last=""; for(Preset p:presets){ Button b=button(p.name); b.setAllCaps(false); b.setPadding(dp(12),0,dp(12),0); b.setOnClickListener(v->{selected=p;status.setText(p.category+" · "+p.name);renderPreview();}); presetList.addView(b,new LinearLayout.LayoutParams(-2,dp(64))); last=p.category; } status.setText(presets.size()+" preset tersedia"); }

    private void loadPreview(){ status.setText("Memuat foto…"); new Thread(()->{ try{ Bitmap b=ImageEngine.decode(this,photoUri,1600); runOnUiThread(()->{previewOriginal=b;preview.setImageBitmap(b);status.setText(presets.size()+" preset · tahan foto untuk Before");renderPreview();}); }catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}).start(); }

    private void renderPreview(){ if(previewOriginal==null)return; if(selected==null){preview.setImageBitmap(previewOriginal);return;} final int g=++generation; final Preset p=selected; final float s=strength.getProgress()/100f; status.setText("Menerapkan "+p.name+"…"); new Thread(()->{ Bitmap out=ImageEngine.apply(previewOriginal,p,s); runOnUiThread(()->{if(g==generation){preview.setImageBitmap(out);status.setText(p.category+" · "+p.name+" · "+strength.getProgress()+"%");}}); }).start(); }

    private void importUris(List<Uri> uris){ status.setText("Mengimport preset…"); new Thread(()->{int n=0;String err=null;for(Uri u:uris)try{n+=PresetStore.importUri(this,u);}catch(Exception e){err=e.getMessage();} final int count=n;final String error=err;runOnUiThread(()->{reloadPresets();toast(count+" preset berhasil diimport"+(error==null?"":" · "+error));});}).start(); }

    private void exportTo(Uri dst){ status.setText("Export resolusi penuh…"); new Thread(()->{try{Bitmap src=ImageEngine.decode(this,photoUri,0);Bitmap out=selected==null?src:ImageEngine.apply(src,selected,strength.getProgress()/100f);try(OutputStream os=getContentResolver().openOutputStream(dst)){if(os==null)throw new Exception("Output tidak bisa dibuka");out.compress(Bitmap.CompressFormat.JPEG,95,os);}runOnUiThread(()->{status.setText("Export selesai");toast("Foto tersimpan");});}catch(Exception e){runOnUiThread(()->toast("Export gagal: "+e.getMessage()));}}).start(); }
    private void toast(String s){ Toast.makeText(this,s==null?"Terjadi kesalahan":s,Toast.LENGTH_LONG).show(); }
}
