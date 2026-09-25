package org.comboship.android;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
/** Only this application's selected internal or external directory is reachable through this API. */
public final class DataFiles {
    private DataFiles() {}
    public static File root(Context context) throws IOException {
        return StorageLocations.current(context);
    }
    private static File child(File root, String relative) throws IOException {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\") ||
                Arrays.asList(relative.split("/")).contains("..")) throw new IOException("Invalid relative filename.");
        File target = new File(root, relative).getCanonicalFile();
        if (!target.toPath().startsWith(root.getCanonicalFile().toPath()) || target.equals(root))
            throw new IOException("Filename escapes the data directory.");
        return target;
    }
    public static byte[] readLimited(InputStream stream, long maximum) throws IOException {
        if (stream == null) throw new IOException("Document provider returned no stream.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384]; int count;
        while ((count=stream.read(buffer)) != -1) {
            if (count == 0) continue;
            if ((long)out.size()+count > maximum) throw new IOException("File exceeds the size limit.");
            out.write(buffer,0,count);
        }
        return out.toByteArray();
    }
    static String digest(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[65536]; int count;
            while ((count=in.read(bytes))!=-1) digest.update(bytes,0,count);
            return RomImporter.hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {throw new IOException(impossible);}
    }
    static void atomicCopy(InputStream input, File destination, String expected, long maximum) throws IOException {
        if (input == null) throw new IOException("Document provider returned no stream.");
        File parent = destination.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create import directory.");
        Path temporary = Files.createTempFile(parent.toPath(), ".import-", ".tmp"); boolean done=false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                byte[] buffer = new byte[65536]; int count; long total=0;
                while ((count=input.read(buffer))!=-1) {
                    if (count==0) continue;
                    total+=count;
                    if (total>maximum) throw new IOException("File exceeds the import size limit.");
                    output.write(buffer,0,count);
                }
                if (total==0) throw new IOException("Selected file is empty.");
                output.flush();
                // Imported user data is durable before replacement. Bundled support files are
                // reconstructible and verified on every setup; syncing 9,000 XML files stalls first launch.
                if(expected==null)output.getFD().sync();
            }
            if (expected!=null && !expected.equals(digest(temporary.toFile()))) throw new IOException("Support-file checksum mismatch.");
            Files.move(temporary,destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            done=true;
        } finally {if(!done) Files.deleteIfExists(temporary);}
    }
    public static void installSupport(Context context, File root) throws IOException {
        try {
            byte[] manifest;
            try (InputStream in=context.getAssets().open("support-manifest.json")) {manifest=readLimited(in,4*1024*1024);}
            JSONArray files=new JSONObject(new String(manifest,java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("files");
            for (int i=0;i<files.length();i++) {
                JSONObject entry=files.getJSONObject(i);
                String relative=entry.getString("path"), sha=entry.getString("sha256");
                if (!relative.startsWith("assets/") && !relative.equals("soh.o2r") && !relative.equals("2ship.o2r") && !relative.equals("gamecontrollerdb.txt"))
                    throw new IOException("Unexpected bundled file: "+relative);
                File target=child(root,relative);
                if (!target.isFile() || !sha.equals(digest(target))) {
                    try (InputStream in=context.getAssets().open("runtime/"+relative)) {atomicCopy(in,target,sha,128L*1024*1024);}
                }
            }
            try (ZipFile archive=new ZipFile(new File(root,"soh.o2r"))) {
                if (archive.getEntry("shaders/opengl/default.shader.glsl")==null)
                    throw new IOException("Bundled renderer archive is missing its GLES shader.");
            }
            for (String directory:new String[]{"mods/soh","mods/2ship"}) {
                File folder=child(root,directory);
                if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create mods directory.");
            }
        } catch (JSONException error) {throw new IOException("Invalid support-file manifest.",error);}
    }
    public static void importMod(InputStream stream, File root, String game, String displayName) throws IOException {
        if (!game.equals("soh") && !game.equals("2ship")) throw new IOException("Unknown game.");
        String name=displayName==null?"":displayName.replaceAll("[^A-Za-z0-9._ -]","_");
        if (name.length()>120) name=name.substring(name.length()-120);
        String lower=name.toLowerCase(Locale.ROOT);
        if (name.startsWith(".") || (!lower.endsWith(".o2r") && !lower.endsWith(".otr")))
            throw new IOException("Choose a .o2r or .otr mod archive.");
        File destination=child(root,"mods/"+game+"/"+name);
        if (destination.exists()) throw new IOException("A mod with that filename exists. No file was overwritten.");
        atomicCopy(stream,destination,null,1024L*1024*1024);
    }
    public static void exportBackup(Context context, File root, OutputStream stream) throws IOException {
        if (stream==null) throw new IOException("Cannot open the backup document.");
        List<Path> candidates=new ArrayList<>();
        try (java.util.stream.Stream<Path> paths=Files.walk(root.toPath())) {
            paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).forEach(candidates::add);
        }
        candidates.sort(Comparator.naturalOrder()); long total=0;
        try (ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(stream))) {
            zip.putNextEntry(new ZipEntry("backup-info.txt"));
            zip.write(("ComboShip Android state backup\nSource: "+BuildConfig.VERSION_NAME+"\nROMs, support archives, and mods excluded.\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("android-touch-settings.json"));
            zip.write(new JSONObject(context.getSharedPreferences("combo-touch",Context.MODE_PRIVATE).getAll())
                .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            byte[] buffer=new byte[65536];
            for (Path path:candidates) {
                String relative=root.toPath().relativize(path).toString().replace(File.separatorChar,'/');
                String lower=relative.toLowerCase(Locale.ROOT);
                if (lower.startsWith("assets/") || lower.startsWith("mods/") || lower.endsWith(".o2r") || lower.endsWith(".otr") ||
                    lower.endsWith(".z64") || lower.endsWith(".n64") || lower.endsWith(".v64") || lower.endsWith(".tmp") ||
                    lower.equals(".runtime.lock") || lower.equals(".running")) continue;
                long length=Files.size(path); total+=length;
                if (length>64L*1024*1024 || total>256L*1024*1024) throw new IOException("Backup exceeds the safety size limit.");
                zip.putNextEntry(new ZipEntry(relative));
                try (InputStream in=Files.newInputStream(path)) {int count; while((count=in.read(buffer))!=-1) zip.write(buffer,0,count);}
                zip.closeEntry();
            }
        }
    }
}
