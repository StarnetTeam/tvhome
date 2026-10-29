package com.cleantv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Auto standby. The accessibility service sees every remote key press (in any app), remembers the
 * time of the last one and, once the chosen number of hours has passed with no key press, puts the
 * TV to sleep by locking the screen (Android 9+). Optionally it waits while audio or video plays.
 * The user has to switch it on once in the system Accessibility settings.
 */
public final class IdleService extends AccessibilityService {
    static volatile long lastInput = System.currentTimeMillis();
    static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable check = new Runnable() {
        @Override public void run() {
            evaluate();
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = true;
        lastInput = System.currentTimeMillis();
        handler.removeCallbacks(check);
        handler.postDelayed(check, 60_000L);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        lastInput = System.currentTimeMillis();
        return false;   // never consume the key: the app in front still gets it
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        running = false;
        handler.removeCallbacks(check);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(check);
        super.onDestroy();
    }

    private void evaluate() {
        int hours = Prefs.standby(this);
        if (hours <= 0 || Build.VERSION.SDK_INT < 28) return;
        long idle = System.currentTimeMillis() - lastInput;
        if (idle < hours * 3_600_000L) return;
        if (Prefs.keepAudio(this)) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) return;   // something is playing: try again in a minute
        }
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        lastInput = System.currentTimeMillis();
    }
}
