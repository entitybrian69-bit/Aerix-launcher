package net.kdt.pojavlaunch.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.Callable;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.downloader.VerificationException;

import org.apache.commons.io.*;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static final String USER_AGENT = Tools.APP_NAME;

    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        InputStream is = null;
        try {
            // System.out.println("Connecting: " + url.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setDoInput(true);
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + conn.getResponseCode()
                        + ": " + conn.getResponseMessage());
            }
            is = conn.getInputStream();
            IOUtils.copy(is, os);
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String downloadString(String url) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        download(url, bos);
        bos.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void downloadFile(String url, File out) throws IOException {
        FileUtils.ensureParentDirectory(out);
        try (FileOutputStream fileOutputStream = new FileOutputStream(out)) {
            download(url, fileOutputStream);
        }
    }

    /**
     * Downloads [urlInput] into [outputFile], surviving a connection that drops mid-transfer.
     *
     * A runtime is tens of megabytes, and a phone connection will often die somewhere in the
     * middle of that. The old version had no timeout, no retry and no resume, so a single dropped
     * read ended the whole install and the progress bar simply vanished. Now every attempt:
     *
     *  - keeps whatever arrived before, and asks the server to continue from there (HTTP Range),
     *  - restarts cleanly if the server ignores the range,
     *  - fails loudly if the body is shorter than the advertised length, which is what a silent
     *    truncation looks like, so the retry can pick it up,
     *  - gives up only after [MAX_ATTEMPTS] tries, with the last error as the cause.
     */
    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);
        if (buffer == null) buffer = new byte[65535];

        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long already = outputFile.isFile() ? outputFile.length() : 0L;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlInput).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                // Without this the server may hand back a gzip stream whose length means nothing.
                conn.setRequestProperty("Accept-Encoding", "identity");
                if (already > 0) conn.setRequestProperty("Range", "bytes=" + already + "-");

                int code = conn.getResponseCode();
                if (already > 0 && code == HttpURLConnection.HTTP_OK) {
                    // The server ignored the range, so the bytes we have are not a prefix of this
                    // response. Start over rather than stitching two different files together.
                    already = 0;
                    if (!outputFile.delete() && outputFile.exists()) {
                        throw new IOException("Could not restart the partial download");
                    }
                } else if (code != HttpURLConnection.HTTP_OK && code != 206) {
                    throw new IOException("Server returned HTTP " + code + " for " + urlInput);
                }

                // getContentLengthLong is API 24+; getContentLength is enough for a runtime.
                long expected = conn.getContentLength();
                if (expected > 0) expected += already;

                long overall = already;
                try (InputStream readStr = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outputFile, already > 0)) {
                    int current;
                    while ((current = readStr.read(buffer)) != -1) {
                        overall += current;
                        fos.write(buffer, 0, current);
                        // max must never be below curr, or the percentage goes backwards.
                        monitor.updateProgress((int) overall, (int) Math.max(expected, overall));
                    }
                }

                if (expected > 0 && overall < expected) {
                    throw new IOException("Download stopped at " + overall + " of " + expected + " bytes");
                }
                return;
            } catch (IOException e) {
                lastError = e;
                Log.w("DownloadUtils", "Attempt " + attempt + "/" + MAX_ATTEMPTS + " failed for "
                        + urlInput + " (" + e.getMessage() + ")");
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        // Small backoff: a dropped mobile connection needs a moment to come back.
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted", interrupted);
                    }
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw lastError != null ? lastError : new IOException("Download failed: " + urlInput);
    }

    private static final int MAX_ATTEMPTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 45_000;

    public static <T> T downloadStringCached(String url, String cacheName, ParseCallback<T> parseCallback) throws IOException, ParseException{
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/"+cacheName);
        if(cacheDestination.isFile() &&
                cacheDestination.canRead() &&
                System.currentTimeMillis() < (cacheDestination.lastModified() + 86400000)) {
            try {
                String cachedString = Tools.read(new FileInputStream(cacheDestination));
                return parseCallback.process(cachedString);
            }catch(IOException e) {
                Log.i("DownloadUtils", "Failed to read the cached file", e);
            }catch (ParseException e) {
                Log.i("DownloadUtils", "Failed to parse the cached file", e);
            }
        }
        String urlContent = DownloadUtils.downloadString(url);
        // if we download the file and fail parsing it, we will yeet outta there
        // and not cache the unparseable sting. We will return this after trying to save the downloaded
        // string into cache
        T parseResult = parseCallback.process(urlContent);

        boolean tryWriteCache;
        if(cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if(tryWriteCache) try {
            Tools.write(cacheDestination, urlContent);
        }catch(IOException e) {
            Log.i("DownloadUtils", "Failed to cache the string", e);
        }
        return parseResult;
    }

    private static boolean verifyFile(File file, String sha1) throws IOException {
        return file.exists() && HashUtils.compareSHA1(file, sha1);
    }

    public static void ensureSha1(File outputFile, @Nullable String sha1, DownloadFunction downloadFunction) throws IOException {
        // Skip if needed
        if(sha1 == null) {
            // If the file exists and we don't know it's SHA1, don't try to redownload it.
            if(outputFile.exists()) return;
            else {
                downloadFunction.download();
                return;
            }
        }

        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        while (attempts < 5 && !fileOkay){
            attempts++;
            downloadFunction.download();
            fileOkay = verifyFile(outputFile, sha1);
        }
        if(!fileOkay) throw new VerificationException("SHA1 verifcation failed after 5 download attempts");
    }

    /**
     * Get the content length for a given URL.
     * @param url the URL to get the length for
     * @return the length in bytes or -1 if not available
     */
    public static long getContentLength(String url) {
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
            urlConnection.setRequestMethod("HEAD");
            urlConnection.setDoInput(false);
            urlConnection.setDoOutput(false);
            urlConnection.connect();
            int responseCode = urlConnection.getResponseCode();
            if(responseCode >= 200 && responseCode <= 299) return urlConnection.getContentLength();
        }catch (IOException e) {
            Log.w("DownloadUtils", "Failed to get content length", e);
        }
        return -1;
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }

    public interface DownloadFunction {
        void download() throws IOException;
    }

    public static class ParseException extends Exception {
        public ParseException(Exception e) {
            super(e);
        }
    }
}

