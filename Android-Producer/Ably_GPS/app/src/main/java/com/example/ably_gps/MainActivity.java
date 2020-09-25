package com.example.ably_gps;
import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.JsonObject;

import java.util.ArrayList;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener{

    private Location location;
    private TextView location_view;
    private GoogleApiClient googleApiClient;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private LocationRequest locationRequest;
    private  long UPDATE_INTERVAL = 2000, FASTEST_INTERVAL = 1000; // these are in Milliseconds, not final to allow them to be changed via message
    // lists for permissions
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected = new ArrayList<>();
    private ArrayList<String> permissions = new ArrayList<>();
    // integer for permissions results request
    private static final int ALL_PERMISSIONS_RESULT = 1011;

    // this is the Ably Channel used
    private Channel channel;

    private final static String API_KEY = "INSERT_ABLY_API_KEY_HERE"; /* Sign up at ably.io to get your API key */

    /* RuntimeException will be thrown if API_KEY will not be set to a proper one */
    static {
        if (API_KEY.contains("INSERT")) {
            throw new RuntimeException("API key is not set, sign up at ably.io to get yours");
        }
    }

    /*
      This app firstly attempts to get permission to check the location of the device in onCreate().
      Once this is done, we initialize Ably in initAbly(), and subscribe to a channel, listening for
      commands on what rate of position updates are desired by the client.
      Finally, we start up the location updates in startLocationUpdates(), with onConnected()
      and onLocationChanged() being used to send the initial and repeated location updates respectively.
      The actual publish into Ably is done in publishMessage().
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        location_view = findViewById(R.id.location);
        // we add permissions we need to request location of the users
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        permissionsToRequest = permissionsToRequest(permissions);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(permissionsToRequest.toArray(
                        new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
            }
        }

        // we build google api client
        googleApiClient = new GoogleApiClient.Builder(this).
                addApi(LocationServices.API).
                addConnectionCallbacks(this).
                addOnConnectionFailedListener(this).build();


        try {
            initAbly();
        } catch (AblyException e) {
            e.printStackTrace();
        }

    }

    private ArrayList<String> permissionsToRequest(ArrayList<String> wantedPermissions) {
        ArrayList<String> result = new ArrayList<>();

        for (String perm : wantedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!checkPlayServices()) {
            location_view.setText("You need to install Google Play Services to use the App properly");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // stop location updates
        if (googleApiClient != null  &&  googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
            googleApiClient.disconnect();
        }
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
            } else {
                finish();
            }

            return false;
        }

        return true;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&  ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Permissions ok, we get last location
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);


        if (location != null) {
            location_view.setText("Latitude : " + location.getLatitude()
                    + "\nLongitude : " + location.getLongitude()
                    + "\nAccuracy : " + location.getAccuracy());


            JsonObject payload = new JsonObject();
            payload.addProperty("Lon", location.getLongitude());
            payload.addProperty("Lat", location.getLatitude());
            payload.addProperty("Acc", location.getAccuracy());

            /* publishes a message into Ably with the device's location */
            try {
                publishMessage(payload.toString());
            } catch (AblyException e) {
                e.printStackTrace();
            }


        }

        startLocationUpdates();
    }

    private void startLocationUpdates() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&  ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You need to enable permissions to display location !", Toast.LENGTH_SHORT).show();
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    //The idea is to send just the required data nothing more. This is the Latitude,longitude and the Accuracy.
    // The exact message format can be strings or JSON or even a binary blob.
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            location_view.setText("Latitude : " + location.getLatitude()
                    + "\nLongitude : " + location.getLongitude()
                    + "\nAccuracy : " + location.getAccuracy());

            JsonObject payload = new JsonObject();
            payload.addProperty("Lon", location.getLongitude());
            payload.addProperty("Lat", location.getLatitude());
            payload.addProperty("Acc", location.getAccuracy());

            /* publishes a message into Ably with the device's location */
            try {
                publishMessage(payload.toString());
            } catch (AblyException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case ALL_PERMISSIONS_RESULT:
                for (String perm : permissionsToRequest) {
                    if (!hasPermission(perm)) {
                        permissionsRejected.add(perm);
                    }
                }
                // If any permissions are rejected, try to request them again
                if (permissionsRejected.size() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                            new AlertDialog.Builder(MainActivity.this).
                                    setMessage("These permissions are mandatory to get your location. You need to allow them.").
                                    setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermissions(permissionsRejected.
                                                        toArray(new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT);
                                            }
                                        }
                                    }).setNegativeButton("Cancel", null).create().show();

                            return;
                        }
                    }
                } else {
                    if (googleApiClient != null) {
                        googleApiClient.connect();
                    }
                }

                break;
        }
    }

    private void initAbly() throws AblyException {
        ClientOptions clientOptions = new ClientOptions();
        clientOptions.key = API_KEY;
        clientOptions.echoMessages = false;
        AblyRealtime realtime = new AblyRealtime(clientOptions);
        /* Get locations channel you can subscribe to */
        channel = realtime.channels.get("agent002.delivery223.locations");
        /* The rate at which this app sends location updates can be changed via messages sent on the above channel */
        channel.subscribe(new Channel.MessageListener() {
            @Override
            public void onMessage(Message messages) {
                /* show a toast when message is received */
                Toast.makeText(getBaseContext(), "Message received: " + (String)messages.data, Toast.LENGTH_SHORT).show();

                try {
                    if(((JsonObject)messages.data).has("speed")){
                        UPDATE_INTERVAL = ((JsonObject)messages.data).get("speed").getAsInt()*1000;
                        FASTEST_INTERVAL = UPDATE_INTERVAL;
                    }
                }
                catch(Exception e){
                    Log.e("Ably_GPS", "Issue with message data, " + messages.data);
                }
            }
        });
    }

    /* Add AblyException to method signature as Channel#publish method can throw one */
    public void publishMessage(String message) throws AblyException {

        channel.publish("update", message, new CompletionListener() {
            @Override
            public void onSuccess() {
                /* Show success message when message is sent */
                Toast.makeText(getBaseContext(), "Message sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(ErrorInfo reason) {
                /* Show error message when something goes wrong */
                Toast.makeText(getBaseContext(), "Message not sent, error occurred: " + reason.message, Toast.LENGTH_LONG).show();
            }
        });
    }
}