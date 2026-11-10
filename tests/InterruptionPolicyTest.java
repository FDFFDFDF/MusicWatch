package dev.melonwatch;

/** Regression cases for interruption signals and reminder state. */
public final class InterruptionPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        var clear = InterruptionPolicy.Signal.CLEAR;
        var active = InterruptionPolicy.Signal.ACTIVE;
        var unknown = InterruptionPolicy.Signal.UNKNOWN;
        check(InterruptionPolicy.reason(true, clear, true, clear) == InterruptionPolicy.Reason.NONE);
        check(InterruptionPolicy.reason(true, active, true, clear) == InterruptionPolicy.Reason.CALL);
        check(InterruptionPolicy.reason(true, clear, true, active) == InterruptionPolicy.Reason.CAMERA);
        check(InterruptionPolicy.reason(true, active, true, active) == InterruptionPolicy.Reason.CALL_AND_CAMERA);
        check(InterruptionPolicy.reason(true, clear, true, active) != InterruptionPolicy.Reason.NONE);
        check(InterruptionPolicy.reason(true, active, true, clear) != InterruptionPolicy.Reason.NONE);
        check(InterruptionPolicy.reason(false, active, true, clear) == InterruptionPolicy.Reason.NONE);
        check(InterruptionPolicy.reason(true, clear, false, active) == InterruptionPolicy.Reason.NONE);
        check(InterruptionPolicy.reason(false, unknown, false, unknown) == InterruptionPolicy.Reason.NONE);
        check(InterruptionPolicy.reason(true, unknown, true, clear) == InterruptionPolicy.Reason.UNAVAILABLE);
        check(InterruptionPolicy.reason(true, clear, true, unknown) == InterruptionPolicy.Reason.UNAVAILABLE);
        check(InterruptionPolicy.reason(true, active, true, unknown) == InterruptionPolicy.Reason.CALL);

        check(InterruptionPolicy.reason(true, clear, true, clear, true, active) == InterruptionPolicy.Reason.RECORDING);
        check(InterruptionPolicy.reason(true, active, true, clear, true, active) == InterruptionPolicy.Reason.CALL_AND_RECORDING);
        check(InterruptionPolicy.reason(true, clear, true, active, true, active) == InterruptionPolicy.Reason.CAMERA_AND_RECORDING);
        check(InterruptionPolicy.reason(true, active, true, active, true, active) == InterruptionPolicy.Reason.ALL);
        check(InterruptionPolicy.reason(true, clear, true, clear, false, active) == InterruptionPolicy.Reason.NONE);
        check(InterruptionPolicy.reason(true, clear, true, clear, true, unknown) == InterruptionPolicy.Reason.UNAVAILABLE);
        check(InterruptionPolicy.reason(true, unknown, true, clear, true, active) == InterruptionPolicy.Reason.RECORDING);
        check(InterruptionPolicy.reason(true, unknown, true, clear, true, clear) == InterruptionPolicy.Reason.UNAVAILABLE);
        check(InterruptionPolicy.reason(true, clear, true, clear, true, clear) == InterruptionPolicy.Reason.NONE);
        ReminderPolicy timer = new ReminderPolicy();
        VibrationBudget budget = new VibrationBudget();
        var stopped = ReminderPolicy.State.NOT_PLAYING;
        check(!timer.update(stopped, 0));
        check(timer.update(stopped, 10_000));
        check(budget.take());
        timer.reset(); // Exception entry cancels reminders without resetting cumulative budget.
        check(!timer.isAlerting());
        check(budget.used() == 1);
        timer.reset(); // Still paused: pending time must not accumulate.
        check(!timer.update(stopped, 120_000));
        check(!timer.update(stopped, 129_999));
        check(timer.update(stopped, 130_000));
        check(budget.take() && budget.used() == 2);
        System.out.println("PASS: " + checks + " interruption checks");
    }
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Interruption case " + checks);
    }
}
