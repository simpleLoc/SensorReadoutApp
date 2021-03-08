package de.fhws.indoor.sensorreadout;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import java.util.HashSet;
import java.util.Set;

import de.fhws.indoor.sensorreadout.sensors.WiFiRTT;

/**
 * @author Markus Ebner
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private Preference prefActiveSensors = null;
    private Preference prefUseWifiFTM = null;
    private Preference prefDecawaveUWBTagMacAddress = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        prefActiveSensors = findPreference("prefActiveSensors");
        prefUseWifiFTM = findPreference("prefUseWifiFTM");
        prefDecawaveUWBTagMacAddress = findPreference("prefDecawaveUWBTagMacAddress");

        if(!WiFiRTT.isSupported(getContext())) {
            prefUseWifiFTM.setEnabled(false);
        }
        prefActiveSensors.setOnPreferenceChangeListener((preference, newValue) -> {
            Set<String> activeSensors = (Set<String>)newValue;
            prefDecawaveUWBTagMacAddress.setEnabled(activeSensors.contains("DECAWAVE_UWB"));
            return true;
        });

        initUi();
    }


    private void initUi() {
        Set<String> activeSensors = getPreferenceManager().getSharedPreferences()
                .getStringSet(prefActiveSensors.getKey(), new HashSet<String>());
        prefDecawaveUWBTagMacAddress.setEnabled(activeSensors.contains("DECAWAVE_UWB"));
    }

}
