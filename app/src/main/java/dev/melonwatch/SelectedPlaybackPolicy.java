package dev.melonwatch;

import java.util.Collection;

/** Any playing session makes its app playing; every selected app must then be playing. */
public final class SelectedPlaybackPolicy {
    public static ReminderPolicy.State appState(Collection<ReminderPolicy.State> sessions) {
        boolean unknown = false;
        for (ReminderPolicy.State state : sessions) {
            if (state == ReminderPolicy.State.PLAYING) return state;
            if (state == ReminderPolicy.State.UNKNOWN) unknown = true;
        }
        return unknown ? ReminderPolicy.State.UNKNOWN : ReminderPolicy.State.NOT_PLAYING;
    }
    public static ReminderPolicy.State combinedState(Collection<ReminderPolicy.State> apps) {
        if (apps.isEmpty()) return ReminderPolicy.State.UNKNOWN;
        boolean unknown = false;
        for (ReminderPolicy.State state : apps) {
            if (state == ReminderPolicy.State.NOT_PLAYING) return state;
            if (state == ReminderPolicy.State.UNKNOWN) unknown = true;
        }
        return unknown ? ReminderPolicy.State.UNKNOWN : ReminderPolicy.State.PLAYING;
    }
}
