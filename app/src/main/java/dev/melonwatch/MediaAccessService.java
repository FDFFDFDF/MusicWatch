package dev.melonwatch;

import android.service.notification.NotificationListenerService;

/** Access grant only. Notification contents are never read or stored. */
public final class MediaAccessService extends NotificationListenerService {
    static volatile boolean connected;
    @Override public void onListenerConnected() { connected = true; }
    @Override public void onListenerDisconnected() { connected = false; }
    @Override public void onDestroy() { connected = false; super.onDestroy(); }
}
