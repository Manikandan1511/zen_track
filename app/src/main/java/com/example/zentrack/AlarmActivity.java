package com.example.zentrack;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.zentrack.R;

public class AlarmActivity extends AppCompatActivity {

    private Ringtone ringtone;
    private Vibrator vibrator;
    private ObjectAnimator shakeAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_alarm);

        String destName   = getIntent().getStringExtra("destinationName");
        float  distMetres = getIntent().getFloatExtra("distanceMeters", 0f);

        TextView tvDest    = findViewById(R.id.tvAlarmDestination);
        TextView tvDist    = findViewById(R.id.tvAlarmDistance);
        TextView tvBell    = findViewById(R.id.tvBell);
        Button   btnDismiss = findViewById(R.id.btnDismissAlarm);

        if (destName != null) tvDest.setText("✈  " + destName);
        tvDist.setText(String.format("%.0f meters away", distMetres));

        shakeAnim = ObjectAnimator.ofFloat(tvBell, "rotation", -12f, 12f);
        shakeAnim.setDuration(180);
        shakeAnim.setRepeatCount(ValueAnimator.INFINITE);
        shakeAnim.setRepeatMode(ValueAnimator.REVERSE);
        shakeAnim.start();

        SharedPreferences prefs = getSharedPreferences("ZentrackPrefs", MODE_PRIVATE);
        
        if (prefs.getBoolean("ringEnabled", true)) {
            playAlarmSound(prefs.getString("ringtoneUri", Settings.System.DEFAULT_ALARM_ALERT_URI.toString()));
        }
        
        if (prefs.getBoolean("vibrationEnabled", true)) {
            startVibration();
        }
        
        btnDismiss.setOnClickListener(v -> dismiss());
    }

    private void playAlarmSound(String uriString) {
        Uri uri = Uri.parse(uriString);
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
        if (ringtone != null) ringtone.play();
    }

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        long[] p = {0, 600, 300, 600, 300, 600};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createWaveform(p, 0));
        else vibrator.vibrate(p, 0);
    }

    private void dismiss() {
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        if (vibrator != null) vibrator.cancel();
        if (shakeAnim != null) shakeAnim.cancel();
        stopService(new Intent(this, LocationTrackingService.class));
        Intent home = new Intent(this, HomeActivity.class);
        home.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }

    @Override public void onBackPressed() { /* blocked */ }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        if (vibrator != null) vibrator.cancel();
        if (shakeAnim != null) shakeAnim.cancel();
    }
}