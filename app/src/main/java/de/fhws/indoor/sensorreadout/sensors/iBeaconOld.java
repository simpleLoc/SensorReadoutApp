package de.fhws.indoor.sensorreadout.sensors;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Created by toni on 02/06/16.
 */
/*
public class iBeaconOld extends mySensor {

    private BluetoothAdapter bt = null;
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    // ctor
    public iBeaconOld(final Activity act) {

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
        if (bt == null) {
            Toast.makeText(act, "Bluetooth not supported!", Toast.LENGTH_SHORT).show();
            return;
        }

        // attach scan callback
        mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                //Log.d("BT", device + " " + rssi);
                if (listener != null) {
                    listener.onData(SystemClock.elapsedRealtimeNanos(), Helper.stripMAC(device.getAddress()) + ";" + rssi);
                }
            }
        };

    }

    void enableBT(final Activity act) {
        if (!bt.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            act.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override public void onResume(final Activity act) {
        enableBT(act);
        bt.startLeScan(mLeScanCallback);
    }

    @Override public void onPause(final Activity act) {
        bt.stopLeScan(mLeScanCallback);
    }

}
*/
