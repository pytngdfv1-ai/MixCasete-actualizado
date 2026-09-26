package com.mixcasete.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    public static WeakReference<MainActivity> self;

    private WebView wv;
    private WebView playerWv;
    private FrameLayout rootLayout;
    private android.widget.TextView videoCloseBtn;

    private boolean polling = false;
    private boolean isForeground = false;
    private int noVideoCount = 0;
    private boolean triedAlt = false;
    private String lastId = null;
    private boolean npInit = false;
    private String pendingExport = null;

    private static final int REQ_OPEN = 777;
    private static final int REQ_WRITE = 42;

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } catch (Exception ignored) {}
        self = new WeakReference<>(this);

        FrameLayout root = new FrameLayout(this);
        rootLayout = root;

        wv = new WebView(this);
        config(wv.getSettings());
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient());
        wv.addJavascriptInterface(new Bridge(), "Android");
        root.addView(wv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        playerWv = new WebView(this);
        config(playerWv.getSettings());
        playerWv.setWebViewClient(new PlayerClient());
        playerWv.setWebChromeClient(new WebChromeClient());
        root.addView(playerWv, new FrameLayout.LayoutParams(1, 1));
        playerWv.setAlpha(0f);

        setContentView(root);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        /* Limpia respaldos duplicados de la playlist al abrir (una sola vez en fondo) */
        new Thread(() -> cleanupDuplicateBackups()).start();

        requestNotifPermission();

        wv.loadUrl("file:///android_asset/index.html");
    }

    /** Desde Android 13 (API 33) hay que pedir este permiso en tiempo de
     *  ejecución o la notificación de reproducción (y sus controles en
     *  pantalla de bloqueo) nunca se muestra, aunque esté en el manifiesto. */
    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 501);
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void config(WebSettings s) {
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    public void onPlayerEvent(String event) {
        runOnUiThread(() -> wv.evaluateJavascript(
                "window.onNativePlayerEvent && window.onNativePlayerEvent('" + event + "')", null));
    }

    /* ================= PUENTE JS ↔ JAVA ================= */
    public class Bridge {

        @JavascriptInterface
        public void openBrowser(final String url) {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {}
            });
        }

        @JavascriptInterface
        public void nativePlay(final String url, final String title) {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.putExtra(PlaybackService.EXTRA_CMD, "play_url");
            i.putExtra(PlaybackService.EXTRA_URL, url);
            i.putExtra(PlaybackService.EXTRA_TITLE, title != null ? title : "Mix.Casete");
            PlaybackService.start(MainActivity.this, i);
        }
        @JavascriptInterface
        public void nativePause() {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.putExtra(PlaybackService.EXTRA_CMD, "pause");
            PlaybackService.start(MainActivity.this, i);
        }
        @JavascriptInterface
        public void nativeResume() {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.putExtra(PlaybackService.EXTRA_CMD, "play");
            PlaybackService.start(MainActivity.this, i);
        }
        @JavascriptInterface
        public void nativeStop() {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.putExtra(PlaybackService.EXTRA_CMD, "stop");
            PlaybackService.start(MainActivity.this, i);
        }
        @JavascriptInterface
        public void nativeSeek(int sec) {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.putExtra(PlaybackService.EXTRA_CMD, "seek");
            i.putExtra(PlaybackService.EXTRA_SEEK, sec);
            PlaybackService.start(MainActivity.this, i);
        }

        @JavascriptInterface
        public boolean isNative() {
            return true;
        }

        @JavascriptInterface
        public int getNativePositionMs() {
            PlaybackService s = PlaybackService.getInstance();
            if (s != null) return s.getPlayerPosition();
            return -1;
        }

        @JavascriptInterface
        public int getNativeDurationMs() {
            PlaybackService s = PlaybackService.getInstance();
            if (s != null) return s.getPlayerDuration();
            return -1;
        }

        @JavascriptInterface
        public void showVideoOverlay(final String id) {
            runOnUiThread(() -> {
                try {
                    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                    int width = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.95f);
                    int height = (int) (width * 9f / 16f);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
                    lp.gravity = android.view.Gravity.CENTER;
                    playerWv.setLayoutParams(lp);
                    playerWv.setAlpha(1f);
                    // controls=0 garantiza que no se vean controles sobre el video
                    playerWv.loadUrl("https://www.youtube.com/embed/" + id
                            + "?autoplay=1&controls=0&playsinline=1&rel=0&modestbranding=1&iv_load_policy=3&disablekb=1&fs=0");

                    if (videoCloseBtn == null) {
                        videoCloseBtn = new android.widget.TextView(MainActivity.this);
                        videoCloseBtn.setText("✕");
                        videoCloseBtn.setTextSize(20);
                        videoCloseBtn.setTextColor(0xFFFFFFFF);
                        videoCloseBtn.setBackgroundColor(0x99000000);
                        int pad = (int) (10 * getResources().getDisplayMetrics().density);
                        videoCloseBtn.setPadding(pad, pad / 2, pad, pad / 2);
                        videoCloseBtn.setOnClickListener(v -> {
                            hideVideoOverlay();
                            wv.evaluateJavascript(
                                "window.onVideoOverlayClosed && window.onVideoOverlayClosed()", null);
                        });
                        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                        clp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                        int margin = (int) (18 * getResources().getDisplayMetrics().density);
                        clp.topMargin = margin; clp.rightMargin = margin;
                        rootLayout.addView(videoCloseBtn, clp);
                    }
                    videoCloseBtn.setVisibility(android.view.View.VISIBLE);
                } catch (Exception e) {}
            });
        }

        @JavascriptInterface
        public void hideVideoOverlay() {
            runOnUiThread(() -> {
                try {
                    stopPoll();
                    playerWv.loadUrl("about:blank");
                    playerWv.setAlpha(0f);
                    playerWv.setLayoutParams(new FrameLayout.LayoutParams(1, 1));
                    if (videoCloseBtn != null) videoCloseBtn.setVisibility(android.view.View.GONE);
                } catch (Exception e) {}
            });
        }

        @JavascriptInterface
        public void setOrientation(final String mode) {
            runOnUiThread(() -> {
                try {
                    if ("landscape".equalsIgnoreCase(mode)) {
                        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else if ("portrait".equalsIgnoreCase(mode)) {
                        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else {
                        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void setKeepScreenOn(final boolean keepOn) {
            runOnUiThread(() -> {
                try {
                    if (keepOn) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void playYT(final String id) {
            playYTWithMeta(id, null, null);
        }

        @JavascriptInterface
        public void playYTWithMeta(final String id, final String title, final String author) {
            lastId = id;
            noVideoCount = 0;
            triedAlt = false;

            // Mantener el servicio en primer plano para que Android no pause ni corte el audio
            try {
                Intent i = new Intent(MainActivity.this, PlaybackService.class);
                i.putExtra(PlaybackService.EXTRA_CMD, "bridge_play");
                i.putExtra(PlaybackService.EXTRA_TITLE, title != null && !title.isEmpty() ? title : "Mix.Casete");
                i.putExtra(PlaybackService.EXTRA_ARTIST, author != null ? author : "");
                PlaybackService.start(MainActivity.this, i);
            } catch (Exception ignored) {}

            runOnUiThread(() -> playerWv.loadUrl(
                    "https://www.youtube.com/watch?v=" + id + "&playsinline=1"));
        }
        @JavascriptInterface public void resumeYT() {
            try {
                Intent i = new Intent(MainActivity.this, PlaybackService.class);
                i.putExtra(PlaybackService.EXTRA_CMD, "play");
                PlaybackService.start(MainActivity.this, i);
            } catch (Exception ignored) {}
            runOnUiThread(() -> { tap(); enforce(); });
        }
        @JavascriptInterface public void pauseYT() {
            try {
                Intent i = new Intent(MainActivity.this, PlaybackService.class);
                i.putExtra(PlaybackService.EXTRA_CMD, "pause");
                PlaybackService.start(MainActivity.this, i);
            } catch (Exception ignored) {}
            js("(function(){var v=document.querySelector('video');if(v)v.pause();})();");
        }
        @JavascriptInterface public void stopYT()  {
            try {
                Intent i = new Intent(MainActivity.this, PlaybackService.class);
                i.putExtra(PlaybackService.EXTRA_CMD, "stop");
                PlaybackService.start(MainActivity.this, i);
            } catch (Exception ignored) {}
            runOnUiThread(() -> { polling = false; playerWv.loadUrl("about:blank"); });
        }
        @JavascriptInterface public void seekYT(final int sec) { js("(function(){var v=document.querySelector('video');if(v)v.currentTime=" + sec + ";})();"); }
        @JavascriptInterface public void unmuteYT() { runOnUiThread(() -> { tap(); enforce(); tap(); enforce(); }); }

        @JavascriptInterface
        public void getStream(final String id) {
            new Thread(() -> {
                String json = null;
                try { json = nativePlayer(id); } catch (Exception e) {}
                final String out = json;
                runOnUiThread(() -> wv.evaluateJavascript(
                        "window.__streamCb && window.__streamCb(" + (out != null ? out : "null") + ")", null));
            }).start();
        }

        @JavascriptInterface
        public void downloadYT(final String id, final String title) {
            new Thread(() -> {
                String path = null;
                try {
                    JSONObject j = new JSONObject(nativePlayer(id));
                    String url = j.getString("url");
                    File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
                    if (dir != null) {
                        File f = new File(dir, id + ".m4a");
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(120000);
                        c.setRequestProperty("User-Agent", UA);
                        InputStream in = c.getInputStream();
                        FileOutputStream out = new FileOutputStream(f);
                        byte[] buf = new byte[16384];
                        int n;
                        long total = 0;
                        while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
                        out.close();
                        in.close();
                        if (total > 10000) path = f.getAbsolutePath();
                        else f.delete();
                    }
                } catch (Exception e) {}
                final String p = path;
                runOnUiThread(() -> wv.evaluateJavascript(
                        "window.onDownloaded && window.onDownloaded('" + id + "'," +
                        (p != null ? "'" + p + "'" : "null") + ")", null));
            }).start();
        }

        @JavascriptInterface
        public void exportPlaylist(final String json) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                       != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingExport = json;
                requestPermissions(new String[]{ android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_WRITE);
                return;
            }
            doExport(json);
        }

        @JavascriptInterface
        public void exportPdf(final String base64Data, final String fileName) {
            new Thread(() -> {
                final boolean ok = writeBytesToDownloads(base64Data, fileName);
                runOnUiThread(() -> wv.evaluateJavascript(
                    "window.onPdfExported && window.onPdfExported(" + ok + ")", null));
            }).start();
        }

        @JavascriptInterface
        public void openPlaylistFile() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_OPEN);
            });
        }

        @JavascriptInterface
        public void loadPlaylistBackup() {
            new Thread(() -> {
                final String s = readPlaylistFromDownloads();
                if (s != null && !s.trim().isEmpty()) {
                    runOnUiThread(() -> {
                        wv.evaluateJavascript(
                            "window.onAutoLoadPlaylist && window.onAutoLoadPlaylist(" + JSONObject.quote(s) + ")", null);
                        wv.evaluateJavascript(
                            "window.onPlaylistImported && window.onPlaylistImported(" + JSONObject.quote(s) + ")", null);
                    });
                }
            }).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] g) {
        super.onRequestPermissionsResult(rc, perms, g);
        if (rc == REQ_WRITE && g.length > 0 && g[0] == 0 && pendingExport != null) {
            doExport(pendingExport);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OPEN && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            new Thread(() -> {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                    is.close();
                    final String s = bo.toString("UTF-8");
                    runOnUiThread(() -> wv.evaluateJavascript(
                        "window.onPlaylistImported && window.onPlaylistImported(" + JSONObject.quote(s) + ")", null));
                } catch (Exception e) {}
            }).start();
        }
    }

    private void doExport(final String json) {
        new Thread(() -> {
            final boolean ok = writePlaylistToDownloads(json);
            runOnUiThread(() -> wv.evaluateJavascript(
                "window.onExported && window.onExported(" + ok + ")", null));
        }).start();
    }

    /* ============ RESPALDO ÚNICO DE PLAYLIST ============ */

    /* Busca TODOS los respaldos existentes (MixCasete_playlist*.json o *playlist*.json) */
    private List<Uri> findAllPlaylistUris() {
        List<Uri> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return out;
        try {
            Cursor c = getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{ MediaStore.Downloads._ID, MediaStore.Downloads.DATE_MODIFIED },
                    MediaStore.Downloads.DISPLAY_NAME + " LIKE ? OR " + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
                    new String[]{ "%playlist%.json", "%MixCasete%.json" },
                    MediaStore.Downloads.DATE_MODIFIED + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    out.add(android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id));
                }
                c.close();
            }
        } catch (Exception ignored) {}
        return out;
    }

    /* Borra duplicados: conserva un solo archivo */
    private void cleanupDuplicateBackups() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
            List<Uri> all = findAllPlaylistUris();
            for (int i = 1; i < all.size(); i++) {
                try { getContentResolver().delete(all.get(i), null, null); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /* Escribe SIEMPRE sobre el mismo archivo (sobrescribe un único MixCasete_playlist.json) */
    private boolean writePlaylistToDownloads(String json) {
        try {
            // Guardar copia interna en almacenamiento privado seguro de la aplicación
            try {
                File internalFile = new File(getFilesDir(), "MixCasete_playlist.json");
                FileOutputStream fos = new FileOutputStream(internalFile, false);
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (Exception ignored) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.ContentResolver cr = getContentResolver();
                List<Uri> existing = findAllPlaylistUris();
                Uri target = null;
                OutputStream os = null;

                // Intentar reutilizar el primer archivo existente abriéndolo con "wt" (truncate/overwrite)
                for (Uri u : existing) {
                    try {
                        os = cr.openOutputStream(u, "wt");
                        if (os != null) {
                            target = u;
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                // Eliminar cualquier duplicado adicional para que quede un único archivo
                for (Uri u : existing) {
                    if (target != null && u.equals(target)) continue;
                    try { cr.delete(u, null, null); } catch (Exception ignored) {}
                }

                // Si no existía o no se pudo abrir, eliminar cualquier entrada conflictiva por nombre antes de crear
                if (os == null) {
                    try {
                        cr.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
                                new String[]{ "MixCasete_playlist%" });
                    } catch (Exception ignored) {}

                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, "MixCasete_playlist.json");
                    cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    cv.put(MediaStore.Downloads.IS_PENDING, 0);
                    target = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (target != null) {
                        os = cr.openOutputStream(target, "wt");
                    }
                }

                if (os != null) {
                    os.write(json.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                    return true;
                }
                return false;
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "MixCasete_playlist.json");
                FileOutputStream os = new FileOutputStream(f, false);
                os.write(json.getBytes("UTF-8"));
                os.flush();
                os.close();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /* Guarda un archivo binario (ej. PDF) en Descargas, decodificado desde base64. */
    private boolean writeBytesToDownloads(String base64Data, String fileName) {
        try {
            byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            String safeName = (fileName == null || fileName.trim().isEmpty())
                    ? "MixCasete_export.pdf" : fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri target = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (target == null) return false;
                OutputStream os = getContentResolver().openOutputStream(target);
                os.write(data);
                os.close();
                return true;
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File f = new File(dir, safeName);
                FileOutputStream os = new FileOutputStream(f);
                os.write(data);
                os.close();
                return true;
            }
        } catch (Exception e) { return false; }
    }

    private String readPlaylistFromDownloads() {
        // 1. Intentar leer desde MediaStore Downloads (Android 10+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                List<Uri> all = findAllPlaylistUris();
                for (Uri u : all) {
                    try (InputStream is = getContentResolver().openInputStream(u)) {
                        if (is != null) {
                            String s = readAll(is);
                            if (isValidPlaylistJson(s)) return s;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        // 2. Intentar leer desde carpeta física Download/
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && dir.exists()) {
                File[] matches = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".json") && (lower.contains("playlist") || lower.contains("mixcasete"));
                });
                if (matches != null && matches.length > 0) {
                    java.util.Arrays.sort(matches, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File f : matches) {
                        try {
                            String s = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
                            if (isValidPlaylistJson(s)) return s;
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Fallback al almacenamiento interno privado de la app
        try {
            File internalFile = new File(getFilesDir(), "MixCasete_playlist.json");
            if (internalFile.exists()) {
                String s = new String(java.nio.file.Files.readAllBytes(internalFile.toPath()), "UTF-8");
                if (isValidPlaylistJson(s)) return s;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private boolean isValidPlaylistJson(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        try {
            JSONObject j = new JSONObject(s);
            if (j.has("tracks") || j.has("app")) return true;
        } catch (Exception e) {
            try {
                JSONArray arr = new JSONArray(s);
                return arr.length() > 0;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /* ============ EXTRACCIÓN DE AUDIO ============ */
    private synchronized void ensureNewPipe() {
        if (!npInit) {
            try { NewPipe.init(new HttpDownloader()); } catch (Throwable t) {}
            npInit = true;
        }
    }

    private String newpipeExtract(String id) {
        try {
            ensureNewPipe();
            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=" + id);
            List<AudioStream> audios = info.getAudioStreams();
            AudioStream best = null;
            for (AudioStream a : audios) {
                if (a == null || a.getContent() == null) continue;
                if (best == null || a.getAverageBitrate() > best.getAverageBitrate()) best = a;
            }
            if (best == null) return null;
            JSONObject out = new JSONObject();
            out.put("url", best.getContent());
            out.put("title", info.getName());
            out.put("author", info.getUploaderName());
            return out.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static class HttpDownloader extends Downloader {
        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            HttpURLConnection conn = (HttpURLConnection) new URL(request.url()).openConnection();
            conn.setRequestMethod(request.httpMethod());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", UA);
            Map<String, List<String>> headers = request.headers();
            if (headers != null) {
                for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                    for (String v : e.getValue()) conn.addRequestProperty(e.getKey(), v);
                }
            }
            byte[] data = request.dataToSend();
            if (data != null) {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(data);
                os.close();
            }
            int code = conn.getResponseCode();
            if (code == 429) throw new ReCaptchaException("reCaptcha", request.url());
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = "";
            if (is != null) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                body = bo.toString("UTF-8");
            }
            return new Response(code, conn.getResponseMessage(),
                    conn.getHeaderFields(), conn.getURL().toString(), body);
        }
    }

    private String nativePlayer(String id) {
        String r = newpipeExtract(id);
        if (r != null) return r;
        r = innertubeExtract(id);
        if (r != null) return r;
        return fromInstances(id);
    }

    private String innertubeExtract(String id) {
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(9000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)");
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            JSONObject ctx = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "IOS");
            client.put("clientVersion", "19.45.4");
            client.put("deviceModel", "iPhone16,2");
            client.put("hl", "es");
            client.put("gl", "US");
            ctx.put("client", client);
            body.put("context", ctx);
            body.put("videoId", id);

            byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(out);
            os.close();

            int code = conn.getResponseCode();
            if (code == 200) {
                String resp = readAll(conn.getInputStream());
                JSONObject j = new JSONObject(resp);
                JSONObject sd = j.optJSONObject("streamingData");
                if (sd != null) {
                    JSONArray adaptive = sd.optJSONArray("adaptiveFormats");
                    if (adaptive != null) {
                        for (int i = 0; i < adaptive.length(); i++) {
                            JSONObject f = adaptive.optJSONObject(i);
                            if (f == null) continue;
                            String mime = f.optString("mimeType", "");
                            String u = f.optString("url", "");
                            if (mime.contains("audio/mp4") && !u.isEmpty()) {
                                JSONObject vd = j.optJSONObject("videoDetails");
                                String title = vd != null ? vd.optString("title", "") : "";
                                String author = vd != null ? vd.optString("author", "") : "";
                                return buildOut(u, title, author);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String fromInstances(String id) {
        try {
            JSONArray arr = new JSONArray(httpGet("https://api.invidious.io/instances.json?sort_by=health"));
            int tried = 0;
            for (int i = 0; i < arr.length() && tried < 6; i++) {
                JSONArray entry = arr.optJSONArray(i);
                if (entry == null) continue;
                String host = entry.optString(0, "");
                JSONObject meta = entry.optJSONObject(1);
                if (host.isEmpty() || meta == null || !meta.optBoolean("api", false)) continue;
                tried++;
                try {
                    JSONObject v = new JSONObject(httpGet("https://" + host + "/api/v1/videos/" + id));
                    String url = pickInvidious(v, host);
                    if (url != null) return buildOut(url, v.optString("title", ""), v.optString("author", ""));
                } catch (Exception e) {}
            }
        } catch (Exception e) {}
        try {
            JSONArray arr = new JSONArray(httpGet("https://piped-instances.kavin.rocks/"));
            int tried = 0;
            for (int i = 0; i < arr.length() && tried < 6; i++) {
                JSONObject inst = arr.optJSONObject(i);
                if (inst == null) continue;
                String api = inst.optString("api_url", "");
                if (api.isEmpty()) continue;
                tried++;
                try {
                    JSONObject v = new JSONObject(httpGet(api + "/streams/" + id));
                    String url = pickPiped(v);
                    if (url != null) return buildOut(url, v.optString("title", ""), v.optString("uploader", ""));
                } catch (Exception e) {}
            }
        } catch (Exception e) {}
        return null;
    }

    private String pickInvidious(JSONObject v, String host) {
        String fallback = null;
        JSONArray ad = v.optJSONArray("adaptiveFormats");
        if (ad != null) {
            for (int i = 0; i < ad.length(); i++) {
                JSONObject f = ad.optJSONObject(i);
                if (f == null) continue;
                String t = f.optString("type", "");
                String u = f.optString("url", "");
                if (u.isEmpty()) continue;
                if (u.startsWith("/")) u = "https://" + host + u;
                if (t.startsWith("audio/mp4")) return u;
                if (fallback == null && t.startsWith("audio")) fallback = u;
            }
        }
        if (fallback != null) return fallback;
        JSONArray fs = v.optJSONArray("formatStreams");
        if (fs != null && fs.length() > 0) {
            JSONObject f0 = fs.optJSONObject(0);
            if (f0 != null) {
                String u = f0.optString("url", "");
                if (u.startsWith("/")) u = "https://" + host + u;
                if (!u.isEmpty()) return u;
            }
        }
        return null;
    }

    private String pickPiped(JSONObject v) {
        JSONArray as = v.optJSONArray("audioStreams");
        String best = null;
        int bestBr = -1;
        if (as != null) for (int i = 0; i < as.length(); i++) {
            JSONObject f = as.optJSONObject(i);
            if (f == null) continue;
            String u = f.optString("url", "");
            if (u.isEmpty()) continue;
            int br = f.optInt("bitrate", 0);
            if (br > bestBr) { bestBr = br; best = u; }
        }
        return best;
    }

    private String buildOut(String url, String title, String author) {
        try {
            JSONObject out = new JSONObject();
            out.put("url", url);
            out.put("title", title);
            out.put("author", author);
            return out.toString();
        } catch (Exception e) { return null; }
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", UA);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("http " + code);
        return readAll(c.getInputStream());
    }

    private String readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    /* ============ WEBVIEW DE RESPALDO ============ */
    private class PlayerClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            injectBackgroundPlaybackScript(view);
            if (url.contains("youtube.com/watch") || url.contains("youtu.be/")) {
                injectWatchCss();
                tap();
                enforce();
                view.postDelayed(() -> { injectBackgroundPlaybackScript(view); injectWatchCss(); tap(); enforce(); }, 700);
                view.postDelayed(() -> { injectBackgroundPlaybackScript(view); enforce(); }, 1800);
                view.postDelayed(() -> { injectBackgroundPlaybackScript(view); enforce(); }, 3500);
                startPoll();
            } else if (url.contains("/embed/") || url.contains("youtube-nocookie")) {
                tap();
                enforce();
                view.postDelayed(() -> { injectBackgroundPlaybackScript(view); tap(); enforce(); }, 700);
                view.postDelayed(() -> { injectBackgroundPlaybackScript(view); enforce(); }, 1800);
                view.postDelayed(() -> { injectBackgroundPlaybackScript(view); enforce(); }, 3500);
                startPoll();
            }
        }
    }

    private void injectBackgroundPlaybackScript(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(
            "(function(){\n" +
            "  try {\n" +
            "    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });\n" +
            "    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });\n" +
            "    Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });\n" +
            "    Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });\n" +
            "  } catch(e) {}\n" +
            "  ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'].forEach(function(ev) {\n" +
            "    window.addEventListener(ev, function(e) { e.stopImmediatePropagation(); }, true);\n" +
            "    document.addEventListener(ev, function(e) { e.stopImmediatePropagation(); }, true);\n" +
            "  });\n" +
            "  var v = document.querySelector('video');\n" +
            "  if (v) {\n" +
            "    v.removeAttribute('muted'); v.defaultMuted = false; v.muted = false; v.volume = 1;\n" +
            "    if (v.paused) v.play();\n" +
            "  }\n" +
            "})();", null);
    }

    private void injectWatchCss() {
        playerWv.evaluateJavascript(
            "(function(){if(document.getElementById('mcCss'))return;" +
            "var s=document.createElement('style');s.id='mcCss';" +
            "s.textContent='ytd-masthead,#masthead,#comments,ytd-comments,#related,ytd-related,#secondary,#below,#chat,ytd-live-chat-frame,#subscribe-button,ytd-reel-shelf-renderer{display:none!important}';" +
            "document.head.appendChild(s);window.scrollTo(0,0);})()", null);
    }

    private void tap() {
        if (!isForeground) return;
        long t = SystemClock.uptimeMillis();
        float cx = Math.max(1, playerWv.getWidth() / 2f);
        float cy = Math.max(1, playerWv.getHeight() / 2f);
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0);
        MotionEvent up   = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cx, cy, 0);
        playerWv.dispatchTouchEvent(down);
        playerWv.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void enforce() {
        if (!isForeground) return;
        playerWv.evaluateJavascript(
            "(function(){var v=document.querySelector('video');" +
            "if(!v)return 'novideo';" +
            "v.removeAttribute('muted');v.defaultMuted=false;v.muted=false;v.volume=1;" +
            "if(v.paused){v.play();}" +
            "return 'ok m='+v.muted+' p='+v.paused;})()",
            value -> wv.evaluateJavascript("debug('bridge " + value + "')", null)
        );
    }

    private void startPoll() {
        if (polling) return;
        polling = true;
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            if (!polling) return; // se cerró el video: no seguir sondeando
            if (!isForeground) {
                // Si la app está en segundo plano o pantalla apagada, no saturar CPU
                // ni interferir con la pantalla de bloqueo
                if (polling) wv.postDelayed(r[0], 2500);
                return;
            }
            playerWv.evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(!v)return null;" +
                "return JSON.stringify({t:v.currentTime||0,p:v.paused,e:v.ended,m:v.muted});})()",
                value -> {
                    if (!polling) return;
                    if (value == null || value.equals("null")) {
                        noVideoCount++;
                        if (noVideoCount > 4 && !triedAlt && lastId != null) {
                            triedAlt = true;
                            noVideoCount = 0;
                            runOnUiThread(() -> playerWv.loadUrl(
                                "https://www.youtube-nocookie.com/embed/" + lastId +
                                "?autoplay=1&playsinline=1&rel=0"));
                        }
                    } else {
                        noVideoCount = 0;
                        if ((value.contains("\"m\":true") || value.contains("\"p\":true"))
                                && !value.contains("\"e\":true")) enforce();
                    }
                    wv.evaluateJavascript(
                        "window.onBridgeState && window.onBridgeState(" + value + ");", null);
                }
            );
            if (polling) wv.postDelayed(r[0], 1000);
        };
        wv.postDelayed(r[0], 1200);
    }

    /** Corta el sondeo y libera el foco de audio que enforce() venía pidiendo
     *  cada segundo; se llama al cerrar el video para no interferir más con
     *  la reproducción nativa. */
    private void stopPoll() {
        polling = false;
    }

    private void js(final String code) {
        runOnUiThread(() -> playerWv.evaluateJavascript(code, null));
    }

    @Override
    public void onBackPressed() {
        if (wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
        try {
            // Liberar flag para que la pantalla de bloqueo de Android funcione normalmente
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        if (wv != null) wv.resumeTimers();
        if (playerWv != null) playerWv.resumeTimers();
    }
}
