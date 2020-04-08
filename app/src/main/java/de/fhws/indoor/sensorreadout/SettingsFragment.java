package de.fhws.indoor.sensorreadout;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import de.fhws.indoor.sensorreadout.sensors.WiFiRTT;

/**
 * @author Markus Ebner
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        if(!WiFiRTT.isSupported(getContext())) {
            SwitchPreferenceCompat useFtmPreference = (SwitchPreferenceCompat)findPreference("prefUseWifiFTM");
            useFtmPreference.setEnabled(false);
        }
    }

}
