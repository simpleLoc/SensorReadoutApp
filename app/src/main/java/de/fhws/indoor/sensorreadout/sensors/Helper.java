package de.fhws.indoor.sensorreadout.sensors;

/**
 * @author Frank Ebner
 */
public class Helper {

    /** remove all ":" within the MAC to reduce file footprint */
    public static final String stripMAC(final String mac) {
        return mac.replaceAll(":", "");
    }

}



