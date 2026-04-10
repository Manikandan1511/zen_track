package com.example.zentrack;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.Task;

import java.util.List;
import java.util.Locale;

public class LocationTrackingService extends Service {

    private static final String TAG        = "ZentrackGPS";
    private static final String CHANNEL_ID = "ZentrackChannel";
    private static final int    NOTIF_ID   = 1001;

    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private String   destinationName;
    private double   alertDistanceKm = 1.0;
    private Location destinationLocation;
    private boolean  alarmFired = false;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            destinationName = intent.getStringExtra("destination");
            alertDistanceKm = intent.getDoubleExtra("alertDistanceKm", 1.0);
        }
        startForeground(NOTIF_ID, buildNotification("Locating destination..."));
        geocodeDestination();
        startLocationUpdates();
        return START_STICKY;
    }

    private void geocodeDestination() {
        if (destinationName == null || destinationName.isEmpty()) return;
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+ non-deprecated callback version
            geocoder.getFromLocationName(destinationName, 1, addresses -> {
                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);
                    destinationLocation = makeLocation(a.getLatitude(), a.getLongitude());
                    updateNotification("Tracking → " + destinationName);
                } else {
                    updateNotification("Cannot find: " + destinationName);
                }
            });
        } else {
            // API 24-32 sync version on background thread
            new Thread(() -> {
                try {
                    List<Address> list = geocoder.getFromLocationName(destinationName, 1);
                    if (list != null && !list.isEmpty()) {
                        Address a = list.get(0);
                        destinationLocation = makeLocation(a.getLatitude(), a.getLongitude());
                        updateNotification("Tracking → " + destinationName);
                    } else {
                        updateNotification("Cannot find: " + destinationName);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Geocode error: " + e.getMessage());
                }
            }).start();
        }
    }

    private Location makeLocation(double lat, double lng) {
        Location loc = new Location("destination");
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        return loc;
    }

    private void startLocationUpdates() {
        LocationRequest req;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(5000)
                    .build();
        } else {
            req = LocationRequest.create();
            req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            req.setInterval(5000);
            req.setFastestInterval(3000);
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (alarmFired) return;
                Location cur = result.getLastLocation();
                if (cur == null || destinationLocation == null) return;

                float metres = cur.distanceTo(destinationLocation);
                double km = metres / 1000.0;
                updateNotification(String.format("%.1f km to %s", km, destinationName));

                if (km <= alertDistanceKm) triggerAlarm(metres);
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());

        } else {
            Log.e(TAG, "No location permission — stopping service");
            stopSelf();
        }
    }

    private void triggerAlarm(float metres) {
        alarmFired = true;
        SharedPreferences prefs = getSharedPreferences("ZentrackPrefs", MODE_PRIVATE);

        if (prefs.getBoolean("ringEnabled", true)) {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (r != null) r.play();
        }

        if (prefs.getBoolean("vibrationEnabled", true)) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                long[] p = {0, 700, 300, 700, 300, 700};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v.vibrate(VibrationEffect.createWaveform(p, -1));
                else v.vibrate(p, -1);
            }
        }

        Intent ai = new Intent(this, AlarmActivity.class);
        ai.putExtra("destinationName", destinationName);
        ai.putExtra("distanceMeters", metres);
        ai.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(ai);

        // High-priority full-screen notification
        Intent tap = new Intent(this, AlarmActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Approaching Destination!")
                .setContentText("Near " + destinationName + " — tap to open")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID + 1, b.build());
    }

    private Notification buildNotification(String msg) {
        Intent tap = new Intent(this, HomeActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Zentrack Running")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String msg) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(msg));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Zentrack Tracking", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Live GPS tracking");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}