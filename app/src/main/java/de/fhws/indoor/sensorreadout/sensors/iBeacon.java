package de.fhws.indoor.sensorreadout.sensors;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Bluetooth iBeacon sensor.
 * @author Frank Ebner
 */
public class iBeacon extends mySensor {

	private BluetoothAdapter bt;
	private BluetoothLeScanner scanner;
	private static ScanSettings settings;
	private static final int REQUEST_ENABLE_BT = 1;
	private ScanCallback mLeScanCallback;

	// ctor
	public iBeacon(final Activity act) {

		// sanity check
		if (!act.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(act, "Bluetooth-LE not supported!", Toast.LENGTH_SHORT).show();
			return;
		}

		// Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager mgr = (BluetoothManager) act.getSystemService(Context.BLUETOOTH_SERVICE);
		bt = mgr.getAdapter();

		// bluetooth supported?
		if (bt == null || !bt.isEnabled()) {

			//TODO: add something that asks the user to enable BLE. this need also be called in onResum()
			Toast.makeText(act, "Bluetooth not supported!", Toast.LENGTH_SHORT).show();
			return;
		}

		// create the scanner
		scanner = bt.getBluetoothLeScanner();

		// and attach the callback
		mLeScanCallback = new ScanCallback() {
			@Override public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
				//Log.d("BT", device + " " + rssi);
				if (listener != null) {
					listener.onData(result.getTimestampNanos(), Helper.stripMAC(result.getDevice().getAddress()) + ";" + result.getRssi() + ";" + result.getScanRecord().getTxPowerLevel());
				}
			}
		};

		settings = new ScanSettings.Builder()
				.setReportDelay(0)
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				//.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) //comment this out for apk < 23
				.build();

	}

	private void enableBT(final Activity act) {
		if (bt == null) {throw new RuntimeException("BT not supported!");}
		if (!bt.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			act.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}

	@Override public void onResume(final Activity act) {
		if (bt != null) {
			enableBT(act);
			List<ScanFilter> filters = new ArrayList<ScanFilter>();
			scanner.startScan(filters, settings, mLeScanCallback);
			//scanner.startScan(mLeScanCallback);
		}
	}

	@Override public void onPause(final Activity act) {
		if (bt != null) {
			scanner.stopScan(mLeScanCallback);
		}
	}

}
