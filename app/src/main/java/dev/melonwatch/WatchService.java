package dev.melonwatch;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.session.*;
import android.os.*;
import android.widget.Toast;
import java.util.*;

public final class WatchService extends Service {
    static final String STOP = "dev.melonwatch.STOP";
    static final String PAUSE = "dev.melonwatch.PAUSE";
    static final String RESUME = "dev.melonwatch.RESUME";
    static volatile boolean running;
    static volatile boolean alarmActive;
    static volatile int vibrationCount;
    static volatile String status = "감시 꺼짐";
    private static final String CHANNEL = "watch_status";
    static final String ALERT_CHANNEL = "playback_stopped";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ReminderPolicy policy = new ReminderPolicy();
    private final VibrationBudget budget = new VibrationBudget();
    private final ManualPause manualPause = new ManualPause();
    private boolean finishingForLimit;
    private String endStatus = "감시 꺼짐";
    private PowerManager.WakeLock wakeLock;
    private MediaSessionManager sessions;
    private InterruptionMonitor interruptions;
    private long renewWakeAt;
    private String shown = "";
    private Set<String> targetPackages;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (finishingForLimit) { pauseForInterruption(); return; }
            if (SystemClock.elapsedRealtime() >= renewWakeAt) renewWakeLock();
            checkPlayback();
            if (running) handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        sessions = getSystemService(MediaSessionManager.class);
        interruptions = new InterruptionMonitor(this, handler);
        ensureChannels(this);
    }
    static void ensureChannels(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "감시 상태", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL, "음악 재생 어플 재생 중단", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("첫 진동 시 상단 알림과 팝업으로 재생 중단을 알립니다.");
        alerts.setSound(null, null);
        // Vibration is controlled by the monitor so repeats use the selected interval.
        alerts.enableVibration(false);
        context.getSystemService(NotificationManager.class).createNotificationChannel(alerts);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { finish(); return START_NOT_STICKY; }
        if (intent != null && (PAUSE.equals(intent.getAction()) || RESUME.equals(intent.getAction()))) {
            // Stale notification controls must never start a new monitoring session.
            if (!running) { stopSelf(); return START_NOT_STICKY; }
            if (finishingForLimit) return START_NOT_STICKY;
            if (PAUSE.equals(intent.getAction())) {
                if (!manualPause.start(intent.getIntExtra("minutes", 5), SystemClock.elapsedRealtime()))
                    return START_NOT_STICKY;
            } else manualPause.clear();
            policy.reset(); cancelVibration(); clearAlert();
            checkPlayback();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        status = "음악 재생 어플 재생 상태 확인 중";
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification(status), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else { startForeground(1, notification(status)); }
        running = true;
        finishingForLimit = false;
        manualPause.clear();
        endStatus = "감시 꺼짐";
        budget.reset();
        budget.setLimit(AppSettings.maxVibrations(this));
        vibrationCount = 0;
        getSystemService(NotificationManager.class).cancel(3);
        targetPackages = AppSettings.targetPackages(this);
        clearAlert();
        policy.reset();
        // Keeps the timer alive while the user explicitly enables monitoring.
        // Doze exemption is still required on devices which suspend wake locks in idle.
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MelonWatch:monitor");
        wakeLock.setReferenceCounted(false);
        renewWakeLock();
        handler.post(tick);
        Toast.makeText(this, "음악 재생 어플 감시를 시작합니다.", Toast.LENGTH_LONG).show();
        return START_NOT_STICKY;
    }
    private void renewWakeLock() {
        // A stalled monitor cannot hold the CPU awake indefinitely.
        wakeLock.acquire(10 * 60_000L);
        renewWakeAt = SystemClock.elapsedRealtime() + 60_000L;
    }
    private void checkPlayback() {
        Set<String> selected = AppSettings.targetPackages(this);
        if (!selected.equals(targetPackages)) {
            targetPackages = selected;
            policy.reset(); cancelVibration(); clearAlert();
        }
        budget.setLimit(AppSettings.maxVibrations(this));
        if (budget.exhausted()) { finishAtLimit(); return; }
        policy.configure(AppSettings.delay(this), AppSettings.interval(this));
        if (!policy.isAlerting()) clearAlert();
        if (!MainActivity.hasAccess(this)) { unknown("알림 접근 권한이 꺼졌습니다 · 감시 종료"); finish(); return; }
        if (pauseForInterruption()) return;
        if (!MediaAccessService.connected) { unknown("상태 확인 불가 · 알림 접근 연결 대기"); return; }
        try {
            List<MediaController> controllers = sessions.getActiveSessions(new ComponentName(this, MediaAccessService.class));
            Map<String, ReminderPolicy.State> appStates = new LinkedHashMap<>();
            List<String> stoppedNames = new ArrayList<>();
            for (String pkg : targetPackages) {
                List<ReminderPolicy.State> sessionStates = new ArrayList<>();
                for (MediaController c : controllers) {
                    if (!pkg.equals(c.getPackageName())) continue;
                    PlaybackState state = c.getPlaybackState();
                    sessionStates.add(state == null || state.getState() == PlaybackState.STATE_NONE
                        ? ReminderPolicy.State.UNKNOWN
                        : state.getState() == PlaybackState.STATE_PLAYING
                            ? ReminderPolicy.State.PLAYING : ReminderPolicy.State.NOT_PLAYING);
                }
                ReminderPolicy.State state = SelectedPlaybackPolicy.appState(sessionStates);
                appStates.put(pkg, state);
                if (state == ReminderPolicy.State.NOT_PLAYING) stoppedNames.add(AppSettings.appName(this, pkg));
            }
            ReminderPolicy.State combined = SelectedPlaybackPolicy.combinedState(appStates.values());
            if (combined == ReminderPolicy.State.PLAYING) {
                policy.update(ReminderPolicy.State.PLAYING, SystemClock.elapsedRealtime());
                cancelVibration();
                clearAlert();
                show("선택한 모든 음악 재생 어플 재생 중 · 정상");
            } else if (combined == ReminderPolicy.State.NOT_PLAYING) {
                boolean alert = policy.update(ReminderPolicy.State.NOT_PLAYING, SystemClock.elapsedRealtime());
                String phase = policy.isAlerting() ? getString(R.string.alert_status, AppSettings.interval(this))
                    : getString(R.string.stopped_status, AppSettings.delay(this), AppSettings.interval(this));
                show(getString(R.string.selected_stopped_status, phase, android.text.TextUtils.join(", ", stoppedNames)));
                if (alert) {
                    if (!budget.take()) { finishAtLimit(); return; }
                    vibrationCount = budget.used();
                    if (!alarmActive) postPlaybackAlert();
                    alarmActive = true;
                    vibrate(this);
                    if (budget.exhausted()) {
                        finishingForLimit = true;
                        // Let the final two-pulse pattern complete before releasing the service.
                        handler.postDelayed(() -> finishAtLimit(), 1100);
                    }
                }
            } else {
                unknown("선택한 앱의 재생 상태 확인 불가 · 진동 보류");
            }
        } catch (SecurityException e) { unknown("접근 권한 확인 필요 · 진동 보류"); }
          catch (RuntimeException e) { unknown("재생 상태 연결 오류 · 다시 확인 중"); }
    }
    private void unknown(String text) { policy.reset(); cancelVibration(); clearAlert(); show(text); }
    private boolean pauseForInterruption() {
        InterruptionPolicy.Reason reason = interruptions.snapshot();
        long seconds = manualPause.secondsRemaining(SystemClock.elapsedRealtime());
        if (seconds > 0) {
            unknown("감시 보류 · 수동 일시정지 " + seconds + "초 남음" +
                (reason == InterruptionPolicy.Reason.NONE ? "" : " · 자동 예외도 적용 중"));
            return true;
        }
        switch (reason) {
            case RECORDING: unknown("감시 보류 · 녹음 감지"); return true;
            case CALL_AND_RECORDING: unknown("감시 보류 · 통화 및 녹음 감지"); return true;
            case CAMERA_AND_RECORDING: unknown("감시 보류 · 카메라 및 녹음 감지"); return true;
            case ALL: unknown("감시 보류 · 통화·카메라·녹음 감지"); return true;
            case CALL: unknown("감시 보류 · 통화 감지"); return true;
            case CAMERA: unknown("감시 보류 · 카메라 사용 감지"); return true;
            case CALL_AND_CAMERA: unknown("감시 보류 · 통화 및 카메라 사용 감지"); return true;
            case UNAVAILABLE: unknown("감시 보류 · 예외 상태 확인 불가 (예외 설정 확인)"); return true;
            default: return false;
        }
    }
    private void clearAlert() {
        alarmActive = false;
        getSystemService(NotificationManager.class).cancel(2);
    }
    private void postPlaybackAlert() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, WatchService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String message = status;
        Notification alert = new Notification.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_watch).setContentTitle("음악 재생 어플 재생 중단")
            .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setColor(0xFFD32F2F).setContentIntent(open).setOnlyAlertOnce(true)
            .setOngoing(true).setCategory(Notification.CATEGORY_REMINDER)
            .addAction(new Notification.Action.Builder(null, "일시정지 / 재개", pauseControls()).build())
            .addAction(new Notification.Action.Builder(null, "감시 종료", stop).build()).build();
        getSystemService(NotificationManager.class).notify(2, alert);
    }
    private void show(String text) {
        status = text;
        if (!text.equals(shown)) {
            shown = text;
            getSystemService(NotificationManager.class).notify(1, notification(text));
        }
    }
    private PendingIntent pauseControls() {
        Intent intent = new Intent(this, MainActivity.class).putExtra("pause_controls", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 4, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, WatchService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(dev.melonwatch.R.drawable.ic_watch)
            .setContentTitle("음악 재생 어플 지킴이 · 감시 중").setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null, "일시정지 / 재개", pauseControls()).build())
            .addAction(new Notification.Action.Builder(null, "감시 종료", stop).build()).build();
    }
    static void vibrate(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 450, 200, 450}, -1),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        }
    }
    private void cancelVibration() { Vibrator v = getSystemService(Vibrator.class); if (v != null) v.cancel(); }
    private void finishAtLimit() {
        endStatus = getString(R.string.limit_reached, budget.used());
        finish();
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        getSystemService(NotificationManager.class).notify(3, new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_watch).setContentTitle("최대 진동 횟수 도달")
            .setContentText(endStatus).setStyle(new Notification.BigTextStyle().bigText(endStatus))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build());
    }
    private void finish() {
        running = false;
        manualPause.clear();
        handler.removeCallbacksAndMessages(null);
        if (interruptions != null) interruptions.close();
        policy.reset();
        cancelVibration();
        clearAlert();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        status = endStatus;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy() { finish(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
