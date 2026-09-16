package com.mixcasete.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class PlaybackService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    public static final String CHANNEL = "mixcasete_play";
    public static final String EXTRA_CMD = "cmd";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SEEK = "seek";

    private PowerManager.WakeLock wl;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private MediaPlayer player;
    private String currentTitle = "Mix.Casete";
    private boolean prepared = false;

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
        crearCanal();
        startForeground(1, buildNotif("Mix.Casete", false));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String cmd = intent.getStringExtra(EXTRA_CMD);
        if (cmd == null) return START_STICKY;

        switch (cmd) {
            case "play_url":
                String url = intent.getStringExtra(EXTRA_URL);
                String title = intent.getStringExtra(EXTRA_TITLE);
                if (title != null) currentTitle = title;
                startPlayback(url);
                break;
            case "play":
                if (player != null && prepared) {
                    player.start();
                    requestAudioFocus();
                    updateNotif(true);
                    notifyJs("playing");
                }
                break;
            case "pause":
                if (player != null && prepared) {
                    player.pause();
                    updateNotif(false);
                    notifyJs("paused");
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
        releasePlayer();
        requestAudioFocus();
        acquireWakeLock();

        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(url);
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
        updateNotif(true);
        notifyJs("playing");
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
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
        releasePlayer();
        releaseWakeLock();
        releaseAudioFocus();
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

    private void acquireWakeLock() {
        if (wl == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mixcasete:play");
        }
        if (!wl.isHeld()) wl.acquire(4 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wl != null && wl.isHeld()) wl.release();
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
        return b.setContentTitle(title)
                .setContentText(playing ? "▶ Reproduciendo" : "❚❚ En pausa")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pOpen)
                .setOngoing(playing)
                .addAction(playing
                        ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pausa" : "Seguir", pP)
                .addAction(android.R.drawable.ic_delete, "Parar", pS)
                .build();
    }

    private void updateNotif(boolean playing) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try { nm.notify(1, buildNotif(currentTitle, playing)); } catch (Exception e) {}
    }

    private void crearCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Reproducción", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Audio de Mix.Casete");
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
        stopPlayback();
        super.onDestroy();
    }
}
