package com.example.zentrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

public class HomeActivity extends AppCompatActivity {

    private static final int LOC_PERM = 101;

    private EditText etFrom, etDest, etCustom;
    private RadioGroup rgDistance;
    private Button btnStart, btnStop;
    private boolean isTracking = false;

    private MapView osmMap;
    private FusedLocationProviderClient fusedClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid needs this BEFORE setContentView
        Configuration.getInstance().load(this,
                PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_home);

        // Location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOC_PERM);
        }

        // Show logged-in user's email
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            TextView tvUsername = findViewById(R.id.tvUsername);
            String email = user.getEmail();
            if (email != null) tvUsername.setText(email.split("@")[0]);
        }

        etFrom     = findViewById(R.id.etFromLocation);
        etDest     = findViewById(R.id.etDestination);
        etCustom   = findViewById(R.id.etCustomDistance);
        rgDistance = findViewById(R.id.rgDistance);
        btnStart   = findViewById(R.id.btnStartTracking);
        btnStop    = findViewById(R.id.btnStopTracking);

        btnStart.setOnClickListener(v -> startTracking());
        btnStop.setOnClickListener(v  -> stopTracking());

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });

        // Setup OSM Map
        osmMap = findViewById(R.id.osmMap);
        osmMap.setTileSource(TileSourceFactory.MAPNIK);
        osmMap.setMultiTouchControls(true);
        osmMap.getController().setZoom(14.0);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        zoomToCurrentLocation();
    }

    private void zoomToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                osmMap.getController().animateTo(point);
                osmMap.getController().setZoom(15.0);

                Marker me = new Marker(osmMap);
                me.setPosition(point);
                me.setTitle("You are here");
                me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                osmMap.getOverlays().clear();
                osmMap.getOverlays().add(me);
                osmMap.invalidate();
            }
        });
    }

    // Called by LocationTrackingService to update map with route line
    public void showRouteOnMap(double uLat, double uLng,
                               double dLat, double dLng, String destName) {
        if (osmMap == null) return;

        osmMap.getOverlays().clear();

        GeoPoint userPos = new GeoPoint(uLat, uLng);
        GeoPoint destPos = new GeoPoint(dLat, dLng);

        // User marker
        Marker userMarker = new Marker(osmMap);
        userMarker.setPosition(userPos);
        userMarker.setTitle("You");
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        osmMap.getOverlays().add(userMarker);

        // Destination marker
        Marker destMarker = new Marker(osmMap);
        destMarker.setPosition(destPos);
        destMarker.setTitle(destName);
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        osmMap.getOverlays().add(destMarker);

        // Route line
        Polyline line = new Polyline();
        line.addPoint(userPos);
        line.addPoint(destPos);
        line.getOutlinePaint().setColor(Color.parseColor("#2DA86E"));
        line.getOutlinePaint().setStrokeWidth(8f);
        osmMap.getOverlays().add(line);

        osmMap.invalidate();

        // Zoom to fit both points
        osmMap.zoomToBoundingBox(
                new org.osmdroid.util.BoundingBox(
                        Math.max(uLat, dLat) + 0.01,
                        Math.max(uLng, dLng) + 0.01,
                        Math.min(uLat, dLat) - 0.01,
                        Math.min(uLng, dLng) - 0.01
                ), true, 100
        );
    }

    private void startTracking() {
        String from = etFrom.getText().toString().trim();
        String dest = etDest.getText().toString().trim();
        if (from.isEmpty()) { etFrom.setError("Enter starting location"); return; }
        if (dest.isEmpty()) { etDest.setError("Enter destination"); return; }

        double alertKm = getSelectedDistance();
        String custom  = etCustom.getText().toString().trim();
        if (!custom.isEmpty()) {
            try { alertKm = Double.parseDouble(custom); }
            catch (NumberFormatException e) { etCustom.setError("Invalid number"); return; }
        }

        Intent si = new Intent(this, LocationTrackingService.class);
        si.putExtra("destination", dest);
        si.putExtra("alertDistanceKm", alertKm);
        ContextCompat.startForegroundService(this, si);

        isTracking = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        Toast.makeText(this, "Tracking started! Alert at " + alertKm + " km", Toast.LENGTH_LONG).show();
    }

    private void stopTracking() {
        stopService(new Intent(this, LocationTrackingService.class));
        isTracking = false;
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        osmMap.getOverlays().clear();
        osmMap.invalidate();
        Toast.makeText(this, "Tracking stopped.", Toast.LENGTH_SHORT).show();
    }

    private double getSelectedDistance() {
        int id = rgDistance.getCheckedRadioButtonId();
        if (id == R.id.rb500m) return 0.5;
        if (id == R.id.rb2km)  return 2.0;
        if (id == R.id.rb5km)  return 5.0;
        return 1.0;
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == LOC_PERM && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            zoomToCurrentLocation();
        } else {
            Toast.makeText(this, "Location permission is required!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        osmMap.onResume();
        if (!isTracking) { btnStart.setEnabled(true); btnStop.setEnabled(false); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        osmMap.onPause();
    }
}