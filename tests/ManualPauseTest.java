package dev.melonwatch;

/** Regression cases for timed manual pauses. */
public final class ManualPauseTest {
    public static void main(String[] args) {
        ManualPause pause = new ManualPause();
        for (int minutes : new int[]{1, 5, 15}) {
            check(pause.start(minutes, 1000));
            check(pause.secondsRemaining(1000) == minutes * 60);
            check(pause.secondsRemaining(1000 + minutes * 60_000L - 1) == 1);
            check(pause.secondsRemaining(1000 + minutes * 60_000L) == 0);
        }
        pause.start(15, 1000);
        check(!pause.start(0, 2000));
        check(pause.secondsRemaining(2000) == 899);
        pause.start(1, 2000);
        check(pause.secondsRemaining(2000) == 60);
        pause.clear();
        check(pause.secondsRemaining(2000) == 0);
        ReminderPolicy timer = new ReminderPolicy();
        VibrationBudget budget = new VibrationBudget();
        timer.update(ReminderPolicy.State.NOT_PLAYING, 0);
        check(timer.update(ReminderPolicy.State.NOT_PLAYING, 10_000));
        check(budget.take());
        pause.start(1, 10_000);
        timer.reset();
        check(pause.secondsRemaining(69_999) == 1);
        check(pause.secondsRemaining(70_000) == 0);
        check(!timer.update(ReminderPolicy.State.NOT_PLAYING, 70_000));
        check(!timer.update(ReminderPolicy.State.NOT_PLAYING, 79_999));
        check(timer.update(ReminderPolicy.State.NOT_PLAYING, 80_000));
        check(budget.used() == 1);
        System.out.println("PASS: manual pause checks");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
