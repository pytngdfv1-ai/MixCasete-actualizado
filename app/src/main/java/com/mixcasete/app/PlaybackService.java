package com.mixcasete.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Servicio de reproducción en primer plano.
 *
 * Usa android.media.session.MediaSession (nativo de Android, sin dependencias
 * extra) para que:
 *  - El sistema reconozca esto como reproducción de música "legítima" en curso
 *    (mejora cómo Doze/el ahorro de batería de fabricantes trata al servicio,
 *    sin tener que pedir permisos especiales al usuario).
 *  - Aparezcan controles en la PANTALLA DE BLOQUEO y en auriculares/Bluetooth.
 *  - Los botones físicos de reproducir/pausar (auriculares, etc.) funcionen.
 */
public class PlaybackService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    public static final String CHANNEL = "mixcasete_play";
    public static final String EXTRA_CMD = "cmd";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_SEEK = "seek";

    private PowerManager.WakeLock wl;
    private WifiManager.WifiLock wifiLock;
    private boolean isBridgeMode = false;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private MediaPlayer player;
    private MediaSession mediaSession;
    private String currentTitle = "Mix.Casete";
    private String currentArtist = "";
    private boolean prepared = false;

    private static volatile PlaybackService sInstance;

    public static PlaybackService getInstance() {
        return sInstance;
    }

    public int getPlayerPosition() {
        try {
            if (player != null && prepared) return player.getCurrentPosition();
        } catch (Exception ignored) {}
        return -1;
    }

    public int getPlayerDuration() {
        try {
            if (player != null && prepared) return player.getDuration();
        } catch (Exception ignored) {}
        return -1;
    }

    public static void start(android.content.Context c, Intent i) {
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(android.content.Context c) {
        c.stopService(new Intent(c, PlaybackService.class));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        crearCanal();
        setupMediaSession();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, buildNotif("Mix.Casete", false),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, buildNotif("Mix.Casete", false));
        }
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "MixCaseteSession");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { doCmd("play"); }
            @Override public void onPause() { doCmd("pause"); }
            @Override public void onStop() { doCmd("stop"); }
            @Override public void onSkipToNext() { notifyJs("btn_next"); }
            @Override public void onSkipToPrevious() { notifyJs("btn_prev"); }
            @Override public void onSeekTo(long pos) {
                if (player != null && prepared) player.seekTo((int) pos);
            }
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                Object evObj = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (evObj instanceof KeyEvent) {
                    KeyEvent ev = (KeyEvent) evObj;
                    if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                        int code = ev.getKeyCode();
                        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                            if (player != null && player.isPlaying()) doCmd("pause"); else doCmd("play");
                            return true;
                        } else if (code == KeyEvent.KEYCODE_MEDIA_PLAY) { doCmd("play"); return true; }
                        else if (code == KeyEvent.KEYCODE_MEDIA_PAUSE) { doCmd("pause"); return true; }
                        else if (code == KeyEvent.KEYCODE_MEDIA_STOP) { doCmd("stop"); return true; }
                        else if (code == KeyEvent.KEYCODE_MEDIA_NEXT
                                || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                            notifyJs(code == KeyEvent.KEYCODE_MEDIA_NEXT ? "btn_next" : "btn_prev");
                            return true;
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });
        mediaSession.setActive(true);
    }

    /** Ejecuta el mismo camino que un comando llegado por Intent, para que
     *  los botones de auriculares/pantalla de bloqueo hagan lo mismo que los
     *  botones dentro de la app. */
    private void doCmd(String cmd) {
        Intent i = new Intent(this, PlaybackService.class);
        i.putExtra(EXTRA_CMD, cmd);
        onStartCommand(i, 0, 0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String cmd = intent.getStringExtra(EXTRA_CMD);
        if (cmd == null) return START_STICKY;

        switch (cmd) {
            case "play_url":
                isBridgeMode = false;
                String url = intent.getStringExtra(EXTRA_URL);
                String title = intent.getStringExtra(EXTRA_TITLE);
                String artist = intent.getStringExtra(EXTRA_ARTIST);
                if (title != null) currentTitle = title;
                currentArtist = artist != null ? artist : "";
                startPlayback(url);
                break;
            case "bridge_play":
                isBridgeMode = true;
                releasePlayer();
                acquireLocks();
                requestAudioFocus();
                String bTitle = intent.getStringExtra(EXTRA_TITLE);
                String bArtist = intent.getStringExtra(EXTRA_ARTIST);
                if (bTitle != null && !bTitle.isEmpty()) currentTitle = bTitle;
                currentArtist = bArtist != null ? bArtist : "";
                updateMetadata();
                updatePlaybackState(true);
                updateNotif(true);
                break;
            case "play":
                acquireLocks();
                requestAudioFocus();
                if (player != null && prepared) {
                    player.start();
                    updatePlaybackState(true);
                    updateNotif(true);
                    notifyJs("playing");
                } else if (isBridgeMode) {
                    updatePlaybackState(true);
                    updateNotif(true);
                    notifyJs("btn_play");
                }
                break;
            case "pause":
                if (player != null && prepared) {
                    player.pause();
                    updatePlaybackState(false);
                    updateNotif(false);
                    notifyJs("paused");
                } else if (isBridgeMode) {
                    updatePlaybackState(false);
                    updateNotif(false);
                    notifyJs("btn_pause");
                }
                break;
            case "stop":
                stopPlayback();
                stopSelf();
                break;
            case "seek":
                int sec = intent.getIntExtra(EXTRA_SEEK, 0);
                if (player != null && prepared) {
                    player.seekTo(sec * 1000);
                }
                break;
        }
        return START_STICKY;
    }

    private void startPlayback(String url) {
        isBridgeMode = false;
        releasePlayer();
        requestAudioFocus();
        acquireLocks();
        updateMetadata();

        try {
            player = new MediaPlayer();
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (url.startsWith("http://") || url.startsWith("https://")) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                headers.put("Referer", "https://www.youtube.com/");
                player.setDataSource(this, Uri.parse(url), headers);
            } else if (url.startsWith("file://")) {
                player.setDataSource(this, Uri.parse(url));
            } else {
                player.setDataSource(url);
            }
            player.setOnPreparedListener(this);
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            player.prepareAsync();
        } catch (Exception e) {
            notifyJs("error");
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        prepared = true;
        mp.start();
        updatePlaybackState(true);
        updateNotif(true);
        notifyJs("playing");
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        updatePlaybackState(false);
        notifyJs("ended");
        updateNotif(false);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        notifyJs("error");
        updateNotif(false);
        return true;
    }

    private void stopPlayback() {
        isBridgeMode = false;
        releasePlayer();
        releaseLocks();
        releaseAudioFocus();
        if (mediaSession != null) mediaSession.setActive(false);
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
                player.release();
            } catch (Exception e) {}
            player = null;
            prepared = false;
        }
    }

    private void requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focus -> {
                        if (focus == AudioManager.AUDIOFOCUS_LOSS && player != null && player.isPlaying()) {
                            player.pause();
                            updatePlaybackState(false);
                            updateNotif(false);
                            notifyJs("paused");
                        }
                    })
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void releaseAudioFocus() {
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    private void acquireLocks() {
        acquireWakeLock();
        acquireWifiLock();
    }

    private void releaseLocks() {
        releaseWakeLock();
        releaseWifiLock();
    }

    private void acquireWakeLock() {
        if (wl == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mixcasete:play");
            }
        }
        if (wl != null && !wl.isHeld()) wl.acquire(4 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wl != null && wl.isHeld()) {
            try { wl.release(); } catch (Exception ignored) {}
        }
    }

    private void acquireWifiLock() {
        if (wifiLock == null) {
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mixcasete:wifi");
                }
            } catch (Exception ignored) {}
        }
        if (wifiLock != null && !wifiLock.isHeld()) {
            try { wifiLock.acquire(); } catch (Exception ignored) {}
        }
    }

    private void releaseWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            try { wifiLock.release(); } catch (Exception ignored) {}
        }
    }

    /** Metadata (título/artista) que ve el sistema: pantalla de bloqueo,
     *  reloj/auto, auriculares con pantalla, etc. */
    private void updateMetadata() {
        if (mediaSession == null) return;
        MediaMetadata md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST,
                        currentArtist == null || currentArtist.isEmpty() ? "Mix.Casete" : currentArtist)
                .build();
        mediaSession.setMetadata(md);
    }

    private void updatePlaybackState(boolean playing) {
        if (mediaSession == null) return;
        long pos = 0;
        try { if (player != null && prepared) pos = player.getCurrentPosition(); } catch (Exception e) {}
        PlaybackState st = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        pos, playing ? 1f : 0f)
                .build();
        mediaSession.setPlaybackState(st);
        mediaSession.setActive(true);
    }

    private Notification buildNotif(String title, boolean playing) {
        Intent pause = new Intent(this, PlaybackService.class).putExtra(EXTRA_CMD, playing ? "pause" : "play");
        Intent stop = new Intent(this, PlaybackService.class).putExtra(EXTRA_CMD, "stop");
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pP = PendingIntent.getService(this, 1, pause, fl);
        PendingIntent pS = PendingIntent.getService(this, 3, stop, fl);

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, open, fl);

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle(title)
                .setContentText(playing ? "▶ Reproduciendo" : "❚❚ En pausa")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pOpen)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(playing
                        ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pausa" : "Seguir", pP)
                .addAction(android.R.drawable.ic_delete, "Parar", pS);

        if (mediaSession != null) {
            Notification.MediaStyle style = new Notification.MediaStyle();
            style.setMediaSession(mediaSession.getSessionToken());
            style.setShowActionsInCompactView(0, 1);
            b.setStyle(style);
        }
        return b.build();
    }

    private void updateNotif(boolean playing) {
        try {
            Notification notif = buildNotif(currentTitle, playing);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(1, notif);
            }
        } catch (Exception e) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(1, buildNotif(currentTitle, playing));
            } catch (Exception ignored) {}
        }
    }

    private void crearCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Reproducción", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Audio de Mix.Casete");
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private void notifyJs(String event) {
        if (MainActivity.self != null && MainActivity.self.get() != null) {
            MainActivity.self.get().onPlayerEvent(event);
        }
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) sInstance = null;
        stopPlayback();
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }
}
