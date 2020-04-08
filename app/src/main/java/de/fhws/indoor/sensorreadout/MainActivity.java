package de.fhws.indoor.sensorreadout;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
//import android.support.wearable.activity.WearableActivity;
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

import de.fhws.indoor.sensorreadout.loggers.Logger;
import de.fhws.indoor.sensorreadout.loggers.OrderedLogger;
import de.fhws.indoor.sensorreadout.sensors.GpsNew;
import de.fhws.indoor.sensorreadout.sensors.GroundTruth;
import de.fhws.indoor.sensorreadout.sensors.PedestrianActivity;
import de.fhws.indoor.sensorreadout.sensors.PhoneSensors;
import de.fhws.indoor.sensorreadout.sensors.WiFi;
import de.fhws.indoor.sensorreadout.sensors.WiFiRTT;
import de.fhws.indoor.sensorreadout.sensors.iBeacon;
import de.fhws.indoor.sensorreadout.sensors.mySensor;
import de.fhws.indoor.sensorreadout.sensors.SensorType;


public class MainActivity extends AppCompatActivity {

    MediaPlayer mpStart;
    MediaPlayer mpStop;
    MediaPlayer mpGround;
    MediaPlayer mpFailure;

    private final ArrayList<mySensor> sensors = new ArrayList<mySensor>();
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

    private int groundTruthCounter = 0;
    private boolean isInitialized = false;

    final private int MY_PERMISSIONS_REQUEST_READ_BT = 123;
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

