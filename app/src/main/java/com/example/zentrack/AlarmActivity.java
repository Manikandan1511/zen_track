package com.example.zentrack;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.media.*;
import android.net.Uri;
import android.os.*;
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

        // API 27+ way to show over lock screen
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

        playAlarmSound();
        startVibration();
        btnDismiss.setOnClickListener(v -> dismiss());
    }

    private void playAlarmSound() {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
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