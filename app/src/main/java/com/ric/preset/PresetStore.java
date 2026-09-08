package com.ric.preset;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PresetStore {
    public static List<Preset> loadAll(Context c) {
        ArrayList<Preset> out = new ArrayList<>();
        try { loadAssets(c.getAssets(), "", out); } catch(Exception ignored) {}
        File root = new File(c.getFilesDir(), "imported_presets");
        loadFiles(root, root, out);
        Collections.sort(out, (a,b) -> (a.category+a.name).compareToIgnoreCase(b.category+b.name));
        return out;
    }

    private static void loadAssets(AssetManager am, String path, List<Preset> out) throws Exception {
        String[] children = am.list(path);
        if (children == null) return;
        for (String child : children) {
            String full = path.isEmpty() ? child : path + "/" + child;
            if (child.toLowerCase().endsWith(".xmp")) {
                try (InputStream in=am.open(full)) { out.add(XmpParser.parse(in, child, parent(full))); }
            } else if (am.list(full) != null && am.list(full).length > 0) loadAssets(am, full, out);
        }
    }

    private static void loadFiles(File root, File dir, List<Preset> out) {
        if (!dir.exists()) return;
        File[] fs = dir.listFiles(); if (fs == null) return;
        for (File f: fs) {
            if (f.isDirectory()) loadFiles(root, f, out);
            else if (f.getName().toLowerCase().endsWith(".xmp")) {
                try (InputStream in=new FileInputStream(f)) { out.add(XmpParser.parse(in, f.getName(), f.getParentFile().getName())); } catch(Exception ignored) {}
            }
        }
    }

    public static int importUri(Context c, Uri uri) throws Exception {
        String name = displayName(c, uri);
        if (name.toLowerCase().endsWith(".zip")) return importZip(c, uri, name);
        if (!name.toLowerCase().endsWith(".xmp")) return 0;
        File dir = new File(c.getFilesDir(), "imported_presets/Imported"); dir.mkdirs();
        File dst = unique(dir, safe(name));
        InputStream in=c.getContentResolver().openInputStream(uri);
        if(in==null) throw new IOException("Tidak bisa membuka file");
        try(InputStream src=in; OutputStream os=new FileOutputStream(dst)) { copy(src,os); }
        return 1;
    }

    private static int importZip(Context c, Uri uri, String zipName) throws Exception {
        File base = new File(c.getFilesDir(), "imported_presets/"+safe(zipName.replaceAll("(?i)\\.zip$", ""))); base.mkdirs();
        int count=0;
        InputStream raw=c.getContentResolver().openInputStream(uri);
        if(raw==null) throw new IOException("Tidak bisa membuka ZIP");
        try(ZipInputStream zis=new ZipInputStream(raw)) {
            ZipEntry e;
            while((e=zis.getNextEntry())!=null) {
                if (e.isDirectory() || !e.getName().toLowerCase().endsWith(".xmp")) continue;
                String leaf = e.getName().replace('\\','/');
                leaf = leaf.substring(leaf.lastIndexOf('/')+1);
                File dst=unique(base, safe(leaf));
                try(OutputStream os=new FileOutputStream(dst)) { byte[] b=new byte[8192]; int n; while((n=zis.read(b))>0) os.write(b,0,n); }
                count++;
            }
        }
        return count;
    }

    private static String displayName(Context c, Uri u) {
        try(Cursor cur=c.getContentResolver().query(u,null,null,null,null)) {
            if(cur!=null && cur.moveToFirst()) { int i=cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(i>=0) return cur.getString(i); }
        } catch(Exception ignored) {}
        String p=u.getLastPathSegment(); return p==null?"preset.xmp":p;
    }
    private static String parent(String p) { int i=p.lastIndexOf('/'); return i>0?p.substring(0,i).replace("/", " · "):"Built-in"; }
    private static String safe(String n) { return n.replaceAll("[^a-zA-Z0-9._ -]","_"); }
    private static File unique(File d,String n) { File f=new File(d,n); int i=2; while(f.exists()){ int dot=n.lastIndexOf('.'); String a=dot>0?n.substring(0,dot):n,b=dot>0?n.substring(dot):""; f=new File(d,a+" ("+(i++)+")"+b);} return f; }
    private static void copy(InputStream in, OutputStream out) throws IOException { byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)out.write(b,0,n); }
}
