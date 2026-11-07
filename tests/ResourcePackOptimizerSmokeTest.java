package net.kdt.pojavlaunch.aerix.resourcepack;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageReadParam;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;

public class ResourcePackOptimizerSmokeTest {
    static void check(boolean x, String msg) { if (!x) throw new AssertionError(msg); }
    static byte[] contents(ZipFile z, String name) throws IOException { return z.getInputStream(z.getEntry(name)).readAllBytes(); }
    static byte[] pngScale(byte[] data,int max) throws IOException {
        try (ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader r=readers.next();
            try {
                r.setInput(stream); int width=r.getWidth(0), height=r.getHeight(0); int sample=1;
                while ((width+sample-1)/sample>max || (height+sample-1)/sample>max) sample*=2;
                if (sample == 1) return null;
                ImageReadParam p=r.getDefaultReadParam(); p.setSourceSubsampling(sample,sample,0,0);
                BufferedImage image=r.read(0,p); ByteArrayOutputStream out=new ByteArrayOutputStream();
                check(ImageIO.write(image,"PNG",out),"encode png"); image.flush(); return out.toByteArray();
            } finally { r.dispose(); }
        }
    }
    static void add(ZipOutputStream z, String name, String value) throws IOException {
        z.putNextEntry(new ZipEntry(name)); z.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); z.closeEntry();
    }
    public static void main(String[] args) throws Exception {
        File dir=Files.createTempDirectory("aerix-pack-test").toFile();
        try {
            File src = new File(dir,"original.zip");
            try (ZipOutputStream z=new ZipOutputStream(new FileOutputStream(src))) {
                add(z,"pack.mcmeta","{\"pack\":{\"pack_format\":46,\"description\":\"}\\\"text\"}}}}}\n");
                add(z,"assets/minecraft/textures/gui/plain.png","static");
                add(z,"assets/minecraft/textures/gui/animated.png","animated");
                add(z,"assets/minecraft/textures/gui/animated.png.mcmeta","{\"animation\":{}}");
                add(z,"assets/minecraft/textures/block/stone.png","block");
            }
            byte[] invalidEscapes = "{\"pack\":{\"description\":\"\\§4Hello\"}}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] fixedEscapes = ResourcePackOptimizer.repairTrailingBraces(invalidEscapes);
            check(fixedEscapes != invalidEscapes && new String(fixedEscapes,java.nio.charset.StandardCharsets.UTF_8).contains("§4Hello"), "format code escape repaired");
            byte[] unknown = "{\"pack\":{}} not understood".getBytes();
            check(ResourcePackOptimizer.repairTrailingBraces(unknown) == unknown, "unknown trailing data untouched");
            byte[] original=Files.readAllBytes(src.toPath());
            ResourcePackOptimizer.ImageScaler mock=(bytes, max) -> new String(bytes).equals("static") ? "smaller".getBytes() : null;
            ResourcePackOptimizer.Result out=ResourcePackOptimizer.createCopy(src,mock);
            check(out.resized==1 && out.metadataRepaired,"repair and resize");
            check(Arrays.equals(original,Files.readAllBytes(src.toPath())),"original preserved");
            try (ZipFile z=new ZipFile(out.file)) {
                check(new String(contents(z,"assets/minecraft/textures/gui/plain.png")).equals("smaller"),"static updated");
                check(new String(contents(z,"assets/minecraft/textures/gui/animated.png")).equals("animated"),"animation unchanged");
                check(new String(contents(z,"assets/minecraft/textures/block/stone.png")).equals("block"),"non-gui unchanged");
                String meta=new String(contents(z,"pack.mcmeta"));
                check(meta.trim().endsWith("}}") && !meta.trim().endsWith("}}}"), "trailing braces repaired");
            }
            ResourcePackOptimizer.Result second=ResourcePackOptimizer.createCopy(src,mock);
            check(!second.file.equals(out.file),"no overwrite on repeat");
            if (args.length > 0 && new File(args[0]).isFile()) {
                File real = new File(dir,"senpai.zip"); Files.copy(new File(args[0]).toPath(),real.toPath());
                byte[] before=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(real.toPath()));
                ResourcePackOptimizer.Result fixed=ResourcePackOptimizer.createCopy(real,ResourcePackOptimizerSmokeTest::pngScale);
                check(fixed.resized>10,"real oversized images reduced");
                check(fixed.metadataRepaired,"real metadata repaired");
                check(Arrays.equals(before,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(real.toPath()))),"real original untouched");
                int preserved=0;
                try (ZipFile a=new ZipFile(real); ZipFile b=new ZipFile(fixed.file)) {
                    check(a.size()==b.size(),"entry count retained");
                    Enumeration<? extends ZipEntry> list=a.entries();
                    while(list.hasMoreElements()) {
                        ZipEntry e=list.nextElement(); String name=e.getName();
                        if (e.isDirectory()) continue;
                        if (name.endsWith(".png.mcmeta")) {
                            check(Arrays.equals(contents(a,name),contents(b,name)),"animation metadata preserved: "+name);
                            String png=name.substring(0,name.length()-7);
                            if (a.getEntry(png)!=null) check(Arrays.equals(contents(a,png),contents(b,png)),"animated png unchanged: "+png);
                            preserved++;
                        }
                    }
                }
                check(preserved>250,"animated textures present");
                System.out.println("::notice title=Real pack mobile copy test::Resized " + fixed.resized + " static images, preserved " + preserved + " animation metadata entries and source bytes; repaired trailing metadata characters.");
            }
            System.out.println("Resource pack optimizer tests passed");
        } finally {
            File[] files=dir.listFiles(); if (files!=null) for (File f:files) f.delete(); dir.delete();
        }
    }
}