        // log GroundTruth ButtonClicks using sensor number 99
        final GroundTruth grndTruth = new GroundTruth(this);
        sensors.add(grndTruth);
        grndTruth.setListener(new mySensor.SensorListener() {
            @Override public void onData(final long timestamp, final String csv) { return; }
            @Override public void onData(SensorType id, final long timestamp, final String csv) {add(id, csv, timestamp); }
        });


        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {

                if(!isInitialized){
                    start();
                    isInitialized = true;
                    mpStart.start();

                    //Write path id and ground truth point num
                    grndTruth.writeInitData(Integer.parseInt(pathSpinner.getSelectedItem().toString().replaceAll("[\\D]", "")),
                            Integer.parseInt(groundSpinner.getSelectedItem().toString().replaceAll("[\\D]", "")));

                    //Write the first groundTruthPoint
                    grndTruth.writeGroundTruth(groundTruthCounter);

                    //Write first activity
                    add(SensorType.PEDESTRIAN_ACTIVITY, PedestrianActivity.STANDING.toString() + ";" + PedestrianActivity.STANDING.ordinal(), Logger.BEGINNING_TS);

                    //Disable the spinners
                    groundSpinner.setEnabled(false);
                    pathSpinner.setEnabled(false);
                }
                else{
                    mpFailure.start();
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
                    mpFailure.start();
                }
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if(isInitialized){
                    //write the last groundTruthPoint
                    grndTruth.writeGroundTruth(++groundTruthCounter);
                    groundTruthCounter = 0;

                    btnGround.setText("Ground Truth");
                    stop();
                    isInitialized = false;
                    mpStop.start();

                    //Enable the spinners
                    groundSpinner.setEnabled(true);
                    pathSpinner.setEnabled(true);

                    //reset activity buttons
                    setActivityBtn(PedestrianActivity.STANDING, false);
                }
                else{
                    mpFailure.start();
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
                    mpGround.start();

                    grndTruth.writeGroundTruth(groundTruthCounter);
                }
                else{
                    mpFailure.start();
                }
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isInitialized) { // Only allow when not currently running
                    startActivity(new Intent(context, SettingsActivity.class));
                } else {
                    mpFailure.start();
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
            add(SensorType.PEDESTRIAN_ACTIVITY, newActivity.toString() + ";" + newActivity.ordinal());
        }
    }

    private void start() {
        loadCounterAny = 0;
        logger.start(new Logger.FileMetadata(metaPerson, metaComment));
        final TextView txt = (TextView) findViewById(R.id.txtFile);
        txt.setText(logger.getName());
        for (final mySensor s : sensors) {s.onResume(this);}
    }

    private void stop() {
        for (final mySensor s : sensors) {s.onPause(this);}
        logger.stop();
        updateStatistics(SystemClock.elapsedRealtimeNanos());
        ((TextView) findViewById(R.id.txtWifi)).setText("-");
        ((TextView) findViewById(R.id.txtBeacon)).setText("-");
        ((TextView) findViewById(R.id.txtGPS)).setText("-");
    }

    /** new sensor data */
    private int loadCounterWifi = 0;
    private int loadCounterBeacon = 0;
    private int loadCounterGPS = 0;
    private int loadCounterAny = 0;
    private void add(final SensorType id, final String csv) {
        add(id, csv, SystemClock.elapsedRealtimeNanos());
    }
    private void add(final SensorType id, final String csv, final long timestamp) {

        logger.addCSV(id, timestamp, csv);


        // update UI for WIFI/BEACON/GPS
        if (id == SensorType.WIFIRTT || id == SensorType.IBEACON || id == SensorType.GPS) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (id == SensorType.WIFIRTT) {
                        final TextView txt = (TextView) findViewById(R.id.txtWifi);
                        txt.setText(((++loadCounterWifi % 7) == 0) ? "wi" : "WI");
                    } else if (id == SensorType.IBEACON) {
                        final TextView txt = (TextView) findViewById(R.id.txtBeacon);
                        txt.setText(((++loadCounterBeacon % 2) == 0) ? "ib" : "IB");
                    } else if (id == SensorType.GPS) {
                        final TextView txt = (TextView) findViewById(R.id.txtGPS);
                        txt.setText(((++loadCounterGPS % 2) == 0) ? "gps" : "GPS");
                    }
                }
            });
        }

        // dump buffer stats every x entries
        if (++loadCounterAny % 250 == 0) {
            updateStatistics(timestamp);
        }

    }

    private void updateStatistics(final long timestamp) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView txt = (TextView) findViewById(R.id.txtBuffer);
                final float elapsedMinutes = (timestamp - logger.getStartTS()) / 1000.0f / 1000.0f / 1000.0f / 60.0f;
                final int kBPerMin = (int) (logger.getSizeTotal() / 1024.0f / elapsedMinutes);
                txt.setText((logger.getSizeTotal() / 1024) + "k, " + logger.getEventCnt() + ", " + kBPerMin + "kB/m");
                prgCacheFillStatus.setProgress((int)(logger.getCacheLevel() * 1000));
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
                    else{
                        mpFailure.start();
                    }
                }
            });
        }

        //set current activity
        setActivityBtn(PedestrianActivity.STANDING, false);
    }

    protected void setupSensors() {
        //cleanup first
        sensors.clear();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> activeSensors = preferences.getStringSet("prefActiveSensors", new HashSet<String>());

        if(activeSensors.contains("PHONE")) {
            // heartbeat permission
//            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
//                    Manifest.permission.BODY_SENSORS)) {
//            } else {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.BODY_SENSORS},
//                        MY_PERMISSIONS_REQUEST_READ_HEART);
//            }

            //all Phone-Sensors (Accel, Gyro, Magnet, ...)
            final PhoneSensors phoneSensors = new PhoneSensors(this);
            sensors.add(phoneSensors);
            phoneSensors.setListener(new mySensor.SensorListener(){
                @Override public void onData(final long timestamp, final String csv) { return; }
                @Override public void onData(final SensorType id, final long timestamp, final String csv) {add(id, csv, timestamp); }
            });
        }
        if(activeSensors.contains("GPS")) {
            Log.i("Sensors", "Using GPS");
            //log gps using sensor number 16
            final GpsNew gps = new GpsNew(this);
            sensors.add(gps);
            gps.setListener(new mySensor.SensorListener(){
                @Override public void onData(final long timestamp, final String csv) { add(SensorType.GPS, csv, timestamp); }
                @Override public void onData(final SensorType id, final long timestamp, final String csv) {return; }
            });
        }
        if(activeSensors.contains("WIFI")) {
            boolean useWifiFtm = preferences.getBoolean("prefUseWifiFTM", false);
            if(useWifiFtm && WiFiRTT.isSupported(this)) {
                Log.i("Sensors", "Using Wifi[FTM]");
                // log wifi RTT using sensor number 17
                final WiFiRTT wifirtt = new WiFiRTT(this);
                sensors.add(wifirtt);
                wifirtt.setListener(new mySensor.SensorListener() {
                    @Override public void onData(final long timestamp, final String csv) { add(SensorType.WIFIRTT, csv, timestamp); }
                    @Override public void onData(final SensorType id, final long timestamp, final String csv) { add(SensorType.WIFIRTT, csv, timestamp); }
                });
            } else {
                Log.i("Sensors", "Using Wifi");
                // log wifi using sensor number 8
                final WiFi wifi = new WiFi(this);
                sensors.add(wifi);
                wifi.setListener(new mySensor.SensorListener() {
                    @Override public void onData(final long timestamp, final String csv) { add(SensorType.WIFI, csv, timestamp); }
                    @Override public void onData(final SensorType id, final long timestamp, final String csv) {return; }
                });
            }
        }
        if(activeSensors.contains("BLUETOOTH")) {
            Log.i("Sensors", "Using Bluetooth");
            // bluetooth permission
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_READ_BT);
            }

            //TODO: add something that asks the user to also enable GPS!

            // log iBeacons using sensor number 9
            final mySensor beacon;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                beacon = new iBeacon(this);
            } else {
                beacon = null;
                //beacon = new iBeaconOld(this);
            }

            if (beacon != null) {
                sensors.add(beacon);
                beacon.setListener(new mySensor.SensorListener() {
                    @Override public void onData(final long timestamp, final String csv) { add(SensorType.IBEACON, csv, timestamp); }
                    @Override public void onData(final SensorType id, final long timestamp, final String csv) {return; }
                });
            }
        }
    }


}
