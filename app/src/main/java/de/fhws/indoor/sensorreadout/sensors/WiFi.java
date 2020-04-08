package de.fhws.indoor.sensorreadout.sensors;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import de.fhws.indoor.sensorreadout.MyException;


/**
 * Wifi sensor exporting scan/advertisement events.
 * @author Frank Ebner
 */
public class WiFi extends mySensor {

	private final Activity act;
	private final WifiManager wifi;
	private BroadcastReceiver receiver;
    private boolean isReceiverRegistered;
	private boolean isFirstMeasurement = true;

	public WiFi(final Activity act) {

		this.act = act;
		this.wifi = (WifiManager) act.getSystemService(Context.WIFI_SERVICE);
        isReceiverRegistered = true;

		//this.wifi.setWifiEnabled(false);
		//this.wifi.setWifiEnabled(true);

		if (wifi == null) {
			throw new MyException("WIFI not supported!");
		}
		if (wifi.isWifiEnabled()) {
			this.receiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {

					// ignore the first measurement
					if (isFirstMeasurement) {
						isFirstMeasurement = false;
					} else {
						final StringBuilder sb = new StringBuilder(1024);
						final List<ScanResult> res = wifi.getScanResults();
						long timestamp = 0;
						for (final ScanResult sr : res) {
							sb.append(Helper.stripMAC(sr.BSSID)).append(';');
							sb.append(sr.frequency).append(';');
							sb.append(sr.level).append(';');

							// export with oldest timestamp among all contained measurements
							final long nanos = sr.timestamp * 1000;
							if (nanos > timestamp) {
								timestamp = nanos;
							}

						}
						if (listener != null && isReceiverRegistered) {
							listener.onData(timestamp, sb.toString());
						}
					}
					startScan();
					//Log.d("wifi", sb.toString());
				}
			};
		}
		else {
			throw new MyException("WIFI not supported!");
		}
	}



	/** exception-safe scanning start */
	private void startScan() {
		try {
			//Method startScanActiveMethod = wifi.getClass().getMethod("startScanActive");
			//startScanActiveMethod.invoke(wifi);
			if(!wifi.startScan()){
				throw new MyException("Cant start WiFi!");
			}
			//Log.d("wifi", "start scan");
		}catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onResume(Activity act) {
		act.registerReceiver(this.receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        isReceiverRegistered = true;
		wifi.createWifiLock(wifi.WIFI_MODE_SCAN_ONLY, "ipin");
		//wifi.disconnect();


		//this is a very nice hack. do not try this at home.
		Method m = null;
		try {
			m = this.wifi.getClass().getDeclaredMethod("setFrequencyBand", int.class, boolean.class);
			m.setAccessible(true);
			m.invoke(this.wifi, 2, true);
			m.invoke(this.wifi, 2, true);
			m.invoke(this.wifi, 2, true);
			Log.d("ok", "ok");
		} catch (Exception e) {
			e.printStackTrace();
		}

		startScan();
	}

	@Override
	public void onPause(Activity act) {
        if (isReceiverRegistered) {
            try {
                act.unregisterReceiver(this.receiver);
            } catch (final Exception  e) {
                e.printStackTrace();
            }
            isReceiverRegistered = false;
        }
    }
}
