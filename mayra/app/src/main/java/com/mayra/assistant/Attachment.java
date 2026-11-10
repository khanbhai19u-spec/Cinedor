package com.mayra.assistant;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.PlainTextSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A picture or document the user attached, converted into a Claude content block. */
public final class Attachment {

    public enum Kind { IMAGE, PDF, TEXT }

    public final Kind kind;
    public final String name;
    public final ContentBlockParam block;
    /** Small preview for images (may be null). */
    public final Bitmap preview;

    private Attachment(Kind kind, String name, ContentBlockParam block, Bitmap preview) {
        this.kind = kind; this.name = name; this.block = block; this.preview = preview;
    }

    public String label() {
        switch (kind) {
            case IMAGE: return "🖼️ " + name;
            case PDF: return "📄 " + name;
            default: return "📝 " + name;
        }
    }

    private static final long MAX_PDF = 30L * 1024 * 1024;   // API request limit is 32 MB
    private static final int MAX_TEXT_CHARS = 1_500_000;
    private static final int MAX_IMAGE_SIDE = 1568;          // larger images are downscaled by the API anyway

    /** Reads the file behind {@code uri}. Call on a background thread. */
    public static Attachment from(Context c, Uri uri) throws Exception {
        ContentResolver cr = c.getContentResolver();
        String name = displayName(c, uri);
        String mime = cr.getType(uri);
        String lower = name.toLowerCase(Locale.ROOT);
        if (mime == null) mime = guessMime(lower);

        if (mime.startsWith("image/")) return image(c, uri, name);

        if (mime.equals("application/pdf") || lower.endsWith(".pdf")) {
            byte[] data = readAll(cr.openInputStream(uri), MAX_PDF);
            String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
            DocumentBlockParam doc = DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(b64).build())
                    .title(name)
                    .build();
            return new Attachment(Kind.PDF, name, ContentBlockParam.ofDocument(doc), null);
        }

        String text;
        if (lower.endsWith(".docx") || mime.contains("wordprocessingml")) {
            text = docxText(cr.openInputStream(uri));
        } else if (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml")
                || mime.contains("javascript") || isCodeFile(lower)) {
            text = new String(readAll(cr.openInputStream(uri), MAX_TEXT_CHARS * 4L), StandardCharsets.UTF_8);
        } else {
            throw new Exception("এই ধরনের ফাইল এখনো সাপোর্ট করে না (" + mime + ")। ছবি, PDF, DOCX বা টেক্সট/কোড ফাইল দিন।");
        }
        if (text.trim().isEmpty()) throw new Exception("ফাইলে কোনো লেখা পাওয়া যায়নি।");
        if (text.length() > MAX_TEXT_CHARS) throw new Exception("ফাইলটা অনেক বড়। ছোট অংশ দিন।");
        DocumentBlockParam doc = DocumentBlockParam.builder()
                .source(PlainTextSource.builder().data(text).build())
                .title(name)
                .build();
        return new Attachment(Kind.TEXT, name, ContentBlockParam.ofDocument(doc), null);
    }

    private static Attachment image(Context c, Uri uri, String name) throws Exception {
        Bitmap bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(), uri),
                (decoder, info, src) -> {
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    int longest = Math.max(w, h);
                    if (longest > MAX_IMAGE_SIDE) {
                        float s = MAX_IMAGE_SIDE / (float) longest;
                        decoder.setTargetSize(Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)));
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos);
        String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        ImageBlockParam img = ImageBlockParam.builder()
                .source(Base64ImageSource.builder().data(b64).mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).build())
                .build();
        int pw = 160, ph = Math.max(1, Math.round(160f * bmp.getHeight() / Math.max(1, bmp.getWidth())));
        Bitmap preview = Bitmap.createScaledBitmap(bmp, pw, Math.min(ph, 320), true);
        return new Attachment(Kind.IMAGE, name, ContentBlockParam.ofImage(img), preview);
    }

    /** Extracts paragraph text from a .docx (it is a zip with word/document.xml inside). */
    static String docxText(InputStream in) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(e.getName())) continue;
                String xml = new String(readAll(zip, 50L * 1024 * 1024), StandardCharsets.UTF_8);
                xml = xml.replaceAll("</w:p>", "\n").replaceAll("<w:tab/>", "\t").replaceAll("<w:br/>", "\n");
                StringBuilder sb = new StringBuilder();
                Matcher m = Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|\n|\t").matcher(xml);
                while (m.find()) sb.append(m.group(1) != null ? m.group(1) : m.group());
                return sb.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                        .replace("&apos;", "'").replace("&amp;", "&").replaceAll("\n{3,}", "\n\n").trim();
            }
        }
        throw new Exception("DOCX ফাইলটা পড়া গেল না।");
    }

    private static byte[] readAll(InputStream in, long max) throws Exception {
        if (in == null) throw new Exception("ফাইল খোলা যায়নি।");
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = is.read(buf)) > 0) {
                total += n;
                if (total > max) throw new Exception("ফাইলটা অনেক বড় (সর্বোচ্চ " + (max / (1024 * 1024)) + " MB)।");
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last != null ? last : "file";
    }

    private static String guessMime(String lower) {
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.matches(".*\\.(jpe?g|png|webp|gif|heic|heif)$")) return "image/*";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }

    private static boolean isCodeFile(String lower) {
        return lower.matches(".*\\.(txt|md|csv|tsv|log|json|xml|html?|css|js|ts|tsx|jsx|java|kt|kts|py|c|h|cpp|hpp|cs|go|rs|rb|php|swift|dart|sql|sh|bat|yaml|yml|toml|ini|gradle|properties)$");
    }

    /** Camera photo saved by the camera app into our private cache. */
    public static Attachment fromCameraFile(Context c, Uri uri) throws Exception {
        return image(c, uri, "camera.jpg");
    }
}
