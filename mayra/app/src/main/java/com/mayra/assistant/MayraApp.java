package com.mayra.assistant;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class MayraApp extends Application {

    public static final String CH_LISTEN = "mayra_listen";
    public static final String CH_WAKE = "mayra_wake";

    /** True while MainActivity is on screen; ClapService releases the mic then so voice input can use it. */
    public static volatile boolean uiVisible = false;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel listen = new NotificationChannel(CH_LISTEN, "Clap listening", NotificationManager.IMPORTANCE_LOW);
        listen.setDescription("হাততালি শোনার সময় দেখানো নোটিফিকেশন");
        listen.setShowBadge(false);
        NotificationChannel wake = new NotificationChannel(CH_WAKE, "Assistant wake-up", NotificationManager.IMPORTANCE_HIGH);
        wake.setDescription("হাততালি শুনে MAYRA চালু হলে");
        nm.createNotificationChannel(listen);
        nm.createNotificationChannel(wake);
    }
}
