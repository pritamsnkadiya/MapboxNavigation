package com.example.pritam.navigation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geocoder.MapboxGeocoder;
import com.mapbox.geocoder.service.models.GeocoderResponse;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;
import com.mapbox.services.android.navigation.ui.v5.NavigationActivity;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;


public class MainActivity extends AppCompatActivity implements LocationEngineListener, PermissionsListener {

    private MapView mapView;

    // variables for adding location layer
    private MapboxMap map;
    private PermissionsManager permissionsManager;
    private LocationLayerPlugin locationPlugin;
    private LocationEngine locationEngine;
    private Location originLocation;

    // variables for adding a marker
    private Marker destinationMarker;
    private LatLng originCoord;
    private LatLng destinationCoord;

    // variables for calculating and drawing a route
    private Point originPosition;
    private Point destinationPosition;
    private DirectionsRoute currentRoute;
    private static final String TAG = "DirectionsActivity";
    private NavigationMapRoute navigationMapRoute;
    List<Double> placeName;
    private Button button,btnGeoPlace;
    EditText geoPlace;
    LinearLayout linearLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        Mapbox.getInstance (this, getString (R.string.access_token));
        setContentView (R.layout.activity_main);
        mapView = (MapView) findViewById (R.id.mapView);
        mapView.onCreate (savedInstanceState);
        mapView.getMapAsync (new OnMapReadyCallback () {
            @Override
            public void onMapReady(final MapboxMap mapboxMap) {
                map = mapboxMap;
                enableLocationPlugin ();
                originCoord = new LatLng(originLocation.getLatitude(), originLocation.getLongitude());
                mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull LatLng point) {
                        if (destinationMarker != null) {
                            mapboxMap.removeMarker(destinationMarker);
                        }
                        destinationCoord = point;
                        destinationMarker = mapboxMap.addMarker(new MarkerOptions()
                                .position(destinationCoord));

                        destinationPosition = Point.fromLngLat(destinationCoord.getLongitude(), destinationCoord.getLatitude());
                        originPosition = Point.fromLngLat(originCoord.getLongitude(), originCoord.getLatitude());
                        getRoute(originPosition, destinationPosition);
                        button.setEnabled(true);
                        button.setVisibility (View.VISIBLE);
                        button.setBackgroundResource(R.color.mapboxBlue);
                    };
                });
                button = findViewById(R.id.startButton);
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Point origin = originPosition;
                        Point destination = destinationPosition;

                        boolean simulateRoute = false;
                        NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                                .origin(origin)
                                .destination(destination)
                                .shouldSimulateRoute(simulateRoute)
                                .build();

                        // Call this method with Context from within an Activity
                        NavigationLauncher.startNavigation(MainActivity.this, options);
                    }
                });
            };
        });
        btnGeoPlace =(Button)findViewById (R.id.btnGeoPlace);
        geoPlace=(EditText)findViewById (R.id.GeoPlace);
        linearLayout=(LinearLayout)findViewById (R.id.hideLayout);
    }
    private void geoCoding(Editable string) {
        MapboxGeocoder client = new MapboxGeocoder.Builder()
                .setAccessToken(getString (R.string.access_token))
                .setLocation(String.valueOf (string))
                .build();
        client.enqueue (new retrofit.Callback<GeocoderResponse> () {
            @Override
            public void onResponse(retrofit.Response<GeocoderResponse> response, retrofit.Retrofit retrofit) {
                linearLayout.setVisibility (View.INVISIBLE);
                placeName = response.body().getFeatures().get(0).getGeometry ().getCoordinates ();
                Log.d(TAG, "Place name: " + placeName.toString ());
                Log.d ("Place lat",placeName.get (1)+"");
                Log.d ("Place lat",placeName.get (0)+"");
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(placeName.get (1),
                        placeName.get (0)), 12));
                destinationMarker = map.addMarker(new MarkerOptions().position (new LatLng (placeName.get (1),placeName.get (0))));
            }
            @Override
            public void onFailure(Throwable t) {
                Log.d(TAG, "Error: " + t.getMessage());
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted (this)) {
            // Create an instance of LOST location engine
            initializeLocationEngine ();

            locationPlugin = new LocationLayerPlugin (mapView, map, locationEngine);
            locationPlugin.setRenderMode (RenderMode.COMPASS);
        } else {
            permissionsManager = new PermissionsManager (this);
            permissionsManager.requestLocationPermissions (this);
        }
    }
    @SuppressLint("MissingPermission")
    private void initializeLocationEngine() {
        LocationEngineProvider locationEngineProvider = new LocationEngineProvider (this);
        locationEngine = locationEngineProvider.obtainBestLocationEngineAvailable ();
        locationEngine.setPriority (LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.requestLocationUpdates ();
        locationEngine.activate ();

        if (ActivityCompat.checkSelfPermission (this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission (this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location lastLocation = locationEngine.getLastLocation ();
        if (lastLocation != null) {
            originLocation = lastLocation;
            Log.d ("Last Location",lastLocation+"");
            setCameraPosition(lastLocation);
            linearLayout.setVisibility (View.VISIBLE);

        } else {
            locationEngine.addLocationEngineListener(this);
        }
    }
    private void setCameraPosition(Location location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 15));
    }
    private void getRoute(Point origin, Point destination) {
        NavigationRoute.builder()
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        // You can get the generic HTTP info about the response
                        Log.d(TAG, "Response code: " + response.code());
                        if (response.body() == null) {
                            Log.e(TAG, "No routes found, make sure you set the right user and access token.");
                            return;
                        } else if (response.body().routes().size() < 1) {
                            Log.e(TAG, "No routes found");
                            return;
                        }

                        currentRoute = response.body().routes().get(0);

                        // Draw the route on the map
                        if (navigationMapRoute != null) {
                            navigationMapRoute.removeRoute();
                        } else {
                            navigationMapRoute = new NavigationMapRoute(null, mapView, map, R.style.NavigationMapRoute);
                        }
                        navigationMapRoute.addRoute(currentRoute);
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                        Log.e(TAG, "Error: " + throwable.getMessage());
                    }
                });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {

    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationPlugin();
        } else {
            finish();
        }
    }
    @SuppressLint("MissingPermission")
    @Override
    public void onConnected() {
        locationEngine.requestLocationUpdates();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            originLocation = location;
            setCameraPosition(location);
            locationEngine.removeLocationEngineListener(this);
        }
    }

    @Override
    @SuppressWarnings( {"MissingPermission"})
    protected void onStart() {
        super.onStart();
        if (locationEngine != null) {
            locationEngine.requestLocationUpdates();
        }
        if (locationPlugin != null) {
            locationPlugin.onStart();
        }
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (locationEngine != null) {
            locationEngine.removeLocationUpdates();
        }
        if (locationPlugin != null) {
            locationPlugin.onStop();
        }
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        if (locationEngine != null) {
            locationEngine.deactivate();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    public void searchPlace(View view) {
        Editable string =geoPlace.getText ();
        Log.d ("Search place ",string.toString ()+"");
        geoCoding(string);

    }
}
