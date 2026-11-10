package dev.melonwatch;

/** Counts reminder patterns across one monitoring session, including playback resumptions. */
public final class VibrationBudget {
    private int used;
    private int limit = 20;
    public void setLimit(int value) {
        if (value < 1 || value > 1000) throw new IllegalArgumentException("Limit must be 1..1000");
        limit = value;
    }
    public boolean take() {
        if (exhausted()) return false;
        used++;
        return true;
    }
    public boolean exhausted() { return used >= limit; }
    public int used() { return used; }
    public void reset() { used = 0; }
}
