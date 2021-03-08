package de.fhws.indoor.sensorreadout.sensors;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.BleManagerCallbacks;
import no.nordicsemi.android.ble.callback.profile.ProfileDataCallback;
import no.nordicsemi.android.ble.data.Data;

/**
 * Decawave UWB DWM1000 module readout via BLE.
 * @author Markus Bullmann
 */
public class DecawaveUWB extends mySensor {

    private static class BleDataStream {
        private final Data _data;
        private int _pos;

        public BleDataStream(Data data) {
            _data = data;
            _pos = 0;
        }

        public int position() {
            return _pos;
        }

        public int size() {
            return _data.size();
        }

        public boolean eof() {
            return _pos >= _data.size();
        }

        public byte readByte() {
            return _data.getByte(_pos++);
        }

        private int readInteger(final int formatType) {
            int result = _data.getIntValue(formatType, _pos);

            switch (formatType) {
                case Data.FORMAT_SINT8:
                case Data.FORMAT_UINT8:
                    _pos++;
                    break;
                case Data.FORMAT_SINT16:
                case Data.FORMAT_UINT16:
                    _pos += 2;
                    break;
                case Data.FORMAT_SINT24:
                case Data.FORMAT_UINT24:
                    _pos += 3;
                    break;
                case Data.FORMAT_SINT32:
                case Data.FORMAT_UINT32:
                    _pos += 4;
                    break;
            }

            return result;
        }

        public int readUInt16() {
            return readInteger(Data.FORMAT_UINT16);
        }

        public int readUInt32() {
            return readInteger(Data.FORMAT_UINT32);
        }

        public float readFloat() {
            float result = _data.getFloatValue(Data.FORMAT_FLOAT, _pos);
            _pos += 4;
            return result;
        }
    }

    private class DecawaveManager extends BleManager {
        private static final String TAG = "DecaManager";

        /** Decawave Service UUID. */
        public final  UUID LBS_UUID_SERVICE = UUID.fromString("680c21d9-c946-4c1f-9c11-baa1c21329e7");
        /** Location data  */
        public final UUID LBS_UUID_LOCATION_DATA_CHAR = UUID.fromString("003bbdf2-c634-4b3d-ab56-7ec889b89a37");
        /** Location data mode */
        public final UUID LBS_UUID_LOCATION_MODE_CHAR = UUID.fromString("a02b947e-df97-4516-996a-1882521e0ead");

        private BluetoothGattCharacteristic _locationDataCharacteristic;
        private BluetoothGattCharacteristic _locationModeCharacteristic;

        public DecawaveManager(@NonNull final Context context)
        {
            super(context);
        }

        @Override
        public void log(final int priority, @NonNull final String message) {
            // Log.println(priority, TAG, message);
        }

        public void connectToDevice(BluetoothDevice device)
        {
            connect(device)
                    .retry(3, 100)
                    .useAutoConnect(false)
                    .enqueue();
        }

        @NonNull
        @Override
        protected BleManagerGattCallback getGattCallback() {
            return new BleManagerGattCallback() {
                @Override
                protected void initialize() {

                    setNotificationCallback(_locationDataCharacteristic).with(mLocationDataCallback);

                    beginAtomicRequestQueue()
                            .add(requestMtu(512) // Remember, GATT needs 3 bytes extra. This will allow packet size of 244 bytes.
                                    .with((device, mtu) -> log(Log.INFO, "MTU set to " + mtu))
                                    .fail((device, status) -> log(Log.WARN, "Requested MTU not supported: " + status)))
                            .add(writeCharacteristic(_locationModeCharacteristic, new byte[] {2}))
                            .add(enableNotifications(_locationDataCharacteristic))
                            .done(device -> log(Log.INFO, "Target initialized"))
                            .enqueue();
                }

                @Override
                public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
                    final BluetoothGattService service = gatt.getService(LBS_UUID_SERVICE);
                    if (service != null) {
                        _locationDataCharacteristic = service.getCharacteristic(LBS_UUID_LOCATION_DATA_CHAR);
                        _locationModeCharacteristic = service.getCharacteristic(LBS_UUID_LOCATION_MODE_CHAR);
                    }

                    return _locationDataCharacteristic != null && _locationModeCharacteristic != null;
                }

                @Override
                protected void onDeviceDisconnected() {
                    _locationDataCharacteristic = null;
                    _locationModeCharacteristic = null;
                }
            };
        }

        private	final ProfileDataCallback mLocationDataCallback = new ProfileDataCallback() {
            @Override
            public void onDataReceived(@NonNull BluetoothDevice device, @NonNull Data data) {
                // Log.d(TAG, "onDataReceived: length=" + data.size() + " data=" + data);

                if (data.size() == 0) {
                    return;
                }

                final long timestamp = SystemClock.elapsedRealtimeNanos();

                BleDataStream stream = new BleDataStream(data);

                // 0: Position only
                // 1: Distance
                // 2: Position and Distance
                int mode = stream.readByte();
                final boolean readPos = mode == 0 || mode == 2;
                final boolean readDist = mode == 1 || mode == 2;

                if (!readPos) {
                    return;
                }

                StringBuilder csv = new StringBuilder(); // X;Y;Z;QualiFactor;[NodeID;DistInMM;QualiFactor]

                if (readPos) {
                    // X,Y,Z coordinates (each 4 bytes) and quality factor in percent (0-100) (1 byte), total size: 13 bytes

                    int x = stream.readUInt32();
                    int y = stream.readUInt32();
                    int z = stream.readUInt32();
                    byte quality = stream.readByte();

                    csv.append(x).append(';');
                    csv.append(y).append(';');
                    csv.append(z).append(';');
                    csv.append(quality).append(';');
                }

                if (readDist) {
                    // First byte is distance count(1 byte)
                    // Sequence of node ID(2 bytes), distance in mm(4 bytes) and quality factor(1 byte)
                    // Max value contains 15 elements, size: 8 - 106
                    byte numOfAnchors = stream.readByte();

                    for (int i = 0; i < numOfAnchors; i++) {

                        int nodeID = stream.readUInt16();
                        int distInMM = stream.readUInt32();
                        byte quality = stream.readByte();

                        csv.append(nodeID).append(';');
                        csv.append(distInMM).append(';');
                        csv.append(quality).append(';');
                    }
                }

                csv.deleteCharAt(csv.length()-1); // remove last ';'

                boolean everything = stream.eof();  // debug helper

                listener.onData(SensorType.DECAWAVE_UWB, timestamp, csv.toString());
            }

            @Override
            public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
                                              @NonNull final Data data) {
                Log.w(TAG, "Invalid data received: " + data);
            }
        };
    }

    private final BluetoothAdapter bluetoothAdapter;
    private final DecawaveManager decaManager;

    public DecawaveUWB(final Activity act)
    {
        BluetoothManager bluetoothManager = (BluetoothManager) act.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            act.startActivityForResult(enableBtIntent, 42);
        }

        decaManager = new DecawaveManager(act);
    }

    @Override
    public void onResume(Activity act) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(act);
        String deviceMAC = preferences.getString("prefDecawaveUWBTagMacAddress", "");

        if (bluetoothAdapter != null) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceMAC);

            decaManager.connectToDevice(device);
        }
    }

    @Override
    public void onPause(Activity act) {
        decaManager.disconnect().enqueue();
    }
}
