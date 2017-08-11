package com.nayana.bhoj.apps.movingmarker;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.CancelableCallback;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by nayana_bhoj on 8/8/17.
 */

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private List<Marker> markers = new ArrayList<>();
    private GoogleMap googleMap;
    private final Handler mHandler = new Handler();
    private Marker selectedMarker;
    private Animator animator = new Animator();
    ArrayList<LatLng> points;
    int markerNumber = 0;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button start = findViewById(R.id.btn_play);
        Button stop = findViewById(R.id.btn_stop);
        Button ResetMarker = findViewById(R.id.btn_clearmarker);
        Button btnDraw = findViewById(R.id.btnDraw);
        final Button btnToggle = findViewById(R.id.btnToggle);
        btnToggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                animator.toggleStyle();
            }
        });

        ResetMarker.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearMarkers();
            }
        });

        start.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                animator.startAnimation();
            }
        });

        stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                animator.stopAnimation();
            }
        });

        btnDraw.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // Checks, whether start and end locations are captured
                if (markers.size() >= 2) {
                    LatLng origin = markers.get(0).getPosition();
                    LatLng dest = markers.get(1).getPosition();

                    // Getting URL to the Google Directions API
                    String url = getDirectionsUrl(origin, dest);

                    DownloadTask downloadTask = new DownloadTask();

                    // Start downloading json data from Google Directions API
                    downloadTask.execute(url);
                }

            }
        });

    }

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_location_permission)
                        .setMessage(R.string.text_location_permission)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        //Allow MapView
                        initializeMap();
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "This permission is mandatory to run application", Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }

        }
    }

    public void initializeMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    public void clearMarkers() {
        googleMap.clear();
        markers.clear();
    }

    protected void removeSelectedMarker() {
        this.markers.remove(this.selectedMarker);
        this.selectedMarker.remove();
    }

    protected void addMarkerToMap(LatLng latLng, boolean addIcon) {
        Marker marker;
        String title = "";
        if (addIcon) {
            if (markerNumber == 0) {
                title = "Source";
                Toast.makeText(MainActivity.this, "Source", Toast.LENGTH_SHORT).show();
            } else if (markerNumber == 1) {
                title = "Destination";
                Toast.makeText(MainActivity.this, "Destination", Toast.LENGTH_SHORT).show();
            } else {
                title = "WayPoint";
                //Toast.makeText(MainActivity.this, "WayPoint", Toast.LENGTH_SHORT).show();
            }
            marker = googleMap.addMarker(new MarkerOptions().position(latLng).title(title));
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        } else {
            marker = googleMap.addMarker(new MarkerOptions().position(latLng).title("Title"));
        }
        markers.add(marker);
        markerNumber++;
    }


    @Override
    public void onMapReady(GoogleMap Map) {
        googleMap = Map;
        googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng latLng) {
                // Already 10 locations with 8 waypoints and 1 start location and 1 end location.
                // Upto 8 waypoints are allowed in a query for non-business users
                if (markers.size() >= 10) {
                    Toast.makeText(MainActivity.this, "Upto 10 locations are allowed", Toast.LENGTH_SHORT).show();
                    return;
                }
                addMarkerToMap(latLng, true);
                //animator.startAnimation(true);
            }
        });
    }

    public class Animator implements Runnable {
        private static final int ANIMATE_SPEEED = 550;
        private static final int ANIMATE_SPEEED_TURN = 400;
        private static final int BEARING_OFFSET = 20;
        private final Interpolator interpolator = new LinearInterpolator();
        int currentIndex = 0;
        float tilt = 90;
        float zoom = 15.5f;
        boolean upward = true;
        long start = SystemClock.uptimeMillis();
        LatLng endLatLng = null;
        LatLng beginLatLng = null;
        //boolean showPolyline = false;
        private Marker trackingMarker;

        public void reset() {
            resetMarkers();
            start = SystemClock.uptimeMillis();
            currentIndex = 0;
            endLatLng = getEndLatLng();
            beginLatLng = getBeginLatLng();

        }

        private void resetMarkers() {
            for (Marker marker : markers) {
                marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            }
        }

        public void stop() {
            trackingMarker.remove();
            mHandler.removeCallbacks(animator);
        }

        private void highLightMarker(int index) {
            highLightMarker(markers.get(index));
        }

        private void highLightMarker(Marker marker) {
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
            marker.showInfoWindow();
            selectedMarker = marker;
        }

        public void initialize() {
            reset();
            //highLightMarker(0);
            polyLine = initializePolyLine();
            // We first need to put the camera in the correct position for the first run (we need 2 markers for this).....
            LatLng markerPos = markers.get(0).getPosition();
            LatLng secondPos = markers.get(1).getPosition();
            setupCameraPositionForMovement(markerPos, secondPos);
        }

        private void setupCameraPositionForMovement(LatLng markerPos, LatLng secondPos) {
            float bearing = bearingBetweenLatLngs(markerPos, secondPos);
            trackingMarker = googleMap.addMarker(new MarkerOptions().position(markerPos)
                    .title("")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    //.icon(BitmapDescriptorFactory.fromResource(R.drawable.car1))
                    .snippet(""));
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(markerPos)
                    .bearing(bearing + BEARING_OFFSET)
                    .tilt(90)
                    .zoom(googleMap.getCameraPosition().zoom >= 16 ? googleMap.getCameraPosition().zoom : 16)
                    .build();

            googleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition),
                    ANIMATE_SPEEED_TURN,
                    new CancelableCallback() {
                        @Override
                        public void onFinish() {
                            System.out.println("finished camera");
                            Log.e("animator before reset", animator + "");
                            animator.reset();
                            Log.e("animator after reset", animator + "");
                            Handler handler = new Handler();
                            handler.post(animator);
                        }

                        @Override
                        public void onCancel() {
                            System.out.println("cancelling camera");
                        }
                    });
        }

        public void navigateToPoint(LatLng latLng, boolean animate) {
            CameraPosition position = new CameraPosition.Builder().target(latLng).build();
            changeCameraPosition(position, animate);
        }

        private void changeCameraPosition(CameraPosition cameraPosition, boolean animate) {
            CameraUpdate cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition);
            if (animate) {
                googleMap.animateCamera(cameraUpdate);
            } else {
                googleMap.moveCamera(cameraUpdate);
            }
        }

        private Location convertLatLngToLocation(LatLng latLng) {
            Location loc = new Location("someLoc");
            loc.setLatitude(latLng.latitude);
            loc.setLongitude(latLng.longitude);
            return loc;
        }

        private float bearingBetweenLatLngs(LatLng begin, LatLng end) {
            Location beginL = convertLatLngToLocation(begin);
            Location endL = convertLatLngToLocation(end);
            return beginL.bearingTo(endL);
        }

        public void toggleStyle() {
            if (GoogleMap.MAP_TYPE_NORMAL == googleMap.getMapType()) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            } else {
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            }
        }

        private Polyline polyLine;
        private PolylineOptions rectOptions = new PolylineOptions();

        private Polyline initializePolyLine() {
            rectOptions.add(markers.get(0).getPosition());
            return googleMap.addPolyline(rectOptions);
        }

        /**
         * Add the marker to the polyline.
         */
        private void updatePolyLine(LatLng latLng) {
            List<LatLng> points = polyLine.getPoints();
            points.add(latLng);
            polyLine.setPoints(points);
        }

        public void stopAnimation() {
            animator.stop();
        }

        public void startAnimation() {
            //if (markers.size() > 2) {
            animator.initialize();
            //}
        }

        @Override
        public void run() {
            long elapsed = SystemClock.uptimeMillis() - start;
            double t = interpolator.getInterpolation((float) elapsed / ANIMATE_SPEEED);
            Log.w("interpolator", t + "");
            double lat = t * endLatLng.latitude + (1 - t) * beginLatLng.latitude;
            double lng = t * endLatLng.longitude + (1 - t) * beginLatLng.longitude;
            Log.w("lat. lng", lat + "," + lng + "");
            LatLng newPosition = new LatLng(lat, lng);
            Log.w("newPosition", newPosition + "");

            trackingMarker.setPosition(newPosition);
            updatePolyLine(newPosition);


            // It's not possible to move the marker + center it through a cameraposition update while another camerapostioning was already happening.
            //navigateToPoint(newPosition,tilt,bearing,currentZoom,false);
            //navigateToPoint(newPosition,false);

            if (t < 1) {
                mHandler.postDelayed(this, 16);
            } else {
                System.out.println("Move to next marker.... current = " + currentIndex + " and size = " + markers.size());
                // imagine 5 elements -  0|1|2|3|4 currentindex must be smaller than 4
                if (currentIndex < markers.size() - 2) {
                    currentIndex++;
                    endLatLng = getEndLatLng();
                    beginLatLng = getBeginLatLng();
                    start = SystemClock.uptimeMillis();
                    LatLng begin = getBeginLatLng();
                    LatLng end = getEndLatLng();
                    float bearingL = bearingBetweenLatLngs(begin, end);
                    //highLightMarker(currentIndex);
                    CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(end) // changed this...
                            .bearing(bearingL + BEARING_OFFSET)
                            .tilt(tilt)
                            .zoom(googleMap.getCameraPosition().zoom)
                            .build();
                    googleMap.animateCamera(
                            CameraUpdateFactory.newCameraPosition(cameraPosition),
                            ANIMATE_SPEEED_TURN,
                            null
                    );
                    start = SystemClock.uptimeMillis();
                    mHandler.postDelayed(animator, 16);
                } else {
                    currentIndex++;
                    //highLightMarker(currentIndex);
                    stopAnimation();
                }
            }
        }

        private LatLng getEndLatLng() {
            return markers.get(currentIndex + 1).getPosition();
            //return points1.get((points1.size()-1));
        }

        private LatLng getBeginLatLng() {
            return markers.get(currentIndex).getPosition();
            //return points1.get(0);
        }

        private void adjustCameraPosition() {
            if (upward) {
                if (tilt < 90) {
                    tilt++;
                    zoom -= 0.01f;
                } else {
                    upward = false;
                }
            } else {
                if (tilt > 0) {
                    tilt--;
                    zoom += 0.01f;
                } else {
                    upward = true;
                }
            }
        }
    }

    private void loadMap() {
        try {
            ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap map) {
                    googleMap = map;
                }
            });
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setZoomControlsEnabled(true);
        } catch (Exception e) {
            e.toString();
        }
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";

        // Waypoints
        String waypoints = "";
        for (int i = 2; i < markers.size(); i++) {
            LatLng point = (LatLng) markers.get(i).getPosition();
            if (i == 2)
                waypoints = "waypoints=";
            waypoints += point.latitude + "," + point.longitude + "|";
        }


        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + waypoints;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;


        return url;
    }

    /**
     * A method to download json data from url
     */
    @SuppressLint("LongLogTag")
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception downloading url", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    // Fetches data from url passed
    private class DownloadTask extends AsyncTask<String, Void, String> {

        // Downloading data in non-ui thread
        @Override
        protected String doInBackground(String... url) {

            // For storing data from web service
            String data = "";

            try {
                // Fetching the data from web service
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        // Executes in UI thread, after the execution of
        // doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();

            // Invokes the thread for parsing the JSON data
            parserTask.execute(result);

        }
    }

    /**
     * A class to parse the Google Places in JSON format
     */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                // Starts parsing data
                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        // Executes in UI thread, after the parsing process
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            try {
                if (result != null) {
                    PolylineOptions lineOptions = null;
                    // Traversing through all the routes
                    for (int i = 0; i < result.size(); i++) {
                        points = new ArrayList<>();
                        lineOptions = new PolylineOptions();
                        // Fetching i-th route
                        List<HashMap<String, String>> path = result.get(i);
                        // Fetching all the points in i-th route
                        for (int j = 0; j < path.size(); j++) {
                            HashMap<String, String> point = path.get(j);

                            double lat = Double.parseDouble(point.get("lat"));
                            double lng = Double.parseDouble(point.get("lng"));
                            LatLng position = new LatLng(lat, lng);

                            points.add(position);

                        }

                        // Adding all the points in the route to LineOptions
                        lineOptions.addAll(points);
                        lineOptions.width(8);
                        lineOptions.color(Color.RED);


                    }

                    List<Marker> markers_temp = new ArrayList<>();
                    markers_temp.addAll(markers);
                    markers.clear();
                    addMarkerToMap(markers_temp.get(0).getPosition(), true);

                    addMarkerToMap(points.get(0), false);
                    for (int i = 1; i < points.size(); i++) {
                        if (markers_temp.size() > 2) {
                            int numberOfWayPoints = markers_temp.size() - 2;
                            for (int j = 1; j <= numberOfWayPoints; j++) {
                                if (distance(markers_temp.get(j + 1).getPosition().latitude,
                                        markers_temp.get(j + 1).getPosition().longitude,
                                        points.get(i).latitude,
                                        points.get(i).latitude) < 0.2) {
                                    addMarkerToMap(markers_temp.get(j + 1).getPosition(), true);
                                }
                            }

                        }
                        if (i % 3 == 0) {
                            addMarkerToMap(points.get(i), false);
                        }
                    }
                    if (!(points.size() % 3 == 0)) {
                        addMarkerToMap(points.get((points.size() - 1)), false);
                    }

                    addMarkerToMap(markers_temp.get(1).getPosition(), true);

                    for (int i = 0; i < markers.size(); i++) {
                        if (i == 0 || i == (markers.size() - 1)) {
                            markers.get(i).setVisible(true);
                        } else {
                            markers.get(i).setVisible(false);
                        }


                    }

                    // Drawing polyline in the Google Map for the i-th route
                    googleMap.addPolyline(lineOptions);
                    animator.startAnimation();
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Direction API not responding", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * calculates the distance between two locations in MILES
     * i.e calculating distance between waypoint and marker point
     */
    private double distance(double lat1, double lng1, double lat2, double lng2) {

        double earthRadius = 3958.75; // in miles, change to 6371 for kilometer output

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);

        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2));

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double dist = earthRadius * c;

        return dist; // output distance, in MILES
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (checkLocationPermission()) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {

                //Allow MapView
                initializeMap();
            }
        }

    }


}
