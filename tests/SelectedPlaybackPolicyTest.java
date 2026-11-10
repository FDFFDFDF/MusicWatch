package dev.melonwatch;

import java.util.Arrays;
import java.util.Collections;

/** Prepared for the next authorized build; not executed during the source-only update. */
public final class SelectedPlaybackPolicyTest {
    public static void main(String[] args) {
        var playing = ReminderPolicy.State.PLAYING;
        var stopped = ReminderPolicy.State.NOT_PLAYING;
        var unknown = ReminderPolicy.State.UNKNOWN;
        check(SelectedPlaybackPolicy.combinedState(Arrays.asList(playing, playing)) == playing);
        check(SelectedPlaybackPolicy.combinedState(Arrays.asList(playing, stopped)) == stopped);
        check(SelectedPlaybackPolicy.combinedState(Arrays.asList(stopped, playing)) == stopped);
        check(SelectedPlaybackPolicy.combinedState(Arrays.asList(stopped, stopped)) == stopped);
        check(SelectedPlaybackPolicy.combinedState(Arrays.asList(playing, unknown)) == unknown);
        check(SelectedPlaybackPolicy.combinedState(Arrays.asList(unknown, stopped)) == stopped);
        check(SelectedPlaybackPolicy.combinedState(Collections.emptyList()) == unknown);
        check(SelectedPlaybackPolicy.combinedState(Collections.singletonList(playing)) == playing);
        check(SelectedPlaybackPolicy.appState(Collections.emptyList()) == stopped);
        check(SelectedPlaybackPolicy.appState(Arrays.asList(stopped, playing)) == playing);
        check(SelectedPlaybackPolicy.appState(Arrays.asList(unknown, playing)) == playing);
        check(SelectedPlaybackPolicy.appState(Arrays.asList(unknown, stopped)) == unknown);
        check(SelectedPlaybackPolicy.appState(Collections.singletonList(stopped)) == stopped);
        System.out.println("PASS: 13 multi-app cases");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Multi-app rule failed"); }
}
