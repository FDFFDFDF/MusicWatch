package dev.melonwatch;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Reads availability only. Never opens a camera, records audio, or reads phone identity. */
final class InterruptionMonitor {
    private final Context context;
    private final Handler handler;
    private final AudioManager audio;
    private final CameraManager camera;
    private final Set<String> knownIds = new HashSet<>();
    private final Set<String> pendingIds = new HashSet<>();
    private final Set<String> unavailableIds = new HashSet<>();
    private boolean registered;
    private boolean cameraFailed;
    private long nextCameraRefresh;
    private long nextRegisterAttempt;

    private final CameraManager.AvailabilityCallback callback = new CameraManager.AvailabilityCallback() {
        @Override public void onCameraAvailable(String id) {
            if (!registered) return;
            knownIds.add(id);
            pendingIds.remove(id);
            unavailableIds.remove(id);
        }
        @Override public void onCameraUnavailable(String id) {
            if (!registered) return;
            knownIds.add(id);
            pendingIds.remove(id);
            unavailableIds.add(id);
        }
    };

    InterruptionMonitor(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        audio = context.getSystemService(AudioManager.class);
        camera = context.getSystemService(CameraManager.class);
    }

    InterruptionPolicy.Reason snapshot() {
        boolean calls = AppSettings.pauseForCalls(context);
        boolean cameras = AppSettings.pauseForCamera(context);
        InterruptionPolicy.Signal callSignal = calls ? callSignal() : InterruptionPolicy.Signal.CLEAR;
        InterruptionPolicy.Signal cameraSignal = InterruptionPolicy.Signal.CLEAR;
        if (cameras) {
            if (!registered && SystemClock.elapsedRealtime() >= nextRegisterAttempt) registerCamera();
            if (registered && SystemClock.elapsedRealtime() >= nextCameraRefresh) refreshCameraIds();
            cameraSignal = !unavailableIds.isEmpty() ? InterruptionPolicy.Signal.ACTIVE
                : cameraFailed || !registered || !pendingIds.isEmpty()
                    ? InterruptionPolicy.Signal.UNKNOWN : InterruptionPolicy.Signal.CLEAR;
        } else {
            close();
        }
        boolean recording = AppSettings.pauseForRecording(context);
        return InterruptionPolicy.reason(calls, callSignal, cameras, cameraSignal,
            recording, recording ? recordingSignal() : InterruptionPolicy.Signal.CLEAR);
    }

    private InterruptionPolicy.Signal callSignal() {
        if (audio == null) return InterruptionPolicy.Signal.UNKNOWN;
        try {
            int mode = audio.getMode();
            boolean active = mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_IN_COMMUNICATION;
            if (Build.VERSION.SDK_INT >= 30) active |= mode == AudioManager.MODE_CALL_SCREENING;
            if (Build.VERSION.SDK_INT >= 33) active |= mode == AudioManager.MODE_CALL_REDIRECT
                || mode == AudioManager.MODE_COMMUNICATION_REDIRECT;
            return active ? InterruptionPolicy.Signal.ACTIVE : InterruptionPolicy.Signal.CLEAR;
        } catch (RuntimeException e) {
            return InterruptionPolicy.Signal.UNKNOWN;
        }
    }

    private InterruptionPolicy.Signal recordingSignal() {
        if (audio == null) return InterruptionPolicy.Signal.UNKNOWN;
        try {
            // API 24+: inspect activity only, never capture or retain audio or app identities.
            return audio.getActiveRecordingConfigurations().isEmpty()
                ? InterruptionPolicy.Signal.CLEAR : InterruptionPolicy.Signal.ACTIVE;
        } catch (RuntimeException e) {
            return InterruptionPolicy.Signal.UNKNOWN;
        }
    }

    private void registerCamera() {
        nextRegisterAttempt = SystemClock.elapsedRealtime() + 5000;
        if (camera == null) { cameraFailed = true; return; }
        refreshCameraIds();
        if (cameraFailed) return;
        try {
            registered = true;
            camera.registerAvailabilityCallback(callback, handler);
        } catch (RuntimeException e) {
            registered = false;
            cameraFailed = true;
        }
    }

    private void refreshCameraIds() {
        nextCameraRefresh = SystemClock.elapsedRealtime() + 5000;
        try {
            Set<String> current = new HashSet<>(Arrays.asList(camera.getCameraIdList()));
            // Removed external cameras must not leave the monitor paused forever.
            unavailableIds.retainAll(current);
            pendingIds.retainAll(current);
            for (String id : current) if (!knownIds.contains(id)) pendingIds.add(id);
            knownIds.clear(); knownIds.addAll(current);
            cameraFailed = false;
        } catch (CameraAccessException | RuntimeException e) {
            cameraFailed = true;
        }
    }

    void close() {
        if (registered && camera != null) {
            try { camera.unregisterAvailabilityCallback(callback); } catch (RuntimeException ignored) { }
        }
        registered = false;
        cameraFailed = false;
        nextRegisterAttempt = 0;
        nextCameraRefresh = 0;
        knownIds.clear(); pendingIds.clear(); unavailableIds.clear();
    }
}
