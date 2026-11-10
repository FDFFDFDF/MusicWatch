package dev.melonwatch;

public final class ReminderPolicyTest {
    private static int count;
    private static void check(boolean value, String label) { count++; if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        ReminderPolicy p = new ReminderPolicy();
        var stopped = ReminderPolicy.State.NOT_PLAYING;
        var playing = ReminderPolicy.State.PLAYING;
        var unknown = ReminderPolicy.State.UNKNOWN;
        check(!p.update(stopped, 0), "No immediate vibration");
        check(!p.update(stopped, 9999), "Wait full 10 seconds");
        check(!p.isAlerting(), "No red state before first vibration");
        check(p.update(stopped, 10000), "First alert at 10 seconds");
        check(p.isAlerting(), "Red state begins with first vibration");
        check(!p.update(stopped, 11999), "No early repeat");
        check(p.isAlerting(), "Red state persists between vibrations");
        check(p.update(stopped, 12000), "Default repeat after 2 seconds");
        check(!p.update(playing, 41000), "Resume resets timer");
        check(!p.isAlerting(), "Resume clears alert state");
        check(!p.update(stopped, 42000), "New stop starts fresh");
        check(!p.update(stopped, 51999), "No carryover");
        check(p.update(stopped, 52000), "New stop alerts");
        check(!p.update(unknown, 53000), "Unknown suppresses alert");
        check(!p.isAlerting(), "Unknown clears alert state");
        check(!p.update(stopped, 100000), "Permission recovery starts fresh");
        check(p.update(stopped, 110000), "Recovery alert");
        check(p.update(stopped, 1000000), "Late execution emits one alert");
        check(!p.update(stopped, 1000001), "No catch-up burst");
        p.reset();
        check(!p.update(stopped, 2000000), "Manual stop resets policy");
        p.configure(2, 5);
        check(!p.update(stopped, 3000000), "Custom settings reset timer");
        p.configure(2, 5);
        check(p.update(stopped, 3002000), "Unchanged settings preserve timer");
        check(!p.update(stopped, 3006999), "Custom interval waits");
        check(p.update(stopped, 3007000), "Custom interval repeats");
        p.configure(1, 1);
        check(!p.isAlerting(), "Settings change clears alert state");
        check(!p.update(stopped, 3008000), "Changing settings restarts delay");
        check(p.update(stopped, 3009000), "Minimum delay works");
        check(p.update(stopped, 3010000), "Minimum repeat works");
        p.configure(3600, 3600);
        check(!p.update(stopped, 4000000), "Maximum delay starts");
        check(!p.update(stopped, 7599999), "Maximum delay waits");
        check(p.update(stopped, 7600000), "Maximum delay fires");
        check(!p.update(stopped, 11199999), "Maximum repeat waits");
        check(p.update(stopped, 11200000), "Maximum repeat fires");
        for (int[] values : new int[][]{{0, 30}, {10, 0}, {-1, 30}, {3601, 30}, {10, 3601}}) {
            boolean rejected = false;
            try { p.configure(values[0], values[1]); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "Invalid timing rejected");
        }
        VibrationBudget budget = new VibrationBudget();
        check(!budget.exhausted(), "Fresh budget is available");
        for (int i = 0; i < 20; i++) check(budget.take(), "Default 20 patterns allowed");
        check(budget.used() == 20 && budget.exhausted(), "Default cap reached exactly");
        check(!budget.take() && budget.used() == 20, "No extra vibration beyond cap");
        budget.reset(); budget.setLimit(1);
        check(budget.take() && budget.exhausted(), "Limit one permits one complete pattern");
        budget.reset(); budget.setLimit(5);
        budget.take(); budget.take();
        budget.setLimit(2);
        check(budget.exhausted(), "Lowering cap stops an already exhausted session");
        budget.setLimit(4);
        check(budget.used() == 2 && budget.take() && budget.take() && !budget.take(), "Changing cap retains cumulative usage");
        budget.reset(); budget.setLimit(1000);
        for (int i = 0; i < 1000; i++) if (!budget.take()) throw new AssertionError("Maximum cap prematurely exhausted");
        check(budget.exhausted() && !budget.take(), "Maximum cap enforced");
        for (int invalid : new int[]{0, -1, 1001}) {
            boolean rejected = false;
            try { budget.setLimit(invalid); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "Invalid cap rejected");
        }
        budget.reset();
        check(budget.used() == 0 && !budget.exhausted(), "New monitoring session restores budget");
        System.out.println("PASS: " + count + " timing/state/budget checks");
    }
}
