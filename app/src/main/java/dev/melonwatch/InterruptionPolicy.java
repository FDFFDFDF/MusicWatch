package dev.melonwatch;

/** Combine independent interruption signals without allowing one to clear the other. */
public final class InterruptionPolicy {
    public enum Signal { CLEAR, ACTIVE, UNKNOWN }
    public enum Reason { NONE, CALL, CAMERA, CALL_AND_CAMERA, RECORDING, CALL_AND_RECORDING, CAMERA_AND_RECORDING, ALL, UNAVAILABLE }

    public static Reason reason(boolean callEnabled, Signal call, boolean cameraEnabled, Signal camera) {
        return reason(callEnabled, call, cameraEnabled, camera, false, Signal.CLEAR);
    }
    public static Reason reason(boolean callEnabled, Signal call, boolean cameraEnabled, Signal camera,
                                boolean recordingEnabled, Signal recording) {
        boolean inRecording = recordingEnabled && recording == Signal.ACTIVE;
        boolean inCall = callEnabled && call == Signal.ACTIVE;
        boolean inCamera = cameraEnabled && camera == Signal.ACTIVE;
        if (inRecording) {
            if (inCall && inCamera) return Reason.ALL;
            if (inCall) return Reason.CALL_AND_RECORDING;
            if (inCamera) return Reason.CAMERA_AND_RECORDING;
            return Reason.RECORDING;
        }
        if (inCall && inCamera) return Reason.CALL_AND_CAMERA;
        if (inCall) return Reason.CALL;
        if (inCamera) return Reason.CAMERA;
        if ((callEnabled && call == Signal.UNKNOWN) || (cameraEnabled && camera == Signal.UNKNOWN)
                || (recordingEnabled && recording == Signal.UNKNOWN))
            return Reason.UNAVAILABLE;
        return Reason.NONE;
    }
}
