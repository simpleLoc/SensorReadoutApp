package de.fhws.indoor.sensorreadout;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup.LayoutParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import de.fhws.indoor.libsmartphonesensors.SensorManager;
import de.fhws.indoor.libsmartphonesensors.helpers.WifiScanProvider;
import de.fhws.indoor.sensorreadout.loggers.Logger;
import de.fhws.indoor.sensorreadout.loggers.OrderedLogger;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphonesensors.sensors.GroundTruth;
import de.fhws.indoor.libsmartphonesensors.PedestrianActivity;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFiRTTScan;
import de.fhws.indoor.libsmartphonesensors.SensorType;


public class MainActivity extends AppCompatActivity {

    private static final long DEFAULT_WIFI_SCAN_INTERVAL = (Build.VERSION.SDK_INT == 28 ? 30 : 1);

    // sounds
    MediaPlayer mpStart;
    MediaPlayer mpStop;
    MediaPlayer mpGround;
    MediaPlayer mpFailure;

    // sensors
    SensorManager sensorManager = new SensorManager();

    //private final Logger logger = new Logger(this);
    //private final LoggerRAM logger = new LoggerRAM(this);
    private final Logger logger = new OrderedLogger(this);
    private Button btnStart;
    private Button btnMetadata;
    private Button btnStop;
    private Button btnGround;
    private Button btnSettings;
    private ProgressBar prgCacheFillStatus;
    private TableLayout activityButtonContainer;
    private HashMap<PedestrianActivity, PedestrianActivityButton> activityButtons = new HashMap<>();
    private PedestrianActivity currentPedestrianActivity = PedestrianActivity.STANDING;
    private Timer statisticsTimer = null;

    private int groundTruthCounter = 0;
    private boolean isInitialized = false;

    final private int MY_PERMISSIONS_REQUEST_READ_BT = 123;
    final private int MY_PERMISSIONS_REQUEST_BT_SCAN = 124;
    final private int MY_PERMISSIONS_REQUEST_BT_CONNECT = 125;
    final private int MY_PERMISSIONS_REQUEST_READ_HEART = 321;

    // file metadata
    private String metaPerson = "";
    private String metaComment = "";

    // static context access
    private static Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // This static call will reset default values for preferences only on the first ever read
        PreferenceManager.setDefaultValues(getBaseContext(), R.xml.preferences, false);

        // context access
        MainActivity.context = getApplicationContext();

        // setup sound-effects
        mpStart = MediaPlayer.create(this, R.raw.go);
        mpStop = MediaPlayer.create(this, R.raw.go);
        mpGround = MediaPlayer.create(this, R.raw.go);
        mpFailure = MediaPlayer.create(this, R.raw.error);

        //configure sensorManager
        sensorManager.addSensorListener((timestamp, id, csv) -> {
            logger.addCSV(id, timestamp, csv);
            // update UI for WIFI/BEACON/GPS
            if(id == SensorType.WIFI) { runOnUiThread(() -> loadCounterWifi++); }
            if(id == SensorType.WIFIRTT) { runOnUiThread(() -> loadCounterWifiRTT++); }
            if(id == SensorType.IBEACON) { runOnUiThread(() -> loadCounterBeacon++); }
            if(id == SensorType.GPS) { runOnUiThread(() -> loadCounterGPS++); }
            if(id == SensorType.DECAWAVE_UWB) { runOnUiThread(() -> loadCounterUWB++); }
        });

