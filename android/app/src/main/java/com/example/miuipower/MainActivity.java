package com.example.miuipower;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static volatile long lastFrontendReadyMs = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastForegroundResumeMs = 0L;
    private long lastWebViewReloadMs = 0L;
    private boolean hasResumedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(MiuiPowerPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        lastForegroundResumeMs = System.currentTimeMillis();
        if (hasResumedOnce) {
            scheduleFrontendReadyWatchdog(lastForegroundResumeMs);
        }
        hasResumedOnce = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    public static void markFrontendReady(String page) {
        lastFrontendReadyMs = System.currentTimeMillis();
    }

    private void scheduleFrontendReadyWatchdog(long resumeMs) {
        handler.postDelayed(() -> {
            if (resumeMs != lastForegroundResumeMs || lastFrontendReadyMs >= resumeMs) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastWebViewReloadMs < 5_000L) {
                return;
            }
            if (bridge == null || bridge.getWebView() == null) {
                return;
            }
            WebView webView = bridge.getWebView();
            if (webView.getProgress() < 100) {
                return;
            }
            lastWebViewReloadMs = now;
            webView.reload();
        }, 2_000L);
    }
}
