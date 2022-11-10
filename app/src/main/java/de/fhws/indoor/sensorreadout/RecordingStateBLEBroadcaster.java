package de.fhws.indoor.sensorreadout;

import android.bluetooth.le.AdvertisingSetParameters;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.util.UUID;

import de.fhws.indoor.libsmartphonesensors.io.RecordingSession;
import de.fhws.indoor.libsmartphonesensors.util.ble.OneTimeBeaconSender;

@RequiresApi(api = Build.VERSION_CODES.O)
public class RecordingStateBLEBroadcaster {
    private OneTimeBeaconSender beaconSender;

    private static final byte EVENTCODE_START = 0;
    private static final byte EVENTCODE_END = 1;

    public RecordingStateBLEBroadcaster(AppCompatActivity activity) {
        beaconSender = new OneTimeBeaconSender(activity);
    }

    public void startRecording(RecordingSession recordingSession) {
        UUID recordingId = recordingSession.getRecordingId();
        ByteBuffer byteBuffer = ByteBuffer.allocate(17);
        byteBuffer.put(EVENTCODE_START);
        byteBuffer.putLong(recordingId.getMostSignificantBits());
        byteBuffer.putLong(recordingId.getLeastSignificantBits());
        beaconSender.send(AdvertisingSetParameters.TX_POWER_HIGH, byteBuffer.array(), 300);
    }

    public void stopRecording(RecordingSession recordingSession) {
        UUID recordingId = recordingSession.getRecordingId();
        ByteBuffer byteBuffer = ByteBuffer.allocate(17);
        byteBuffer.put(EVENTCODE_END);
        byteBuffer.putLong(recordingId.getMostSignificantBits());
        byteBuffer.putLong(recordingId.getLeastSignificantBits());
        beaconSender.send(AdvertisingSetParameters.TX_POWER_HIGH, byteBuffer.array(), 300);
    }

}
