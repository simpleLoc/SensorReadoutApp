package android.net.wifi;

import android.net.MacAddress;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class ScanResultHack {

    public static ScanResult createScanResultFromMacAddress(MacAddress mac) {

        // only works with targetSDK < 28
        ScanResult result = new ScanResult();
        result.BSSID = mac.toString();
        result.SSID = "tof_test";
        result.frequency = 2447;
        result.capabilities = "[ESS]";

        try {

            Class<?> clazz = Class.forName("android.net.wifi.ScanResult$InformationElement");
            Object[] ies = (Object[]) Array.newInstance(clazz, 1);

            Object ie = clazz.getDeclaredConstructor().newInstance();
            ies[0] = ie;

            Field f1 = result.getClass().getDeclaredField("flags");
            f1.setAccessible(true);
            f1.set(result, 2);

            Field f2 = result.getClass().getDeclaredField("informationElements");
            f2.setAccessible(true);
            f2.set(result, ies);

        } catch (final Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
