package de.fhws.indoor.sensorreadout.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.fhws.indoor.sensorreadout.loggers.DataFolder;

/**
 * Sensor that surfaces all Sensors a phone has.
 * <p>
 *     While the term "sensor" is used for all implementations, such as Wifi and iBeacon, the term
 *     "sensor" in this context actually refers to a smartphone's sensors, such as
 *     Accelerometer, Gyroscope, MagneticField, Light, Pressure, ...
 *
 *     This Sensor implementation exports all sensors supported by the smartphone.
 * </p>
 *
 * Created by Toni on 25.03.2015.
 */
public class PhoneSensors extends mySensor implements SensorEventListener{

	//private static final int SENSOR_TYPE_HEARTRATE = 65562;

    private SensorManager sensorManager;
    private Sensor acc;
    private Sensor grav;
   	private Sensor lin_acc;
    private Sensor gyro;
    private Sensor magnet;
    private Sensor press;
	private Sensor ori;
	//private Sensor heart;
	private Sensor humidity;
	private Sensor rotationVector;
	private Sensor light;
	private Sensor temperature;
	private Sensor gameRotationVector;
	private Sensor stepDetector;

	/** local gravity copy (needed for orientation matrix) */
    private float[] mGravity = new float[3];
	/** local geomagnetic copy (needed for orientation matrix) */
    private float[] mGeomagnetic = new float[3];


	/** ctor */
    public PhoneSensors(final Activity act){

		// fetch the sensor manager from the activity
        sensorManager = (SensorManager) act.getSystemService(Context.SENSOR_SERVICE);

		// try to get each sensor
        acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        grav = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        lin_acc = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        press = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
		ori = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
		//heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
		humidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
		rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
		gameRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
		stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

		// dump sensor-vendor info to file
		dumpVendors(act);

	}

	final char NL = '\n';

	/** Write Vendors to file */
	private void dumpVendors(final Activity act) {

		final DataFolder folder = new DataFolder(act, "sensorOutFiles");
		final File file = new File(folder.getFolder(), "vendors.txt");

		try {

			final FileOutputStream fos = new FileOutputStream(file);
			final StringBuilder sb = new StringBuilder();

			// constructor smartphone details
			sb.append("[Device]").append(NL);
			sb.append("\tModel: " + android.os.Build.MODEL).append(NL);
			sb.append("\tAndroid: " + Build.VERSION.RELEASE).append(NL);
			sb.append(NL);

			// construct sensor details
			dumpSensor(sb, SensorType.ACCELEROMETER, acc);
			dumpSensor(sb, SensorType.GRAVITY, grav);
			dumpSensor(sb, SensorType.LINEAR_ACCELERATION, lin_acc);
			dumpSensor(sb, SensorType.GYROSCOPE, gyro);
			dumpSensor(sb, SensorType.MAGNETIC_FIELD, magnet);
			dumpSensor(sb, SensorType.PRESSURE, press);
			dumpSensor(sb, SensorType.RELATIVE_HUMIDITY, humidity);
			dumpSensor(sb, SensorType.ORIENTATION_OLD, ori);
			dumpSensor(sb, SensorType.LIGHT, light);
			dumpSensor(sb, SensorType.AMBIENT_TEMPERATURE, temperature);
			//dumpSensor(sb, SensorType.HEART_RATE, heart);
			dumpSensor(sb, SensorType.GAME_ROTATION_VECTOR, gameRotationVector);
			dumpSensor(sb, SensorType.STEP_DETECTOR, stepDetector);

			// write
			fos.write(sb.toString().getBytes());
			fos.close();

		}catch (final IOException e) {
			throw new RuntimeException(e);
		}

	}

