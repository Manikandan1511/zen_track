package com.example.zentrack;

import android.content.SharedPreferences;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.zentrack.R;
public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Switch switchVibration, switchRing, switchSilentOverride, switchBatterySaver;
    private Spinner spinnerRingtone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("ZentrackPrefs", MODE_PRIVATE);

        switchVibration      = findViewById(R.id.switchVibration);
        switchRing           = findViewById(R.id.switchRing);
        switchSilentOverride = findViewById(R.id.switchSilentOverride);
        switchBatterySaver   = findViewById(R.id.switchBatterySaver);
        spinnerRingtone      = findViewById(R.id.spinnerRingtone);
        Button btnPreview    = findViewById(R.id.btnPreviewAlarm);
        Button btnSave       = findViewById(R.id.btnSaveSettings);

        String[] tones = {"Default Alarm", "Notification", "Ringtone"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, tones);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRingtone.setAdapter(adapter);

        // Load saved prefs
        switchVibration.setChecked(prefs.getBoolean("vibrationEnabled", true));
        switchRing.setChecked(prefs.getBoolean("ringEnabled", true));
        switchSilentOverride.setChecked(prefs.getBoolean("silentOverride", false));
        switchBatterySaver.setChecked(prefs.getBoolean("batterySaver", false));

        btnPreview.setOnClickListener(v -> {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (r != null) {
                r.play();
                new Handler(Looper.getMainLooper()).postDelayed(r::stop, 3000);
                Toast.makeText(this, "Playing preview...", Toast.LENGTH_SHORT).show();
            }
        });

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putBoolean("vibrationEnabled", switchVibration.isChecked())
                    .putBoolean("ringEnabled",      switchRing.isChecked())
                    .putBoolean("silentOverride",   switchSilentOverride.isChecked())
                    .putBoolean("batterySaver",     switchBatterySaver.isChecked())
                    .apply();
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        });
    }
}