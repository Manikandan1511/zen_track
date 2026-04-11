package com.example.zentrack;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.google.android.gms.location.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private static final int LOC_PERM = 101;
    private EditText etFrom, etDest, etCustom;
    private RadioGroup rgDistance;
    private Button btnStart, btnStop;
    private boolean isTracking = false;

    private MapView osmMap;
    private MyLocationNewOverlay locationOverlay;
    private FusedLocationProviderClient fusedClient;

    // Receiver to get live updates from the Background Service
    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocationTrackingService.ACTION_LOCATION_UPDATE.equals(intent.getAction())) {
                double lat = intent.getDoubleExtra("lat", 0);
                double lng = intent.getDoubleExtra("lng", 0);
                
                if (intent.hasExtra("destLat")) {
                    double dLat = intent.getDoubleExtra("destLat", 0);
                    double dLng = intent.getDoubleExtra("destLng", 0);
                    String dName = intent.getStringExtra("destName");
                    showRouteOnMap(lat, lng, dLat, dLng, dName);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_home);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOC_PERM);
        }

        etFrom = findViewById(R.id.etFromLocation);
        etDest = findViewById(R.id.etDestination);
        etCustom = findViewById(R.id.etCustomDistance);
        rgDistance = findViewById(R.id.rgDistance);
        btnStart = findViewById(R.id.btnStartTracking);
        btnStop = findViewById(R.id.btnStopTracking);

        btnStart.setOnClickListener(v -> startTracking());
        btnStop.setOnClickListener(v -> stopTracking());

        osmMap = findViewById(R.id.osmMap);
        osmMap.setTileSource(TileSourceFactory.MAPNIK);
        osmMap.setMultiTouchControls(true);
        osmMap.getController().setZoom(16.0);

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), osmMap);
        locationOverlay.enableMyLocation();
        locationOverlay.enableFollowLocation();
        osmMap.getOverlays().add(locationOverlay);

        updateCurrentAddress();
    }

    private void updateCurrentAddress() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    try {
                        List<Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                        if (!addresses.isEmpty()) etFrom.setText(addresses.get(0).getAddressLine(0));
                    } catch (IOException e) { etFrom.setText("Current Location"); }
                }
            });
        }
    }

    public void showRouteOnMap(double uLat, double uLng, double dLat, double dLng, String destName) {
        // Clear old markers but keep the blue user dot
        osmMap.getOverlays().removeIf(o -> o instanceof Marker || o instanceof Polyline);

        GeoPoint userPos = new GeoPoint(uLat, uLng);
        GeoPoint destPos = new GeoPoint(dLat, dLng);

        // Add Destination Marker
        Marker destMarker = new Marker(osmMap);
        destMarker.setPosition(destPos);
        destMarker.setTitle(destName);
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        osmMap.getOverlays().add(destMarker);

        // Draw Route Line
        Polyline line = new Polyline();
        line.addPoint(userPos);
        line.addPoint(destPos);
        line.getOutlinePaint().setColor(Color.parseColor("#2DA86E"));
        line.getOutlinePaint().setStrokeWidth(10f);
        osmMap.getOverlays().add(line);

        osmMap.invalidate();
    }

    private void startTracking() {
        String dest = etDest.getText().toString().trim();
        if (dest.isEmpty()) { etDest.setError("Enter destination"); return; }

        double alertKm = getSelectedDistance();
        String custom = etCustom.getText().toString().trim();
        if (!custom.isEmpty()) {
            try { alertKm = Double.parseDouble(custom); } catch (Exception e) { etCustom.setError("Invalid number"); return; }
        }

        Intent si = new Intent(this, LocationTrackingService.class);
        si.putExtra("destination", dest);
        si.putExtra("alertDistanceKm", alertKm);
        ContextCompat.startForegroundService(this, si);

        isTracking = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        Toast.makeText(this, "Tracking Started!", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        stopService(new Intent(this, LocationTrackingService.class));
        isTracking = false;
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        osmMap.getOverlays().removeIf(o -> o instanceof Marker || o instanceof Polyline);
        osmMap.invalidate();
    }

    private double getSelectedDistance() {
        int id = rgDistance.getCheckedRadioButtonId();
        if (id == R.id.rb500m) return 0.5;
        if (id == R.id.rb2km) return 2.0;
        if (id == R.id.rb5km) return 5.0;
        return 1.0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        osmMap.onResume();
        locationOverlay.enableMyLocation();
        IntentFilter filter = new IntentFilter(LocationTrackingService.ACTION_LOCATION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(locationReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        osmMap.onPause();
        locationOverlay.disableMyLocation();
        unregisterReceiver(locationReceiver);
    }
}