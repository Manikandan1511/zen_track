package com.example.zentrack;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.*;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.*;

import java.util.List;
import java.util.Locale;

public class LocationTrackingService extends Service {

    public static final String ACTION_LOCATION_UPDATE = "com.example.zentrack.LOCATION_UPDATE";
    private static final String TAG        = "ZentrackGPS";
    private static final String CHANNEL_ID = "ZentrackChannel";
    private static final int    NOTIF_ID   = 1001;

    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private String   destinationName;
    private double   alertDistanceKm = 1.0;
    private Location destinationLocation;
    private Location lastUserLocation;
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
            alarmFired = false; 
        }
        startForeground(NOTIF_ID, buildNotification("Locating: " + destinationName));
        geocodeDestination();
        startLocationUpdates();
        return START_STICKY;
    }

    private void geocodeDestination() {
        if (destinationName == null || destinationName.isEmpty()) return;
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        new Thread(() -> {
            try {
                List<Address> list = geocoder.getFromLocationName(destinationName, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    destinationLocation = new Location("destination");
                    destinationLocation.setLatitude(a.getLatitude());
                    destinationLocation.setLongitude(a.getLongitude());
                    
                    updateNotification("Tracking → " + destinationName);
                    // Update map immediately
                    if (lastUserLocation != null) broadcastLocation(lastUserLocation);
                }
            } catch (Exception e) {
                Log.e(TAG, "Geocode error: " + e.getMessage());
            }
        }).start();
    }

    private void startLocationUpdates() {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location cur = result.getLastLocation();
                if (cur == null) return;

                lastUserLocation = cur;
                broadcastLocation(cur);

                if (destinationLocation == null || alarmFired) return;

                float metres = cur.distanceTo(destinationLocation);
                double km = metres / 1000.0;
                updateNotification(String.format("%.2f km to %s", km, destinationName));

                // TRIGGER ALARM
                if (km <= alertDistanceKm) {
                    triggerAlarm(metres);
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        }
    }

    private void broadcastLocation(Location loc) {
        Intent intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.putExtra("lat", loc.getLatitude());
        intent.putExtra("lng", loc.getLongitude());
        if (destinationLocation != null) {
            intent.putExtra("destLat", destinationLocation.getLatitude());
            intent.putExtra("destLng", destinationLocation.getLongitude());
            intent.putExtra("destName", destinationName);
        }
        sendBroadcast(intent);
    }

    private void triggerAlarm(float metres) {
        alarmFired = true;
        
        // Start the noisy Alarm Screen
        Intent ai = new Intent(this, AlarmActivity.class);
        ai.putExtra("destinationName", destinationName);
        ai.putExtra("distanceMeters", metres);
        ai.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(ai);

        updateNotification("Arrived at " + destinationName + "!");
    }

    private Notification buildNotification(String msg) {
        Intent tap = new Intent(this, HomeActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Zentrack Active")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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
                    CHANNEL_ID, "GPS Tracking", NotificationManager.IMPORTANCE_HIGH);
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