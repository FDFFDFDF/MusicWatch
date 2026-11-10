package dev.melonwatch;

/** Pure state machine using monotonic milliseconds, independent of Android. */
public final class ReminderPolicy {
    public enum State { PLAYING, NOT_PLAYING, UNKNOWN }
    private long stoppedAt = -1;
    private long lastAlert = -1;
    private long delayMs = 10_000;
    private long intervalMs = 2_000;
    public void configure(int delaySeconds, int intervalSeconds) {
        if (delaySeconds < 1 || delaySeconds > 3600 || intervalSeconds < 1 || intervalSeconds > 3600)
            throw new IllegalArgumentException("Seconds must be between 1 and 3600");
        long delay = delaySeconds * 1000L, interval = intervalSeconds * 1000L;
        if (delay != delayMs || interval != intervalMs) {
            delayMs = delay; intervalMs = interval; reset();
        }
    }
    public boolean update(State state, long now) {
        if (state != State.NOT_PLAYING) { reset(); return false; }
        if (stoppedAt < 0) stoppedAt = now;
        if (now - stoppedAt < delayMs) return false;
        if (lastAlert >= 0 && now - lastAlert < intervalMs) return false;
        lastAlert = now;
        return true;
    }
    public void reset() { stoppedAt = -1; lastAlert = -1; }
    public boolean isAlerting() { return lastAlert >= 0; }
}
