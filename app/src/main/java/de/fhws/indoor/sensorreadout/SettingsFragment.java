package de.fhws.indoor.sensorreadout;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.HashSet;
import java.util.Set;

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
