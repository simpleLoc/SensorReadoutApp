package de.fhws.indoor.sensorreadout.sensors;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.content.ContextCompat;

import de.fhws.indoor.sensorreadout.MyException;

/**
 * GPS sensor.
 */
@TargetApi(23)
public class Gps extends mySensor implements LocationListener {

    private Activity act;
    private LocationManager locationManager;
    private Location location;

    public Gps(Activity act) {

        this.act = act;
        initGPS();

    }

    private void initGPS(){
        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( act, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( act, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return  ;
        }

        try   {
            this.locationManager = (LocationManager) act.getSystemService(Context.LOCATION_SERVICE);

            // Get GPS
            boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            if (isGPSEnabled)  {

                //get the most accurate provider
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_HIGH);
                String provider = locationManager.getBestProvider(criteria, true);

                //use only gps and not network 0 and 0 for fastest updates possible
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        0,
                        0, this);

                if (locationManager != null)  {
                    location = locationManager.getLastKnownLocation(provider);
                    setMostRecentLocation(location);
                }
            }
        } catch (Exception ex)  {
            throw new MyException("error creating gps!");

        }
    }

    private void setMostRecentLocation(Location loc){
        this.location = loc;
    }

    @Override
    public void onResume(Activity act) {
        initGPS();
    }

    @Override
    public void onPause(Activity act) {
        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;

        // inform listeners
        if (listener != null){
            listener.onData(SensorType.GRAVITY, location.getElapsedRealtimeNanos(), //TODO: Is this correct? SystemClock.elapsedRealtimeNanos() otherwise..
                    Double.toString(location.getLatitude()) + ";" +
                    Double.toString(location.getLongitude()) + ";" +
                    Double.toString(location.getAltitude()) + ";" +
                    Double.toString(location.getBearing())
            );
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
