package de.fhws.indoor.sensorreadout.sensors;

/**
 * Enum mapping all supported events in the logfile to their corresponding ids.
 *
 * @author Toni Fetzer
 * @author Frank Ebner
 */
public enum SensorType {

    ACCELEROMETER(0),
    GRAVITY(1),
    LINEAR_ACCELERATION(2),
    GYROSCOPE(3),
    MAGNETIC_FIELD(4),
    PRESSURE(5),
    ORIENTATION_NEW(6),
    ROTATION_MATRIX(7),
    WIFI(8),
    IBEACON(9),
    RELATIVE_HUMIDITY(10),
    ORIENTATION_OLD(11),
    ROTATION_VECTOR(12),
    LIGHT(13),
    AMBIENT_TEMPERATURE(14),
    HEART_RATE(15),
    GPS(16),
    WIFIRTT(17),
    GAME_ROTATION_VECTOR(18),
    EDDYSTONE_UID(19),

    PEDESTRIAN_ACTIVITY(50),
    GROUND_TRUTH(99),
    GROUND_TRUTH_PATH(-1),
    FILE_METADATA(-2)

    ;

    private int id;

    SensorType(final int id) {
        this.id = id;
    }

    public final int id() {return id;}

}