	/** dump all details of the given sensor into the provided stringbuilder */
	private void dumpSensor(final StringBuilder sb, final SensorType type, final Sensor sensor) {
		sb.append("[Sensor]").append(NL);
		sb.append("\tour_id: ").append(type.id()).append(NL);
		sb.append("\ttype: ").append(type).append(NL);

		if (sensor != null) {
			sb.append("\tVendor: ").append(sensor.getVendor()).append(NL);
			sb.append("\tName: ").append(sensor.getName()).append(NL);
			sb.append("\tVersion: ").append(sensor.getVersion()).append(NL);
			sb.append("\tMinDelay: ").append(sensor.getMinDelay()).append(NL);
			//sb.append("\tMaxDelay: ").append(sensor.getMaxDelay()).append(NL);
			sb.append("\tMaxRange: ").append(sensor.getMaximumRange()).append(NL);
			sb.append("\tPower: ").append(sensor.getPower()).append(NL);
			//sb.append("ReportingMode: ").append(sensor.getReportingMode()).append(NL);
			sb.append("\tResolution: ").append(sensor.getResolution()).append(NL);
			sb.append("\tType: ").append(sensor.getType()).append(NL);
		} else {
			sb.append("\tnot available!\n");
		}
		sb.append("\n");
	}

    @Override
    public void onSensorChanged(SensorEvent event) {
		if(listener == null) { return; }
		// to compare with the other orientation
		if(event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
			// inform listeners
			listener.onData(SensorType.ORIENTATION_OLD, event.timestamp,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
		}
//		else if(event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
//
//			// inform listeners
//			if (listener != null){
//				listener.onData(SensorType.HEART_RATE, event.timestamp,
//						Float.toString(event.values[0])
//				);
//			}
//
//		}
		else if(event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// inform listeners
			listener.onData(SensorType.LIGHT, event.timestamp,
					Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
			// inform listeners
			listener.onData(SensorType.AMBIENT_TEMPERATURE, event.timestamp,
					Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
			// inform listeners
			listener.onData(SensorType.RELATIVE_HUMIDITY, event.timestamp,
					Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			// inform listeners
			if(event.values.length > 3){
				listener.onData(SensorType.ROTATION_VECTOR, event.timestamp,
						Float.toString(event.values[0]) + ";" +
						Float.toString(event.values[1]) + ";" +
						Float.toString(event.values[2]) + ";" +
						Float.toString(event.values[3])
				);
			} else {
				listener.onData(SensorType.ROTATION_VECTOR, event.timestamp,
						Float.toString(event.values[0]) + ";" +
						Float.toString(event.values[1]) + ";" +
						Float.toString(event.values[2])
				);
			}
		} else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
			// inform listeners
			listener.onData(SensorType.GYROSCOPE, event.timestamp,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_PRESSURE) {
			// inform listeners
			listener.onData(SensorType.PRESSURE, event.timestamp,
				Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
			// inform listeners
			listener.onData(SensorType.LINEAR_ACCELERATION, event.timestamp,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_GRAVITY) {
			// inform listeners
			listener.onData(SensorType.GRAVITY, event.timestamp,
					Float.toString(event.values[0]) + ";" +
					Float.toString(event.values[1]) + ";" +
					Float.toString(event.values[2])
			);
        } else if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			// inform listeners
			listener.onData(SensorType.ACCELEROMETER, event.timestamp,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
			// keep a local copy (needed for orientation matrix)
			System.arraycopy(event.values, 0, mGravity, 0, 3);

			// NOTE:
			// @see TYPE_MAGNETIC_FIELD
			//updateOrientation();
		} else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			// inform listeners
			listener.onData(SensorType.MAGNETIC_FIELD, event.timestamp,
					Float.toString(event.values[0]) + ";" +
					Float.toString(event.values[1]) + ";" +
					Float.toString(event.values[2])
			);
			// keep a local copy (needed for orientation matrix)
			System.arraycopy(event.values, 0, mGeomagnetic, 0, 3);

			// NOTE
			// @see TYPE_ACCELEROMETER
			// only MAG updates the current orientation as MAG is usually slower than ACC and this reduces the file-footprint
			updateOrientation(event.timestamp);
        } else if(event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
        	// inform listeners
			listener.onData(SensorType.GAME_ROTATION_VECTOR, event.timestamp,
					Float.toString(event.values[0]) + ";" +
							Float.toString(event.values[1]) + ";" +
							Float.toString(event.values[2])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
        	//inform listeners
			listener.onData(SensorType.STEP_DETECTOR, event.timestamp, "1.0");
		}
    }

	/** calculate orientation from acc and mag */
	private void updateOrientation(long timestamp) {

		// skip orientation update if either grav or geo is missing
		if (mGravity == null) {return;}
		if (mGeomagnetic == null) {return;}

		// calculate rotationMatrix and orientation
		// see: https://developer.android.com/reference/android/hardware/SensorManager#getRotationMatrix(float[],%20float[],%20float[],%20float[])
		// these are row-major
		float R[] = new float[9];
		float I[] = new float[9];

		// derive rotation matrix from grav and geo sensors
		boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
		if (!success) {return;}

		// derive orientation-vector using the rotation matrix
		float orientationNew[] = new float[3];
		SensorManager.getOrientation(R, orientationNew);

		// inform listeners
		if (listener != null) {

			// orientation vector
			listener.onData(SensorType.ORIENTATION_NEW, timestamp,
				Float.toString(orientationNew[0]) + ";" +
				Float.toString(orientationNew[1]) + ";" +
				Float.toString(orientationNew[2])
			);

			// rotation matrix
			final StringBuilder sb = new StringBuilder(1024);
			sb.append(R[0]).append(';');
			sb.append(R[1]).append(';');
			sb.append(R[2]).append(';');
			sb.append(R[3]).append(';');
			sb.append(R[4]).append(';');
			sb.append(R[5]).append(';');
			sb.append(R[6]).append(';');
			sb.append(R[7]).append(';');
			sb.append(R[8]);

			//Write the whole rotationMatrix R into the Listener.
			listener.onData(SensorType.ROTATION_MATRIX, timestamp, sb.toString());

//				Float.toString(R[0]) + ";" +
//				Float.toString(R[1]) + ";" +
//				Float.toString(R[2]) + ";" +
//				Float.toString(R[3]) + ";" +
//				Float.toString(R[4]) + ";" +
//				Float.toString(R[5]) + ";" +
//				Float.toString(R[6]) + ";" +
//				Float.toString(R[7]) + ";" +
//				Float.toString(R[8])
//				Float.toString(R[9]) + ";" +
//				Float.toString(R[10]) + ";" +
//				Float.toString(R[11]) + ";" +
//				Float.toString(R[12]) + ";" +
//				Float.toString(R[13]) + ";" +
//				Float.toString(R[14]) + ";" +
//				Float.toString(R[15])
//			);

		}

	}

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// nothing to-do here
    }

    @Override
    public void onResume(final Activity act) {
		// attach as listener to each of the available sensors
        registerIfPresent(acc, SensorManager.SENSOR_DELAY_FASTEST);
      	registerIfPresent(grav, SensorManager.SENSOR_DELAY_FASTEST);
       	registerIfPresent(gyro, SensorManager.SENSOR_DELAY_FASTEST);
        registerIfPresent(lin_acc, SensorManager.SENSOR_DELAY_FASTEST);
        registerIfPresent(magnet, SensorManager.SENSOR_DELAY_FASTEST);
        registerIfPresent(press, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(ori, SensorManager.SENSOR_DELAY_FASTEST);
		//registerIfPresent(heart, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(humidity, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(rotationVector, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(light, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(temperature, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(gameRotationVector, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(stepDetector, SensorManager.SENSOR_DELAY_FASTEST);
    }

	private void registerIfPresent(final Sensor sens, final int delay) {
		if (sens != null) {
			sensorManager.registerListener(this, sens, delay);
			Log.d("PhoneSensors", "added sensor " + sens.toString());
		} else {
			Log.d("PhoneSensors", "sensor not present. skipping");
		}
	}

    @Override
    public void onPause(final Activity act) {
		// detach from all events
		sensorManager.unregisterListener(this);
    }

}
