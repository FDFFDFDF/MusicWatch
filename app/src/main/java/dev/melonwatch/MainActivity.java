package dev.melonwatch;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.text.InputType;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView state, permissions;
    private Button start, stop, target, pause;
    private TextView timingSummary;
    private EditText delayInput, intervalInput, maxInput;
    private TextView vibrationCount;
    private boolean dark;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        int mode = AppSettings.theme(this);
        dark = mode == 2 || (mode == 0 && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(savedInstanceState);
        WatchService.ensureChannels(this);
        int background = dark ? 0xFF101A16 : 0xFFF4F8F5;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(28), dp(24), dp(28));
        body.setBackgroundColor(background);
        scroll.addView(body);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        label(body, "MUSIC WATCH", 13, 0xFF13845B);
        label(body, "음악 재생 어플 지킴이", 32, 0xFF16392D);
        label(body, "음악이 멈추면 진동으로 알려드려요.", 17, 0xFF51645C);
        state = label(body, "감시 꺼짐", 22, 0xFF13845B);
        state.setPadding(0, dp(24), 0, dp(18));
        timingSummary = label(body, "", 17, 0xFF243D32);
        updateTimingSummary();
        label(body, "감시할 앱", 21, 0xFF16392D);
        target = button(body, AppSettings.targetName(this), this::chooseTarget);
        button(body, "음악 재생 어플 열기", this::openSelectedApp);
        label(body, "멜론·지니뮤직을 함께 선택할 수 있어요. 선택한 앱이 모두 재생 중이어야 정상이며, 하나라도 재생하지 않으면 경고합니다.", 14, 0xFF51645C);
        start = button(body, "감시 시작", this::startWatch);
        stop = button(body, "감시 종료", () -> { stopService(new Intent(this, WatchService.class)); update(); });
        pause = button(body, "감시 일시정지 / 재개", this::choosePause);
        button(body, "진동 테스트", () -> { WatchService.vibrate(this); toast("진동이 없다면 방해 금지·알림 진동 설정을 확인하세요."); });
        label(body, "진동 시간 설정", 21, 0xFF16392D);
        delayInput = secondsInput(body, "첫 진동까지 대기 시간 (초)", AppSettings.delay(this), 1001);
        intervalInput = secondsInput(body, "반복 진동 간격 (초)", AppSettings.interval(this), 1002);
        maxInput = secondsInput(body, "최대 진동 횟수 (1~1,000회)", AppSettings.maxVibrations(this), 1003);
        label(body, "기본 20회. 짧게 두 번 울리는 한 묶음을 1회로 셉니다. 감시 시작 이후 누적 횟수가 한도에 도달하면 감시도 자동 종료합니다. 재생을 재개해도 횟수는 유지되며, 감시를 새로 시작하면 0회부터 셉니다.", 14, 0xFF51645C);
        vibrationCount = label(body, "", 16, 0xFF243D32);
        label(body, "각각 1~3,600초로 설정할 수 있어요. 감시 중 시간을 바꾸면 저장 시점부터 대기 시간을 다시 셉니다.", 14, 0xFF51645C);
        button(body, "진동 설정 저장", this::saveTiming);
        label(body, "자동 예외 처리", 21, 0xFF16392D);
        exceptionSwitch(body, "통화 감지 시 진동 보류", "pause_for_calls", AppSettings.pauseForCalls(this));
        exceptionSwitch(body, "카메라 사용 감지 시 진동 보류", "pause_for_camera", AppSettings.pauseForCamera(this));
        exceptionSwitch(body, "녹음 감지 시 진동 보류", "pause_for_recording", AppSettings.pauseForRecording(this));
        label(body, "통화·카메라 예외는 기본 켜짐, 녹음 예외는 기본 꺼짐이에요. 녹음 예외에는 음성 입력도 포함될 수 있어요. 감지 중에는 진동·경고를 보류하고, 모든 예외가 끝나면 첫 진동 대기 시간을 다시 셉니다. 누적 진동 횟수는 유지합니다. 변경은 즉시 저장됩니다.", 14, 0xFF51645C);
        label(body, "통화는 기기의 오디오 모드로 판단하므로 앱에 따라 감지되지 않을 수 있어요. 카메라 예외에는 촬영뿐 아니라 미리보기·QR 스캔도 포함될 수 있습니다. 예외 상태 확인 불가가 계속되면 해당 옵션을 끄고 확인해 주세요.", 14, 0xFF51645C);
        label(body, "화면 모드", 21, 0xFF16392D);
        RadioGroup modes = new RadioGroup(this);
        String[] choices = {"시스템 설정 따름 (기본)", "라이트 모드", "다크 모드"};
        for (int i = 0; i < choices.length; i++) {
            RadioButton radio = new RadioButton(this); radio.setId(2000 + i); radio.setText(choices[i]);
            radio.setMinHeight(dp(48)); modes.addView(radio);
        }
        modes.check(2000 + mode); body.addView(modes);
        modes.setOnCheckedChangeListener((group, checked) -> {
            int selected = checked - 2000;
            if (selected >= 0 && selected <= 2 && selected != AppSettings.theme(this)) {
                AppSettings.prefs(this).edit().putInt("theme", selected).apply();
                recreate();
            }
        });
        label(body, "처음 한 번 설정해 주세요", 21, 0xFF16392D);
        permissions = label(body, "", 14, 0xFF51645C);
        button(body, "① 알림 접근 허용", () -> open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(body, "② 알림 표시 허용", () -> {
            if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed()) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            else open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        button(body, "재생 중단 팝업 알림 설정", () -> {
            open(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, WatchService.ALERT_CHANNEL));
        });
        button(body, "③ 배터리 제한 확인", () -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        button(body, "배터리 최적화 예외 설정", () -> open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        label(body, "갤럭시: 설정 → 배터리 → 백그라운드 앱 사용 제한 → 절전 예외 앱에 ‘음악 재생 어플 지킴이’를 추가하세요. 메뉴 이름은 기기마다 다를 수 있어요.", 14, 0xFF51645C);
        label(body, "선택한 앱 중 하나라도 재생하지 않으면 대기 시간을 센 뒤 경고합니다. 재생 세션이 없는 앱도 재생 안 함으로 판단합니다. 상태 조회 자체가 실패하면 확인 불가로 구분합니다. 감시 중 선택을 변경하면 대기 시간을 다시 세며 누적 진동 횟수는 유지합니다.\n\n알림 접근은 음악 재생 어플 재생 상태 확인에만 사용합니다. 다른 알림 내용은 읽거나 저장하지 않으며, 인터넷으로 보내는 정보도 없습니다.\n\n화면이 꺼져도 감시하도록 실행을 유지하므로 배터리 사용량이 늘어납니다. 사용 후 감시를 종료하세요. 방해 금지 및 진동 설정에 따라 진동이 제한될 수 있습니다. 강제 종료·재부팅 후에는 다시 감시 시작을 눌러 주세요.\n\n개인 테스트용 0.7.0 · 음악 재생 어플 공식 앱이 아닙니다.", 14, 0xFF51645C);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
    }
    private void choosePause() {
        if (!WatchService.running) { toast("먼저 감시를 시작해 주세요."); return; }
        new AlertDialog.Builder(this).setTitle("감시 일시정지")
            .setItems(new String[]{"1분 일시정지", "5분 일시정지", "15분 일시정지", "수동 일시정지 해제"}, (dialog, which) -> {
                if (!WatchService.running) { toast("감시가 종료되었습니다."); return; }
                Intent intent = new Intent(this, WatchService.class)
                    .setAction(which == 3 ? WatchService.RESUME : WatchService.PAUSE);
                if (which != 3) intent.putExtra("minutes", new int[]{1, 5, 15}[which]);
                startService(intent);
                toast(which == 3 ? "수동 일시정지를 해제합니다. 자동 예외가 남아 있으면 계속 보류합니다." : "선택한 시간 동안 감시 경고를 보류합니다.");
            }).setNegativeButton("취소", null).show();
    }
    private TextView label(LinearLayout body, String text, int size, int color) {
        if (dark) color = color == 0xFF13845B ? 0xFF71DDAA : color == 0xFF51645C ? 0xFFB3C7BC : 0xFFE5F0E9;
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(10), 0, dp(10)); body.addView(t); return t;
    }
    private Button button(LinearLayout body, String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setMinHeight(dp(54));
        b.setOnClickListener(v -> action.run()); body.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private void exceptionSwitch(LinearLayout body, String title, String key, boolean enabled) {
        Switch toggle = new Switch(this);
        toggle.setText(title); toggle.setTextSize(16); toggle.setMinHeight(dp(56));
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> AppSettings.prefs(this).edit().putBoolean(key, checked).apply());
        body.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
    }
    private void chooseTarget() {
        String[] packages = AppSettings.SUPPORTED_PACKAGES;
        String[] names = new String[packages.length];
        boolean[] checked = new boolean[packages.length];
        java.util.Set<String> selected = AppSettings.targetPackages(this);
        for (int i = 0; i < packages.length; i++) {
            names[i] = AppSettings.appName(this, packages[i]);
            checked[i] = selected.contains(packages[i]);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("감시할 앱 선택 · 중복 선택 가능")
            .setMultiChoiceItems(names, checked, (d, which, enabled) -> checked[which] = enabled)
            .setPositiveButton("저장", null).setNegativeButton("취소", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            java.util.Set<String> chosen = new java.util.LinkedHashSet<>();
            for (int i = 0; i < packages.length; i++) if (checked[i]) chosen.add(packages[i]);
            if (chosen.isEmpty()) { toast("감시할 앱을 하나 이상 선택해 주세요."); return; }
            for (String pkg : chosen) {
                if (getPackageManager().getLaunchIntentForPackage(pkg) == null) {
                    toast(getString(R.string.install_selected_app, AppSettings.appName(this, pkg))); return;
                }
            }
            AppSettings.saveTargets(this, chosen);
            target.setText(AppSettings.targetName(this));
            toast("감시할 앱 선택을 저장했습니다.");
            dialog.dismiss();
        }));
        dialog.show();
    }
    private void openSelectedApp() {
        String[] packages = AppSettings.targetPackages(this).toArray(new String[0]);
        if (packages.length == 1) { launchApp(packages[0]); return; }
        String[] names = new String[packages.length];
        for (int i = 0; i < packages.length; i++) names[i] = AppSettings.appName(this, packages[i]);
        new AlertDialog.Builder(this).setTitle("열 앱 선택")
            .setItems(names, (d, which) -> launchApp(packages[which]))
            .setNegativeButton("취소", null).show();
    }
    private void launchApp(String pkg) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) toast(getString(R.string.install_selected_app, AppSettings.appName(this, pkg)));
        else open(launch);
    }
    private EditText secondsInput(LinearLayout body, String title, int value, int id) {
        TextView caption = label(body, title, 16, 0xFF243D32);
        EditText input = new EditText(this); input.setId(id); caption.setLabelFor(id);
        input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setSingleLine(true);
        input.setText(String.valueOf(value)); input.setMinHeight(dp(48));
        body.addView(input, new LinearLayout.LayoutParams(-1, -2)); return input;
    }
    private int readSeconds(EditText input) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value >= 1 && value <= 3600) return value;
        } catch (NumberFormatException ignored) { }
        input.setError("1~3,600 사이의 초를 입력하세요."); input.requestFocus(); return -1;
    }
    private void saveTiming() {
        int delay = readSeconds(delayInput); if (delay < 0) return;
        int interval = readSeconds(intervalInput); if (interval < 0) return;
        int maximum;
        try { maximum = Integer.parseInt(maxInput.getText().toString().trim()); }
        catch (NumberFormatException e) { maximum = -1; }
        if (maximum < 1 || maximum > 1000) {
            maxInput.setError("1~1,000 사이의 횟수를 입력하세요."); maxInput.requestFocus(); return;
        }
        AppSettings.prefs(this).edit().putInt("delay_seconds", delay).putInt("interval_seconds", interval).putInt("max_vibrations", maximum).apply();
        updateTimingSummary(); toast("진동 설정을 저장했습니다.");
    }
    private void updateTimingSummary() {
        timingSummary.setText(getString(R.string.timing_summary, AppSettings.delay(this), AppSettings.interval(this)));
    }
    static boolean hasAccess(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName expected = new ComponentName(c, MediaAccessService.class);
        for (String entry : enabled.split(":")) if (expected.equals(ComponentName.unflattenFromString(entry))) return true;
        return false;
    }
    private boolean notificationsAllowed() {
        return getSystemService(NotificationManager.class).areNotificationsEnabled()
            && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }
    private void startWatch() {
        if (!hasAccess(this)) { toast("먼저 알림 접근 권한을 허용해 주세요."); return; }
        if (!notificationsAllowed()) { toast("감시 상태를 볼 수 있도록 알림 표시를 허용해 주세요."); return; }
        for (String pkg : AppSettings.targetPackages(this)) {
            if (getPackageManager().getLaunchIntentForPackage(pkg) == null) {
                toast(getString(R.string.install_selected_app, AppSettings.appName(this, pkg))); return;
            }
        }
        try { startForegroundService(new Intent(this, WatchService.class)); }
        catch (RuntimeException e) { toast("감시를 시작하지 못했습니다. 권한을 확인하고 다시 시도해 주세요."); }
    }
    private void update() {
        state.setText(WatchService.status);
        target.setText(AppSettings.targetName(this));
        vibrationCount.setText(getString(R.string.vibration_count, WatchService.vibrationCount, AppSettings.maxVibrations(this)));
        state.setTextColor(WatchService.alarmActive ? (dark ? 0xFFFF8A80 : 0xFFB71C1C) : (dark ? 0xFF71DDAA : 0xFF13845B));
        start.setEnabled(!WatchService.running); stop.setEnabled(WatchService.running); pause.setEnabled(WatchService.running);
        boolean exempt = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        permissions.setText(getString(R.string.permission_status, hasAccess(this) ? "허용" : "필요", notificationsAllowed() ? "허용" : "필요", exempt ? "설정됨" : "미설정"));
    }
    private void open(Intent intent) { try { startActivity(intent); } catch (ActivityNotFoundException e) { toast("이 기기에서는 설정 앱에서 직접 변경해 주세요."); } }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override public void onResume() { super.onResume(); handler.post(refresh);
        if (getIntent().getBooleanExtra("pause_controls", false)) {
            getIntent().removeExtra("pause_controls"); choosePause();
        } }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
