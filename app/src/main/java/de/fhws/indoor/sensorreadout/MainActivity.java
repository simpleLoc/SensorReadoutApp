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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorManager;
import de.fhws.indoor.libsmartphonesensors.io.RecordingManager;
import de.fhws.indoor.libsmartphonesensors.io.RecordingSession;
import de.fhws.indoor.libsmartphonesensors.loggers.DataFolder;
import de.fhws.indoor.libsmartphonesensors.loggers.Logger;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphonesensors.sensors.GroundTruth;
import de.fhws.indoor.libsmartphonesensors.PedestrianActivity;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFi;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFiRTTScan;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.loggers.TimedOrderedLogger;
import de.fhws.indoor.libsmartphonesensors.ui.EventCounterView;
import de.fhws.indoor.libsmartphonesensors.util.permissions.AppCompatMultiPermissionRequester;
import de.fhws.indoor.libsmartphonesensors.util.permissions.IPermissionRequester;
import de.fhws.indoor.sensorreadout.dialogs.MetadataFragment;
import de.fhws.indoor.sensorreadout.dialogs.RecordingSuccessfulDialog;


public class MainActivity extends AppCompatActivity {

    private static final long DEFAULT_WIFI_SCAN_INTERVAL = (Build.VERSION.SDK_INT == 28 ? 30000 : 1);
    private static final PedestrianActivity DEFAULT_ACTIVITY = PedestrianActivity.STANDING;
    public static final String FILE_PROVIDER_AUTHORITY = "de.fhws.indoor.sensorreadout.fileprovider";

    // sounds
    MediaPlayer mpStart;
    MediaPlayer mpStop;
    MediaPlayer mpGround;
    MediaPlayer mpFailure;

    // sensors
    private SensorManager sensorManager;
    private AppCompatMultiPermissionRequester permissionRequester = null;
    private RecordingStateBLEBroadcaster recordingStateBLEBroadcaster;

    private final Logger logger = new TimedOrderedLogger(this);
    private RecordingManager recordingManager;

    private Button btnStart;
    private Button btnMetadata;
    private Button btnStop;
    private Button btnGround;
    private Button btnShareLast;
    private Button btnSettings;
    private ProgressBar prgCacheFillStatus;
    private TableLayout activityButtonContainer;
    private HashMap<PedestrianActivity, PedestrianActivityButton> activityButtons = new HashMap<>();
    private PedestrianActivity currentPedestrianActivity = DEFAULT_ACTIVITY;
    private Timer statisticsTimer = null;

    private int groundTruthCounter = 0;
    private long lastUserInteractionTs;
    private boolean isInitialized = false;

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

