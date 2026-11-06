package net.kdt.pojavlaunch.aerix.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Makes a separate smaller resource pack; animated textures and the original file stay untouched. */
public final class ResourcePackOptimizer {
    public interface ImageScaler { byte[] scalePng(byte[] image, int maxDimension) throws IOException; }
    public static final class Result {
        public final File file;
        public final int resized;
        public final boolean metadataRepaired;
        Result(File file, int resized, boolean metadataRepaired) { this.file=file; this.resized=resized; this.metadataRepaired=metadataRepaired; }
    }
    private static final long ENTRY_LIMIT = 64L * 1024 * 1024, TOTAL_LIMIT = 1024L * 1024 * 1024;
    private ResourcePackOptimizer() { }
    public static Result createCopy(File original, ImageScaler scaler) throws IOException {
        if (!original.isFile() || !original.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) throw new IOException("Choose a zipped resource pack");
        File dir = original.getCanonicalFile().getParentFile();
        File temporary = File.createTempFile(".aerix-pack-", ".tmp", dir);
        File target = null;
        try {
            int resized=0; boolean repaired=false;
            try (ZipFile source = new ZipFile(original); ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
                Set<String> names = new HashSet<>(); long total=0; int count=0;
                Enumeration<? extends ZipEntry> check=source.entries();
                while (check.hasMoreElements()) {
                    ZipEntry entry=check.nextElement();
                    if (++count > 20000 || entry.getSize() < 0 || entry.getSize() > ENTRY_LIMIT || (total += entry.getSize()) > TOTAL_LIMIT) throw new IOException("Resource pack exceeds safe limits");
                    String name=entry.getName();
                    if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/")).contains("..") || !names.add(name)) throw new IOException("Invalid or duplicate resource pack entry");
                }
                if (!names.contains("pack.mcmeta")) throw new IOException("This ZIP does not have a root pack.mcmeta");
                boolean hasLegacyAnimations = false;
                for (String name : names) {
                    String lower=name.toLowerCase(Locale.ROOT);
                    if ((lower.contains("/optifine/anim/") || lower.contains("/mcpatcher/anim/")) && lower.endsWith(".properties")) hasLegacyAnimations = true;
                }
                Enumeration<? extends ZipEntry> all=source.entries();
                while (all.hasMoreElements()) {
                    ZipEntry entry=all.nextElement(); String name=entry.getName();
                    ZipEntry copy = new ZipEntry(name);
                    if (entry.getTime() >= 0) copy.setTime(entry.getTime());
                    output.putNextEntry(copy);
                    if (!entry.isDirectory()) {
                        if (name.equals("pack.mcmeta") && entry.getSize() <= 1024 * 1024) {
                            byte[] bytes=readBytes(source,entry,1024*1024);
                            byte[] fixed=repairTrailingBraces(bytes);
                            output.write(fixed); repaired = fixed != bytes;
                        } else if (eligible(name) && (!hasLegacyAnimations || name.equals("pack.png")) && !names.contains(name + ".mcmeta") && entry.getSize() <= 32 * 1024 * 1024) {
                            byte[] bytes=readBytes(source,entry,32*1024*1024);
                            byte[] scaled=scaler.scalePng(bytes, 1024);
                            if (scaled != null) { output.write(scaled); resized++; } else output.write(bytes);
                        } else {
                            try (InputStream in=source.getInputStream(entry)) { copyBounded(in,output,ENTRY_LIMIT); }
                        }
                    }
                    output.closeEntry();
                }
            }
            if (resized == 0 && !repaired) throw new IOException("No oversized static menu textures or repairable metadata were found");
            String stem=original.getName().substring(0,original.getName().length()-4);
            for (int i=0; i<1000; i++) {
                File candidate=new File(dir, stem + " - Aerix mobile" + (i == 0 ? "" : " " + (i+1)) + ".zip");
                if (candidate.createNewFile()) { target=candidate; break; }
            }
            if (target == null) throw new IOException("Could not choose a new file name");
            if (!temporary.renameTo(target)) throw new IOException("Could not save the new pack");
            return new Result(target,resized,repaired);
        } catch (IOException | RuntimeException e) {
            if (target != null) target.delete();
            throw e;
        } finally { temporary.delete(); }
    }
    static boolean eligible(String name) {
        return name.equals("pack.png") || (name.startsWith("assets/minecraft/textures/gui/") && name.endsWith(".png"));
    }
    private static byte[] readBytes(ZipFile zip, ZipEntry entry, int max) throws IOException {
        try (InputStream in=zip.getInputStream(entry); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            copyBounded(in,out,max); return out.toByteArray();
        }
    }
    private static void copyBounded(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buffer = new byte[8192]; int n; long total=0;
        while ((n=in.read(buffer)) != -1) { total+=n; if (total>max) throw new IOException("Resource pack entry exceeds safe limit"); out.write(buffer,0,n); }
    }
    static byte[] repairTrailingBraces(byte[] bytes) {
        String text;
        try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
        catch (java.nio.charset.CharacterCodingException e) { return bytes; }
        StringBuilder cleaned = new StringBuilder();
        boolean inString=false, slash=false, fixedEscape=false;
        for (int i=0; i<text.length(); i++) {
            char c=text.charAt(i);
            if (inString && !slash && c=='\\' && i+1<text.length() && text.charAt(i+1)=='§') {
                cleaned.append('§'); i++; fixedEscape=true; continue;
            }
            cleaned.append(c);
            if (inString) { if (slash) slash=false; else if (c=='\\') slash=true; else if (c=='"') inString=false; }
            else if (c=='"') inString=true;
        }
        text=cleaned.toString();
        int start=0; while (start<text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start>=text.length() || text.charAt(start)!='{') return bytes;
        int depth=0, end=-1; boolean quoted=false, escaped=false;
        for (int i=start;i<text.length();i++) {
            char c=text.charAt(i);
            if (quoted) { if (escaped) escaped=false; else if (c=='\\') escaped=true; else if (c=='"') quoted=false; }
            else if (c=='"') quoted=true;
            else if (c=='{') depth++;
            else if (c=='}' && --depth==0) { end=i+1; break; }
        }
        if (end<0) return bytes;
        boolean extraBrace=false;
        for (int i=end;i<text.length();i++) {
            char c=text.charAt(i);
            if (c=='}') extraBrace=true; else if (!Character.isWhitespace(c)) return bytes;
        }
        if (!extraBrace && !fixedEscape) return bytes;
        try {
            JsonElement element=new JsonParser().parse(text.substring(0,end));
            if (!element.isJsonObject() || !element.getAsJsonObject().has("pack") || !element.getAsJsonObject().get("pack").isJsonObject()) return bytes;
            return (text.substring(0,end) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) { return bytes; }
    }
}
