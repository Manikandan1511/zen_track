package com.example.zentrack;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.zentrack.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {

    private static final int RINGTONE_PICKER_REQUEST = 999;
    private SharedPreferences prefs;
    private Switch switchVibration, switchRing;
    private TextView tvRingtoneName;
    private String selectedRingtoneUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("ZentrackPrefs", MODE_PRIVATE);

        switchVibration = findViewById(R.id.switchVibration);
        switchRing      = findViewById(R.id.switchRing);
        tvRingtoneName  = findViewById(R.id.tvRingtoneName);
        LinearLayout btnSelectRingtone = findViewById(R.id.btnSelectRingtone);
        LinearLayout btnLogout         = findViewById(R.id.btnLogout);
        Button btnSave                 = findViewById(R.id.btnSaveSettings);

        // Load saved prefs
        switchVibration.setChecked(prefs.getBoolean("vibrationEnabled", true));
        switchRing.setChecked(prefs.getBoolean("ringEnabled", true));
        selectedRingtoneUri = prefs.getString("ringtoneUri", Settings.System.DEFAULT_ALARM_ALERT_URI.toString());

        updateRingtoneName(Uri.parse(selectedRingtoneUri));

        btnSelectRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone");
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedRingtoneUri));
            startActivityForResult(intent, RINGTONE_PICKER_REQUEST);
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putBoolean("vibrationEnabled", switchVibration.isChecked())
                    .putBoolean("ringEnabled",      switchRing.isChecked())
                    .putString("ringtoneUri",       selectedRingtoneUri)
                    .apply();
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        });

        // Bottom Navigation logic
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_settings);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            return true;
        });
    }

    private void updateRingtoneName(Uri uri) {
        Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
        if (ringtone != null) {
            tvRingtoneName.setText(ringtone.getTitle(this));
        } else {
            tvRingtoneName.setText("Default Alarm");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                selectedRingtoneUri = uri.toString();
                updateRingtoneName(uri);
            }
        }
    }
}