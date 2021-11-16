package de.fhws.indoor.sensorreadout.sensors;

import android.app.Activity;
import android.net.wifi.ScanResult;

import java.util.List;
import de.fhws.indoor.sensorreadout.helpers.WifiScanProvider;


/**
 * Wifi sensor exporting scan/advertisement events.
 * @author Markus Ebner
 */
public class WiFi extends mySensor implements WifiScanProvider.WifiScanCallback {

	private final WifiScanProvider wifiScanProvider;

	public WiFi(WifiScanProvider wifiScanProvider) {
		this.wifiScanProvider = wifiScanProvider;
	}

	@Override
	public void onResume(Activity act) {
		wifiScanProvider.registerCallback(this);
	}

	@Override
	public void onPause(Activity act) {
		wifiScanProvider.unregisterCallback(this);
    }

	@Override
	public void onScanResult(List<ScanResult> scanResults) {
		final StringBuilder sb = new StringBuilder(1024);
		for(final ScanResult sr : scanResults) {
			sb.append(Helper.stripMAC(sr.BSSID)).append(';');
			sb.append(sr.frequency).append(';');
			sb.append(sr.level);
			listener.onData(sr.timestamp * 1000, sb.toString());
			sb.setLength(0);
		}
	}
}
