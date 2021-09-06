package de.fhws.indoor.sensorreadout.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import de.fhws.indoor.sensorreadout.math.MadgwickFilter;
import de.fhws.indoor.sensorreadout.math.Vec3;

/**
 * Virtual sensor, measuring the heading-change along the human z-axis
 * (purged from the dependence on the smartphone's own orientation by using
 * a MadgwickFilter to estimate and undo the smartphone orientation).
 * @author Markus Ebner
 */
public class HeadingChange extends mySensor implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private Sensor gyroscopeSensor;

    // internal heading estimation state
    private MadgwickFilter madgwickFilter = new MadgwickFilter(0.1);
    private Long lastUpdateTs = null;
    private int updatecnt = 0;
    private Vec3 lastAccel = new Vec3();
    private Vec3 lastGyro = new Vec3();

    /** ctor */
    public HeadingChange(final Activity act){
        // fetch the sensor manager from the activity
        sensorManager = (SensorManager) act.getSystemService(Context.SENSOR_SERVICE);

        // try to get each sensor
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    // ###########
    // # EVENTS
    // ###########
    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor == this.accelerometerSensor) {
            lastAccel.set(event.values[0], event.values[1], event.values[2]);
        } else if(event.sensor == this.gyroscopeSensor) {
            lastGyro.set(event.values[0], event.values[1], event.values[2]);

            // calculate headingChange
            if(lastUpdateTs != null) {
                long timeStep = (event.timestamp - lastUpdateTs);
                double timeStepFactor = ((double) timeStep) / 1000000000;
                madgwickFilter.calculcate(timeStep, lastAccel, lastGyro);
                Vec3 alignedGyro = madgwickFilter.getQuaternion().transformVector(lastGyro);
                double headingChange = alignedGyro.z * timeStepFactor;
                if(this.listener != null) {
                    this.listener.onData(event.timestamp, Double.toString(headingChange));
                }
            }
            lastUpdateTs = event.timestamp;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onResume(Activity act) {
        this.sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
        this.sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onPause(Activity act) {
        // detach from all events
        sensorManager.unregisterListener(this);
    }

}