        permissionRequester = new AppCompatMultiPermissionRequester(this);
        // context access
        MainActivity.context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            recordingStateBLEBroadcaster = new RecordingStateBLEBroadcaster(permissionRequester);
        }
        recordingManager = new RecordingManager(new DataFolder(context, "sensorOutFiles").getFolder(), FILE_PROVIDER_AUTHORITY);

        // setup sound-effects
        mpStart = MediaPlayer.create(this, R.raw.go);
        mpStop = MediaPlayer.create(this, R.raw.go);
        mpGround = MediaPlayer.create(this, R.raw.go);
        mpFailure = MediaPlayer.create(this, R.raw.error);

        //configure sensorManager
        sensorManager = new SensorManager(new SensorDataInterface() {
            @Override
            public long getStartTimestamp() { return logger.getStartTS(); }

            @Override
            public void onData(long timestamp, SensorType id, String csv) {
                logger.addCSV(id, timestamp, csv);
                // update UI for WIFI/BEACON/GPS
                if(id == SensorType.WIFI) { loadCounterWifi.incrementAndGet(); }
                if(id == SensorType.WIFIRTT) { loadCounterWifiRTT.incrementAndGet(); }
                if(id == SensorType.IBEACON) { loadCounterBeacon.incrementAndGet(); }
                if(id == SensorType.GPS) { loadCounterGPS.incrementAndGet(); }
                if(id == SensorType.DECAWAVE_UWB) { loadCounterUWB.incrementAndGet(); }
            }

            @Override
            public OutputStream requestAuxiliaryChannel(String id) throws IOException {
                return recordingManager.getCurrentSession().openAuxiliaryChannel(id);
            }
        });

        //init Path spinner
        final Spinner pathSpinner = findViewById(R.id.pathspinner);
        List<String> pathList = new ArrayList<String>();
        for (int i=0; i<=255; i++) {
            pathList.add("Path: " + i);
        }
        ArrayAdapter<String> pathDataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, pathList);
        pathDataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pathSpinner.setAdapter(pathDataAdapter);

        //init GroundTruthPoint spinner
        final Spinner groundSpinner = findViewById(R.id.groundspinner);
        List<String> groundList = new ArrayList<String>();
        for (int i=0; i<=255; i++) {
            groundList.add("Num: " + i);
        }
        ArrayAdapter<String> groundDataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, groundList);
        groundDataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groundSpinner.setAdapter(groundDataAdapter);

        //get Buttons
        btnStart = findViewById(R.id.btnStart);
        btnMetadata = findViewById(R.id.btnMetadata);
        btnStop = findViewById(R.id.btnStop);
        btnGround = findViewById(R.id.btnGround);
        btnShareLast = findViewById(R.id.btnShareLast);
        btnSettings = findViewById(R.id.btnSettings);
        activityButtonContainer = findViewById(R.id.pedestrianActivityButtonContainer);

        prgCacheFillStatus = findViewById(R.id.prgCacheFillStatus);

        btnStart.setOnClickListener(v -> {
            if(!isInitialized) {
                lastUserInteractionTs = System.currentTimeMillis();
                //if(!runPreStartChecks()) { return; }
                start();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    recordingStateBLEBroadcaster.startRecording(recordingManager.getCurrentSession());
                }
                isInitialized = true;
                playSound(mpStart);
                resetStatistics();

                final GroundTruth groundTruth = sensorManager.getSensor(GroundTruth.class);
                //Write path id and ground truth point num
                groundTruth.writeInitData(
                    Integer.parseInt(pathSpinner.getSelectedItem().toString().replaceAll("[\\D]", "")),
                    Integer.parseInt(groundSpinner.getSelectedItem().toString().replaceAll("[\\D]", "")),
                    Logger.BEGINNING_TS
                );

                //Write the first groundTruthPoint
                groundTruth.writeGroundTruth(groundTruthCounter, Logger.BEGINNING_TS);

                //Write first activity
                logger.addCSV(SensorType.PEDESTRIAN_ACTIVITY, Logger.BEGINNING_TS, DEFAULT_ACTIVITY.toString() + ";" + DEFAULT_ACTIVITY.id());

                //Disable the spinners
                groundSpinner.setEnabled(false);
                pathSpinner.setEnabled(false);
                btnShareLast.setEnabled(false);

                dumpVendorInfo();
            } else {
                playSound(mpFailure);
            }
        });

        btnMetadata.setOnClickListener(v -> {
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
            } else {
                playSound(mpFailure);
            }
        });

        btnStop.setOnClickListener(v -> {
            if(isInitialized) {
                final GroundTruth groundTruth = sensorManager.getSensor(GroundTruth.class);
                //write the last groundTruthPoint
                groundTruth.writeGroundTruth(++groundTruthCounter, SystemClock.elapsedRealtimeNanos());
                groundTruthCounter = 0;

                btnGround.setText("Ground Truth");
                stop();
                resetStatistics();
                isInitialized = false;
                playSound(mpStop);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    recordingStateBLEBroadcaster.stopRecording(recordingManager.getCurrentSession());
                }

                //Enable the spinners
                groundSpinner.setEnabled(true);
                pathSpinner.setEnabled(true);
                btnShareLast.setEnabled(true);

                //reset activity buttons
                setActivityBtn(DEFAULT_ACTIVITY, false);

                // open recording completed popup to ask user what to do with the recording
                RecordingSuccessfulDialog.show(this, new RecordingSuccessfulDialog.ResultListener() {
                    @Override public void onCommit() { recordingManager.getCurrentSession().close(); }
                    @Override public void onCommitWithRemark(String remark) {
                        try {
                            recordingManager.getCurrentSession().closeWithRemark(remark);
                        } catch (IOException e) { e.printStackTrace(); }
                    }
                    @Override public void onReject() { recordingManager.getCurrentSession().abort(); }
                });
            } else{
                playSound(mpFailure);
            }
        });

        btnGround.setOnClickListener(v -> {
            if(isInitialized) {
                lastUserInteractionTs = System.currentTimeMillis();
                String numGroundTruthPoints = groundSpinner.getSelectedItem().toString().replaceAll("[\\D]", "");

                btnGround.setText(Integer.toString(++groundTruthCounter) + " / " + numGroundTruthPoints);
                playSound(mpGround);

                final GroundTruth groundTruth = sensorManager.getSensor(GroundTruth.class);
                groundTruth.writeGroundTruth(groundTruthCounter, SystemClock.elapsedRealtimeNanos());
            }
            else{
                playSound(mpFailure);
            }
        });

        btnShareLast.setOnClickListener(v -> {
            recordingManager.shareLast(this);
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


    private void setActivityBtn(PedestrianActivity newActivity, boolean logChange) {
        if(activityButtons.containsKey(currentPedestrianActivity)) {
            activityButtons.get(currentPedestrianActivity).setActivity(false);
        }
        currentPedestrianActivity = newActivity;
        activityButtons.get(newActivity).setActivity(true);
        if(logChange) {
            logger.addCSV(SensorType.PEDESTRIAN_ACTIVITY, SystemClock.elapsedRealtimeNanos(), newActivity.toString() + ";" + newActivity.id());
        }
    }

    private void start() {
        try {
            RecordingSession recordingSession = recordingManager.startNewSession();
            logger.start(recordingSession, new Logger.FileMetadata(metaPerson, Build.MODEL + ", " + metaComment));
            final TextView txt = findViewById(R.id.txtFile);
            txt.setText(logger.getName());
            sensorManager.start(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start SensorManager", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Failed to stop SensorManager", Toast.LENGTH_LONG).show();
        }
        logger.stop();
        updateDiagnostics(SystemClock.elapsedRealtimeNanos());
    }

    /** new sensor data */
    private AtomicLong loadCounterWifi = new AtomicLong(0);
    private AtomicLong loadCounterWifiRTT = new AtomicLong(0);
    private AtomicLong loadCounterBeacon = new AtomicLong(0);
    private AtomicLong loadCounterGPS = new AtomicLong(0);
    private AtomicLong loadCounterUWB = new AtomicLong(0);
    private void resetStatistics() {
        loadCounterWifi.set(0);
        loadCounterWifiRTT.set(0);
        loadCounterBeacon.set(0);
        loadCounterGPS.set(0);
        loadCounterUWB.set(0);
    }
    private String makeStatusString(long evtCnt) {
        return (evtCnt == 0) ? "-" : Long.toString(evtCnt);
    }

    private void updateDiagnostics(final long timestamp) {
        runOnUiThread(() -> {
            { // update UI timer
                final TextView txtClock = findViewById(R.id.txtClock);
                long now = System.currentTimeMillis();
                long milliseconds = now - lastUserInteractionTs;
                long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
                milliseconds -= TimeUnit.MINUTES.toMillis(minutes);
                long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
                milliseconds -= TimeUnit.SECONDS.toMillis(seconds);
                txtClock.setText(String.format("%02d:%02d.%03d", minutes, seconds, milliseconds));
            }

            final TextView txt = (TextView) findViewById(R.id.txtBuffer);
            final float elapsedMinutes = (timestamp - logger.getStartTS()) / 1000.0f / 1000.0f / 1000.0f / 60.0f;
            final int kBPerMin = (int) (logger.getSizeTotal() / 1024.0f / elapsedMinutes);
            txt.setText((logger.getSizeTotal() / 1024) + "kB, " + logger.getEventCnt() + "ev\n" + kBPerMin + "kB/m");
            prgCacheFillStatus.setProgress((int)(logger.getCacheLevel() * 1000));

            EventCounterView evtCounterView = findViewById(R.id.event_counter_view);
            evtCounterView.updateCounterData(counterData -> {
                DecawaveUWB sensorUWB = sensorManager.getSensor(DecawaveUWB.class);
                WiFi sensorWifi = sensorManager.getSensor(WiFi.class);
                counterData.wifiEvtCnt = loadCounterWifi.get();
                counterData.wifiScanCnt = (sensorWifi != null) ? sensorWifi.getScanResultCount() : 0;
                counterData.bleEvtCnt = loadCounterBeacon.get();
                counterData.ftmEvtCnt = loadCounterWifiRTT.get();
                counterData.gpsEvtCnt = loadCounterGPS.get();
                counterData.uwbEvtCnt = loadCounterUWB.get();
                counterData.uwbState = EventCounterView.UWBState.from(sensorUWB);
            });
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
            button.setOnClickListener(v -> {
                lastUserInteractionTs = System.currentTimeMillis();
                if(isInitialized) {
                    setActivityBtn(button.getPedestrianActivity(), true);
                } else {
                    playSound(mpFailure);
                }
            });
        }

        //set current activity
        setActivityBtn(DEFAULT_ACTIVITY, false);
    }

    protected void setupSensors() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
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
        config.hasMicrophone = activeSensors.contains("MICROPHONE");

        config.decawaveUWBTagMacAddress = preferences.getString("prefDecawaveUWBTagMacAddress", "");
        config.wifiScanIntervalMSec = Long.parseLong(preferences.getString("prefWifiScanIntervalMSec", Long.toString(DEFAULT_WIFI_SCAN_INTERVAL)));
        config.ftmRangingIntervalMSec = Long.parseLong(preferences.getString("prefFtmRangingIntervalMSec", Long.toString(DEFAULT_WIFI_SCAN_INTERVAL)));

        try {
            sensorManager.configure(this, config, permissionRequester);
            permissionRequester.launch(() -> {});
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to configure sensors", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Method that checks whether the Recording can/should be started.
     */
    private boolean runPreStartChecks() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> activeSensors = preferences.getStringSet("prefActiveSensors", new HashSet<>());
        if(activeSensors.contains("WIFIRTTSCAN") && !WiFiRTTScan.isSupported(this)) {
            new AlertDialog.Builder(this)
                    .setMessage("This smartphone does not support WifiRTT")
                    .show();
            return false;
        }
        if(activeSensors.contains("WIFI") || activeSensors.contains("WIFIRTTSCAN")) {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            boolean scanThrottleActive = false;
            try { // since android 9, wifi scan speed is crippled
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11
                    scanThrottleActive = wifiManager.isScanThrottleEnabled();
                } else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) { // Android 10
                    scanThrottleActive = (Settings.Global.getInt(this.getContentResolver(), "wifi_scan_throttle_enabled") == 1);
                } else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.P) { // Android 9 - no way to unthrottle
                    scanThrottleActive = true;
                }
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            if(scanThrottleActive) {
                new AlertDialog.Builder(this)
                    .setMessage("A wifi sensor is requested, but either your Smartphone settings or the Android version cripple wifi scanning!")
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

    private void dumpVendorInfo() {
        File vendorFile = new File(recordingManager.getRootPath(), "vendors.txt");
        try {
            sensorManager.dumpVendorInformation(this, vendorFile);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to dump vendor info", Toast.LENGTH_LONG).show();
        }
    }
}