        //init Path spinner
        final Spinner pathSpinner = (Spinner) findViewById(R.id.pathspinner);
        List<String> pathList = new ArrayList<String>();
        for (int i=0; i<=255; i++){
            pathList.add("Path: " + i);
        }
        ArrayAdapter<String> pathDataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, pathList);
        pathDataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pathSpinner.setAdapter(pathDataAdapter);

        //init GroundTruthPoint spinner
        final Spinner groundSpinner = (Spinner) findViewById(R.id.groundspinner);
        List<String> groundList = new ArrayList<String>();
        for (int i=0; i<=255; i++){
            groundList.add("Num: " + i);
        }
        ArrayAdapter<String> groundDataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, groundList);
        groundDataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groundSpinner.setAdapter(groundDataAdapter);

        //get Buttons
        btnStart = (Button) findViewById(R.id.btnStart);
        btnMetadata = (Button) findViewById(R.id.btnMetadata);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnGround = (Button) findViewById(R.id.btnGround);
        btnSettings = (Button) findViewById(R.id.btnSettings);
        activityButtonContainer = (TableLayout) findViewById(R.id.pedestrianActivityButtonContainer);

        prgCacheFillStatus = (ProgressBar) findViewById(R.id.prgCacheFillStatus);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {

                if(!isInitialized) {
                    //if(!runPreStartChecks()) { return; }
                    start();
                    isInitialized = true;
                    playSound(mpStart);

                    final GroundTruth groundTruth = sensorManager.getSensor(GroundTruth.class);
                    //Write path id and ground truth point num
                    groundTruth.writeInitData(Integer.parseInt(pathSpinner.getSelectedItem().toString().replaceAll("[\\D]", "")),
                            Integer.parseInt(groundSpinner.getSelectedItem().toString().replaceAll("[\\D]", "")));

                    //Write the first groundTruthPoint
                    groundTruth.writeGroundTruth(groundTruthCounter);

                    //Write first activity
                    logger.addCSV(SensorType.PEDESTRIAN_ACTIVITY, Logger.BEGINNING_TS, PedestrianActivity.STANDING.toString() + ";" + PedestrianActivity.STANDING.ordinal());

                    //Disable the spinners
                    groundSpinner.setEnabled(false);
                    pathSpinner.setEnabled(false);
                } else {
                    playSound(mpFailure);
                }
            }
        });

        btnMetadata.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if(!isInitialized){
                    MetadataFragment metadataDialog = new MetadataFragment(metaPerson, metaComment, new MetadataFragment.ResultListener() {
                        @Override public void onCommit(String person, String comment) {
                            metaPerson = person;
                            metaComment = comment;
                            Log.d("MetadataDialog", "Person: " + person + " Comment: " + comment);
                        }

                        @Override public void onClose() {}
                    });
                    metadataDialog.show(getSupportFragmentManager(), "metadata");
                }
                else{
                    playSound(mpFailure);
                }
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if(isInitialized) {
                    final GroundTruth groundTruth = sensorManager.getSensor(GroundTruth.class);
                    //write the last groundTruthPoint
                    groundTruth.writeGroundTruth(++groundTruthCounter);
                    groundTruthCounter = 0;

                    btnGround.setText("Ground Truth");
                    stop();
                    resetStatistics();
                    isInitialized = false;
                    playSound(mpStop);

                    //Enable the spinners
                    groundSpinner.setEnabled(true);
                    pathSpinner.setEnabled(true);

                    //reset activity buttons
                    setActivityBtn(PedestrianActivity.STANDING, false);
                }
                else{
                    playSound(mpFailure);
                }
            }
        });

        btnGround.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if(isInitialized) {

                    String numGroundTruthPoints = groundSpinner.getSelectedItem().toString().replaceAll("[\\D]", "");

                    btnGround.setText(Integer.toString(++groundTruthCounter) + " / "
                                    + numGroundTruthPoints
                    );
                    playSound(mpGround);

                    final GroundTruth groundTruth = sensorManager.getSensor(GroundTruth.class);
                    groundTruth.writeGroundTruth(groundTruthCounter);
                }
                else{
                    playSound(mpFailure);
                }
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isInitialized) { // Only allow when not currently running
                    startActivity(new Intent(context, SettingsActivity.class));
                } else {
                    playSound(mpFailure);
                }
            }
        });
    }


    private void setActivityBtn(PedestrianActivity newActivity, boolean logChange){
        if(activityButtons.containsKey(currentPedestrianActivity)) {
            activityButtons.get(currentPedestrianActivity).setActivity(false);
        }
        currentPedestrianActivity = newActivity;
        activityButtons.get(newActivity).setActivity(true);
        if(logChange) {
            logger.addCSV(SensorType.PEDESTRIAN_ACTIVITY, SystemClock.elapsedRealtimeNanos(), newActivity.toString() + ";" + newActivity.ordinal());
        }
    }

    private void start() {
        logger.start(new Logger.FileMetadata(metaPerson, metaComment));
        final TextView txt = (TextView) findViewById(R.id.txtFile);
        txt.setText(logger.getName());

        try {
            sensorManager.start(this);
        } catch (Exception e) {
            e.printStackTrace();
            //TODO: ui feedback?
        }
        statisticsTimer = new Timer();
        statisticsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateDiagnostics(SystemClock.elapsedRealtimeNanos());
            }
        }, 250, 250);
    }

    private void stop() {
        statisticsTimer.cancel();
        try {
            sensorManager.stop(this);
        } catch (Exception e) {
            e.printStackTrace();
            //TODO: ui feedback?
        }
        logger.stop();
        updateDiagnostics(SystemClock.elapsedRealtimeNanos());
        ((TextView) findViewById(R.id.txtWifi)).setText("-");
        ((TextView) findViewById(R.id.txtBeacon)).setText("-");
        ((TextView) findViewById(R.id.txtGPS)).setText("-");
    }

    /** new sensor data */
    private volatile int loadCounterWifi = 0;
    private volatile int loadCounterWifiRTT = 0;
    private volatile int loadCounterBeacon = 0;
    private volatile int loadCounterGPS = 0;
    private volatile int loadCounterUWB = 0;
    private void resetStatistics() {
        loadCounterWifi = 0;
        loadCounterWifiRTT = 0;
        loadCounterBeacon = 0;
        loadCounterGPS = 0;
        loadCounterUWB = 0;
    }
    private String makeStatusString(long evtCnt, String evtText) {
        if(evtCnt == 0) { return "-"; }
        return (evtCnt % 2 == 0) ? evtText.toLowerCase() : evtText.toUpperCase();
    }

    private void updateDiagnostics(final long timestamp) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView txt = (TextView) findViewById(R.id.txtBuffer);
                final float elapsedMinutes = (timestamp - logger.getStartTS()) / 1000.0f / 1000.0f / 1000.0f / 60.0f;
                final int kBPerMin = (int) (logger.getSizeTotal() / 1024.0f / elapsedMinutes);
                txt.setText((logger.getSizeTotal() / 1024) + "kB, " + logger.getEventCnt() + "ev , " + kBPerMin + "kB/m");
                prgCacheFillStatus.setProgress((int)(logger.getCacheLevel() * 1000));

                final TextView txtWifi = (TextView) findViewById(R.id.txtWifi);
                txtWifi.setText(makeStatusString(loadCounterWifi, "wi"));
                final TextView txtWifiRTT = (TextView) findViewById(R.id.txtWifiRTT);
                txtWifiRTT.setText(makeStatusString(loadCounterWifiRTT, "rtt"));
                final TextView txtBeacon = (TextView) findViewById(R.id.txtBeacon);
                txtBeacon.setText(makeStatusString(loadCounterBeacon, "ib"));
                final TextView txtGPS = (TextView) findViewById(R.id.txtGPS);

                txtGPS.setText(makeStatusString(loadCounterGPS, "gps"));
                final TextView txtUWB = (TextView) findViewById(R.id.txtUWB);
                DecawaveUWB sensorUWB = sensorManager.getSensor(DecawaveUWB.class);
                if(sensorUWB != null) {
                    if(sensorUWB.isConnectedToTag()) {
                        txtUWB.setText(makeStatusString(loadCounterUWB, "uwb"));
                    } else {
                        txtUWB.setText(sensorUWB.isCurrentlyConnecting() ? "⌛" : "✖");
                    }
                }
            }
        });
    }

    /** pause activity */
    protected void onPause() {
        //stop();
        super.onPause();
    }

    protected void onStart() {
        super.onStart();
        if(!isInitialized) {
            // Do not apply new settings when recording is currently running, since we can't have come
            // from the SettingsActivity, then.
            setupActivityButtons();
            setupSensors();
        }
    }

    /** resume activity */
    protected void onResume() {
        super.onResume();

        //print memory info
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        long availableMegs = mi.availMem / 1048576L;

        Runtime info = Runtime.getRuntime();
        long freeSize = info.freeMemory();
        long totalSize = info.totalMemory();
        long usedSize = totalSize - freeSize;

        Log.d("MemoryInfo: ", String.valueOf(availableMegs));
        Log.d("FreeSize: ", String.valueOf(freeSize));
        Log.d("TotalSize: ", String.valueOf(totalSize));
        Log.d("usedSize: ", String.valueOf(usedSize));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }
    }

    /** static context access */
    public static Context getAppContext() {
        return MainActivity.context;
    }






    protected void setupActivityButtons() {
        // cleanup before recreation
        for(int i = 0; i < activityButtonContainer.getChildCount(); ++i) {
            TableRow buttonRow = (TableRow)activityButtonContainer.getChildAt(i);
            buttonRow.removeAllViews();
        }
        activityButtonContainer.removeAllViews();
        activityButtons.clear();

        // setup activity buttons
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> activeActivities = preferences.getStringSet("prefActiveActions", new HashSet<String>());

        activityButtons.put(PedestrianActivity.STANDING, new PedestrianActivityButton(this, PedestrianActivity.STANDING, R.drawable.ic_standing));
        if(activeActivities.contains("WALK")) {
            activityButtons.put(PedestrianActivity.WALK, new PedestrianActivityButton(this, PedestrianActivity.WALK, R.drawable.ic_walk));
        }
        if(activeActivities.contains("ELEVATOR")) {
            activityButtons.put(PedestrianActivity.ELEVATOR_UP, new PedestrianActivityButton(this, PedestrianActivity.ELEVATOR_UP, R.drawable.ic_elevator_up));
            activityButtons.put(PedestrianActivity.ELEVATOR_DOWN, new PedestrianActivityButton(this, PedestrianActivity.ELEVATOR_DOWN, R.drawable.ic_elevator_down));
        }
        if(activeActivities.contains("STAIRS")) {
            activityButtons.put(PedestrianActivity.STAIRS_UP, new PedestrianActivityButton(this, PedestrianActivity.STAIRS_UP, R.drawable.ic_stairs_up));
            activityButtons.put(PedestrianActivity.STAIRS_DOWN, new PedestrianActivityButton(this, PedestrianActivity.STAIRS_DOWN, R.drawable.ic_stairs_down));
        }
        if(activeActivities.contains("MESS_AROUND")) {
            activityButtons.put(PedestrianActivity.MESS_AROUND, new PedestrianActivityButton(this, PedestrianActivity.MESS_AROUND, R.drawable.ic_mess_around));
        }
        int activityButtonColumns = 1;
        if(activityButtons.size() > 6) { activityButtonColumns = 4; }
        else if(activityButtons.size() > 4) { activityButtonColumns = 3; }
        else if(activityButtons.size() > 2) { activityButtonColumns = 2; }

        TableLayout.LayoutParams rowLayout = new TableLayout.LayoutParams();
        rowLayout.width = LayoutParams.MATCH_PARENT;
        rowLayout.height = LayoutParams.WRAP_CONTENT;
        rowLayout.weight = 1.0f;
        TableRow.LayoutParams columnLayout = new TableRow.LayoutParams();
        columnLayout.height = LayoutParams.MATCH_PARENT;
        columnLayout.width = 1; // set minWidth to 1, because this will be stretched, and the images adjust in size
        columnLayout.weight = 1.0f;
        PedestrianActivity[] activeActiviesArray = activityButtons.keySet().toArray(new PedestrianActivity[activityButtons.size()]);
        Arrays.sort(activeActiviesArray);
        TableRow currentButtonRow = null;
        for(int i = 0; i < activeActiviesArray.length; ++i) {
            final PedestrianActivityButton button = activityButtons.get(activeActiviesArray[i]);
            if(i % activityButtonColumns == 0) {
                currentButtonRow = new TableRow(this);
                currentButtonRow.setWeightSum(1.0f * activityButtonColumns);
                activityButtonContainer.addView(currentButtonRow, rowLayout);
            }
            currentButtonRow.addView(button, columnLayout);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(isInitialized) {
                        setActivityBtn(button.getPedestrianActivity(), true);
                    }
                    else {
                        playSound(mpFailure);
                    }
                }
            });
        }

        //set current activity
        setActivityBtn(PedestrianActivity.STANDING, false);
    }

    protected void setupSensors() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        long wifiScanIntervalMSec = Long.parseLong(preferences.getString("prefWifiScanIntervalMSec", Long.toString(DEFAULT_WIFI_SCAN_INTERVAL)));
        final WifiScanProvider wifiScanProvider = new WifiScanProvider(this, wifiScanIntervalMSec);
        Set<String> activeSensors = preferences.getStringSet("prefActiveSensors", new HashSet<String>());

        SensorManager.Config config = new SensorManager.Config();
        config.hasPhone = activeSensors.contains("PHONE");
        config.hasGPS = activeSensors.contains("GPS");
        config.hasWifi = activeSensors.contains("WIFI");
        config.hasWifiRTT = activeSensors.contains("WIFIRTTSCAN");
        config.hasBluetooth = activeSensors.contains("BLUETOOTH");
        config.hasDecawaveUWB = activeSensors.contains("DECAWAVE_UWB");
        config.hasStepDetector = activeSensors.contains("STEP_DETECTOR");
        config.hasHeadingChange = activeSensors.contains("HEADING_CHANGE");

        config.decawaveUWBTagMacAddress = preferences.getString("prefDecawaveUWBTagMacAddress", "");
        config.wifiScanIntervalSec = Long.parseLong(preferences.getString("prefWifiScanIntervalMSec", Long.toString(DEFAULT_WIFI_SCAN_INTERVAL)));

        try {
            sensorManager.configure(this, config);
        } catch (Exception e) {
            e.printStackTrace();
            //TODO: ui feedback?
        }
    }

    /**
     * Method that checks whether the Recording can/should be started.
     */
    private boolean runPreStartChecks() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> activeSensors = preferences.getStringSet("prefActiveSensors", new HashSet<String>());
        if(activeSensors.contains("WIFIRTTSCAN") && !WiFiRTTScan.isSupported(this)) {
            new AlertDialog.Builder(this)
                    .setMessage("This smartphone does not support WifiRTT")
                    .show();
            return false;
        }
        if(activeSensors.contains("WIFI") || activeSensors.contains("WIFIRTTSCAN")) {
            if(Build.VERSION.SDK_INT == 28) { // there is no way to scan wifi in Android 9
                new AlertDialog.Builder(this)
                        .setMessage("Android 9 is not supported for Wifi-Scanning!")
                        .show();
                return false;
            }
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            boolean scanThrottleActive = false;
            try { // since android 9, wifi scan speed is crippled
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scanThrottleActive = wifiManager.isScanThrottleEnabled();
                } else {
                    scanThrottleActive = (Settings.Global.getInt(this.getContentResolver(), "wifi_scan_throttle_enabled") == 1);
                }
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            if(scanThrottleActive) {
                new AlertDialog.Builder(this)
                        .setMessage("A wifi sensor is requested, but your Smartphone settings cripple wifi scanning!")
                        .show();
                return false;
            }
        }
        return true;
    }

    private void playSound(MediaPlayer player) {
        if(player.isPlaying()) {
            player.seekTo(0);
        } else {
            player.start();
        }
    }

}
