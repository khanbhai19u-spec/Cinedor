package com.mayra.assistant;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Foreground service that listens for a double clap and then opens MAYRA in voice mode.
 * The microphone is released while MAYRA is on screen so speech input can use it.
 */
public class ClapService extends Service {

    public static final String ACTION_STOP = "com.mayra.assistant.STOP_CLAP";
    private static final int NOTIF_ID = 7;
    private static final int WAKE_NOTIF_ID = 8;
    private static final int RATE = 16000;

    private volatile boolean running;
    private Thread worker;

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, ClapService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, ClapService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            new Prefs(this).setClapEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = listeningNotification();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIF_ID, n);
        } catch (Exception e) {
            // Android refuses to start a microphone service from the background; the user must open the app.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            worker = new Thread(this::loop, "clap-listener");
            worker.start();
        }
        return START_STICKY;
    }

    private Notification listeningNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ClapService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, MayraApp.CH_LISTEN)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("MAYRA শুনছে 👏👏")
                .setContentText("দুইবার হাততালি দিলেই MAYRA চালু হবে")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "বন্ধ করুন", stop).build())
                .setOngoing(true)
                .build();
    }

    @SuppressWarnings("MissingPermission")
    private void loop() {
        int minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        short[] frame = new short[512];  // 32 ms
        AudioRecord rec = null;
        ClapDetector detector = null;
        int sens = -1;
        while (running) {
            try {
                if (MayraApp.uiVisible) {
                    if (rec != null) { rec.stop(); rec.release(); rec = null; }
                    Thread.sleep(400);
                    continue;
                }
                int s = new Prefs(this).clapSensitivity();
                if (detector == null || s != sens) { detector = new ClapDetector(RATE, s); sens = s; }
                if (rec == null) {
                    rec = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, 4096) * 2);
                    if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                        rec.release(); rec = null;
                        Thread.sleep(2000);
                        continue;
                    }
                    rec.startRecording();
                }
                int n = rec.read(frame, 0, frame.length);
                if (n <= 0) { Thread.sleep(50); continue; }
                if (detector.process(frame, n)) {
                    rec.stop(); rec.release(); rec = null;
                    detector = null;
                    wakeAssistant();
                    Thread.sleep(3000);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                if (rec != null) { try { rec.release(); } catch (Exception ignored) { } rec = null; }
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
            }
        }
        if (rec != null) { try { rec.stop(); rec.release(); } catch (Exception ignored) { } }
    }

    @SuppressWarnings("deprecation")
    private void wakeAssistant() {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP, "mayra:clap");
        wl.acquire(5000);

        Intent i = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_VOICE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Settings.canDrawOverlays(this)) {
            // "Display over other apps" lets us open the activity directly from the background.
            startActivity(i);
            return;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, MayraApp.CH_WAKE)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("👏 MAYRA এখানে!")
                .setContentText("ট্যাপ করে বলুন")
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(15000)
                .build();
        getSystemService(NotificationManager.class).notify(WAKE_NOTIF_ID, n);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
