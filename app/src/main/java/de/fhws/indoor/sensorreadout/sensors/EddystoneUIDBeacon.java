package de.fhws.indoor.sensorreadout.sensors;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bluetooth Eddystone sensor.
 * @author Markus Ebner
 */
public class EddystoneUIDBeacon extends mySensor {

    private static final ParcelUuid EDDYSTONE_UUID = ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb");

    private Activity activity = null;
    private boolean bluetoothRunning = false;
    private BluetoothAdapter bleAdapter = null;
    private BluetoothLeScanner bleScanner = null;
    private ScanCallback bleScanCallback;
    private List<ScanFilter> bleFilters;
    private ScanSettings bleScanSettings;

    private static class HexConverter {
        private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        public static String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }
    }

    // ctor
    public EddystoneUIDBeacon(final Activity act) {
        this.activity = act;
        initializeBluetooth();
    }

    private boolean initializeBluetooth() {
        if(bluetoothRunning) { return true; }
        if(bleFilters == null) {
            bleFilters = new ArrayList<>();
            // Filter for Eddystone beacons
            bleFilters.add(new ScanFilter.Builder().setServiceUuid(EDDYSTONE_UUID).build());
        }
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }
        final BluetoothManager mgr = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = mgr.getAdapter();
        if(bleAdapter == null) {
            return false;
        }
        return true;
    }

    private void startBluetooth() {
        if(bluetoothRunning) { return; }
        bleScanner = bleAdapter.getBluetoothLeScanner();
        if(bleScanner == null) { return; }
        bleScanSettings = new ScanSettings.Builder()
                .setReportDelay(0)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        bleScanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, android.bluetooth.le.ScanResult scanResult) {
                byte[] payload = scanResult.getScanRecord().getBytes();
                if(payload.length > 9) {
                    // Check whether the Eddystone-Frame we got is a EddystoneUID frame (instead of: TLM, EID, URL, ...)
                    // see: https://github.com/google/eddystone/blob/master/protocol-specification.md
                    int i = 8;
                    for(; i < payload.length; ++i) {
                        if(payload[i-8] == (byte)0x03 && payload[i-7] == (byte)0x03 && payload[i-6] == (byte)0xAA && payload[i-5] == (byte)0xFE
                                && payload[i-3] == (byte)0x16 && payload[i-2] == (byte)0xAA && payload[i-1] == (byte)0xFE) {
                            // found eddystone frame header
                            if(payload[i] == (byte)0x00) { // EddystoneUID
                                handleBluetoothAdvertisement(scanResult, i);
                            }
                        }
                    }
                }
            }
        };
        bleScanner.startScan(bleFilters, bleScanSettings, bleScanCallback);
        bluetoothRunning = true;
    }
    void stopBluetooth() {
        if(!bluetoothRunning) { return; }
        bleScanner.stopScan(bleScanCallback);
        bluetoothRunning = false;
    }

    private void handleBluetoothAdvertisement(ScanResult advertisement, int uidFrameOffset) {
        byte[] payload = advertisement.getScanRecord().getBytes();
        if(payload.length - uidFrameOffset < 31) {
            return; // not a valid Eddystone UID frame
        }
        byte[] uuidBytes = new byte[16];
        System.arraycopy(payload, uidFrameOffset + 2, uuidBytes, 0, 16);
        ByteBuffer bb = ByteBuffer.wrap(uuidBytes);
        long uuidHigh = bb.getLong();
        long uuidLow = bb.getLong();
        UUID uuid = new UUID(uuidHigh, uuidLow);

        // For EddystoneUID layout, see: https://github.com/google/eddystone/tree/master/eddystone-uid
        if(listener != null) {
            listener.onData(advertisement.getTimestampNanos(), Helper.stripMAC(advertisement.getDevice().getAddress()) + ";"
                    + advertisement.getRssi() + ";"
                    + advertisement.getScanRecord().getTxPowerLevel() + ";"
                    + uuid.toString());
        }
    }

    @Override public void onResume(final Activity act) {
        startBluetooth();
    }

    @Override public void onPause(final Activity act) {
        stopBluetooth();
    }

}
