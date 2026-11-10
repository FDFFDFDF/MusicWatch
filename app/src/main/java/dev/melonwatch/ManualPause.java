package dev.melonwatch;

/** Monotonic, session-only pause. Does not own or reset the vibration budget. */
public final class ManualPause {
    private long until;
    public boolean start(int minutes, long now) {
        if (minutes != 1 && minutes != 5 && minutes != 15) return false;
        until = now + minutes * 60_000L;
        return true;
    }
    public long secondsRemaining(long now) { return Math.max(0, (until - now + 999) / 1000); }
    public void clear() { until = 0; }
}
