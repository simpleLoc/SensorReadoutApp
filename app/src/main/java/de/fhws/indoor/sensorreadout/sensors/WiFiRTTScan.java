package de.fhws.indoor.sensorreadout.sensors;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

import de.fhws.indoor.sensorreadout.MyException;
import de.fhws.indoor.sensorreadout.helpers.WifiScanProvider;

/**
 * Wifi RTT sensor exporting time-of-flight measurements.
 * @author Steffen Kastner
 * @author Markus Ebner
 */
public class WiFiRTTScan extends mySensor implements WifiScanProvider.WifiScanCallback {
    private final String TAG = "WiFiRTTScan";

    private final Activity activity;
    private final WifiRttManager rttManager;
    private final WifiScanProvider wifiScanProvider;
    private final Executor mainExecutor;

    private Timer rangeTimer;
    private TimerTask rangingTask() {
        return new TimerTask() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            @Override
            public void run() {
                startRanging();
            }
        };
    }
    private final RangingResultCallback rangeCallback;

    private final HashMap<String, ScanResult> rttEnabledAPs = new HashMap<>();

    @RequiresApi(api = Build.VERSION_CODES.P)
    public WiFiRTTScan(Activity activity, WifiScanProvider wifiScanProvider) {
        this.activity = activity;
        this.wifiScanProvider = wifiScanProvider;
        this.rangeCallback = new WiFiRTTScanRangingCallback();

        this.rttManager = (WifiRttManager) activity.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        this.mainExecutor = activity.getMainExecutor();
    }

    @Override
    public void onResume(Activity act) {
        startScanningAndRanging();
    }

    @Override
    public void onPause(Activity act) {
        stopScanningAndRanging();
    }

    private void startScanningAndRanging() {
        wifiScanProvider.registerCallback(this);

        // range to all available APs all 200ms
        rangeTimer = new Timer();
        rangeTimer.scheduleAtFixedRate(rangingTask(), 0, 200);
    }

    private void stopScanningAndRanging() {
        wifiScanProvider.unregisterCallback(this);
        rangeTimer.cancel();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void startRanging() {
        if (rttEnabledAPs.isEmpty())
            return;


        LinkedList<RangingRequest.Builder> builders = new LinkedList<>();
        builders.add(new RangingRequest.Builder());
        int cnt = 0;
        for (ScanResult sr : rttEnabledAPs.values()) {
            if (cnt >= RangingRequest.getMaxPeers()) {
                builders.add(new RangingRequest.Builder());
                cnt = 0;
            }
            builders.getLast().addAccessPoint(sr);
            cnt++;
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Can not start ranging. Permission not granted");
            stopScanningAndRanging();
        } else {
            for (RangingRequest.Builder builder : builders) {
                final RangingRequest request = builder.build();
                rttManager.startRanging(request, mainExecutor, rangeCallback);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onScanResult(List<ScanResult> scanResults) {
        for(ScanResult sr : scanResults) {
            if(sr.is80211mcResponder() && !rttEnabledAPs.containsKey(sr.BSSID)) {
                Log.i(TAG, "Found new RTT-enabled AP: " + sr.BSSID);
                rttEnabledAPs.put(sr.BSSID, sr);
            }
        }
    }


    // result callback
    @RequiresApi(api = Build.VERSION_CODES.P)
    private class WiFiRTTScanRangingCallback extends RangingResultCallback {
        @Override
        public void onRangingFailure(final int i) {
            //emitter.onError(new RuntimeException("The WiFi-Ranging failed with error code: " + i));
            Log.d(TAG, "onRangingFailure: " + i);
        }

        @Override
        public void onRangingResults(final List<RangingResult> list) {
            //emitter.onSuccess(list);
            //Log.d("RTT", "onRangingResults: " + list.size());

            for (final RangingResult res : list) {

                int success = 0;
                long timeStampInNS = 0;
                MacAddress mac = res.getMacAddress();
                int dist = 0;
                int stdDevDist = 0;
                int rssi = 0;
                int numAttemptedMeas = 0;
                int numSuccessfulMeas = 0;

                if (res.getStatus() == RangingResult.STATUS_SUCCESS) {
                    success = 1;
                    timeStampInNS = res.getRangingTimestampMillis() * 1000000;
                    dist = res.getDistanceMm();
                    stdDevDist = res.getDistanceStdDevMm();
                    rssi = res.getRssi();
                    numAttemptedMeas = res.getNumAttemptedMeasurements();
                    numSuccessfulMeas = res.getNumSuccessfulMeasurements();

                    Log.d(TAG, mac.toString() + " " + dist + " " + stdDevDist + " " + rssi);
                } else {
                    //Log.d("RTT", mac.toString() + " FAILED");
                }

                if (listener != null) {
                    // success; mac; dist; stdDevDist; RSSI; numAttemptedMeas; numSuccessfulMeas
                    StringBuilder sb = new StringBuilder();

                    sb.append(success).append(';');
                    sb.append(Helper.stripMAC(mac.toString())).append(';');
                    sb.append(dist).append(';');
                    sb.append(stdDevDist).append(';');
                    sb.append(rssi).append(';');
                    sb.append(numAttemptedMeas).append(';');
                    sb.append(numSuccessfulMeas);

                    listener.onData(SensorType.WIFIRTT, timeStampInNS, sb.toString());
                }
            }
        }
    };

    public static boolean isSupported(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return false;

        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            return false;
        }

        WifiRttManager rttManager = (WifiRttManager) context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        if(rttManager == null) {
            return false;
        }

        return rttManager.isAvailable();
    }
}
