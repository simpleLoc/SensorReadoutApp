package de.fhws.indoor.sensorreadout.sensors;

/**
 * Enum mapping all supported Activities to their ids.
 * @author Toni Fetzer
 */
public enum PedestrianActivity {

    WALK(0),
    STANDING(1),
    STAIRS_UP(2),
    STAIRS_DOWN(3),
    ELEVATOR_UP(4),
    ELEVATOR_DOWN(5),
    MESS_AROUND(6),
    ;

    private int id;

    PedestrianActivity(final int id) {
        this.id = id;
    }

    public final int id() {return id;}
}
