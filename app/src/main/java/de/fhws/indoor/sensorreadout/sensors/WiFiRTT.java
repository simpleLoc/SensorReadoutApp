package de.fhws.indoor.sensorreadout.sensors;
//package android.net.wifi;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanResultHack;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Wifi RTT sensor exporting time-of-flight measurements.
 * @author Markus Bullmann
 */
public class WiFiRTT extends mySensor {

    private final Activity act;
    private final WifiRttManager rttManager;
    private Executor mainExecutor;

    private Thread ftmThread;
    private boolean ftmRunning;


    public WiFiRTT(final Activity act) {
        this.act = act;
        this.rttManager = (WifiRttManager) act.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        this.mainExecutor = act.getMainExecutor();
    }

    @Override
    public void onResume(Activity act) {
        startScan();
    }

    @Override
    public void onPause(Activity act) {
        stopScan();
    }

    private void startScan() {
        if (ftmRunning)
            return;

        ftmRunning = true;

        final ArrayList<MacAddress> macs = new ArrayList<>();
        macs.add(MacAddress.fromString("38:de:ad:6d:77:25"));    // NUC 1
        macs.add(MacAddress.fromString("38:de:ad:6d:60:ff"));    // NUC 2
        macs.add(MacAddress.fromString("1c:1b:b5:ef:a2:9a"));    // NUC 3
        macs.add(MacAddress.fromString("1c:1b:b5:ec:d1:82"));    // NUC 4
        macs.add(MacAddress.fromString("d0:c6:37:bc:5c:41"));    // NUC 5
        macs.add(MacAddress.fromString("d0:c6:37:bc:77:8a"));    // NUC 6
        macs.add(MacAddress.fromString("d0:c6:37:bc:77:ad"));    // NUC 7
        macs.add(MacAddress.fromString("d0:c6:37:bc:6b:4b"));    // NUC 8

        ftmThread = new Thread() {
            public void run() {
                while (ftmRunning) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    startRangingOnMacs(macs);
                }
            }
        };

        ftmThread.start();
    }

    private void stopScan() {
        ftmRunning = false;
    }

    private void startRangingOnMacs(final ArrayList<MacAddress> macs) {
        RangingRequest.Builder builder = new RangingRequest.Builder();

        for (final MacAddress mac : macs) {
            //builder.addWifiAwarePeer(mac);

            ScanResult sr = ScanResultHack.createScanResultFromMacAddress(mac);
            builder.addAccessPoint(sr);
        }

        // fire#
        if (ActivityCompat.checkSelfPermission(act, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ftmRunning = false;
        } else {
            final RangingRequest request = builder.build();
            rttManager.startRanging(request, mainExecutor, rangeCallback);
        }
    }



    // result callback
    private final RangingResultCallback rangeCallback = new RangingResultCallback() {
        @Override
        public void onRangingFailure(final int i) {
            //emitter.onError(new RuntimeException("The WiFi-Ranging failed with error code: " + i));
            Log.d("RTT", "onRangingFailure: " + i);
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

                    //Log.d("RTT", mac.toString() + " " + dist + " " + stdDevDist + " " + rssi);
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


                    listener.onData(SensorType.WIFIRTT,timeStampInNS, sb.toString());
                }
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.P)
    public static final boolean isSupported(Context context) {
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
