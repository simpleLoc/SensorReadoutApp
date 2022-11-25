package de.fhws.indoor.sensorreadout;

import android.net.wifi.rtt.RangingRequest;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

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
    private Preference prefFtmBurstSize = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        prefActiveSensors = findPreference("prefActiveSensors");
        prefUseWifiFTM = findPreference("prefUseWifiFTM");
        prefDecawaveUWBTagMacAddress = findPreference("prefDecawaveUWBTagMacAddress");

        prefFtmBurstSize = findPreference("prefFtmBurstSize");
        assert prefFtmBurstSize != null;
        prefFtmBurstSize.setEnabled(prefActiveSensors.getPersistedStringSet(new HashSet<>()).contains("WIFIRTTSCAN"));

        prefFtmBurstSize.setOnPreferenceChangeListener((preference, newValue) -> {
            int burstSize = Integer.parseInt((String)newValue);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // accept 0 for default burst size, reject other invalid sizes
                if (burstSize == 0) {
                    return true;
                } else if (burstSize < RangingRequest.getMinRttBurstSize()) {
                    Toast.makeText(requireContext(), "Burst size too small!", Toast.LENGTH_LONG).show();
                    return false;
                } else if(burstSize > RangingRequest.getMaxRttBurstSize()) {
                    Toast.makeText(requireContext(), "Burst size too big!", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            return true;
        });

        prefActiveSensors.setOnPreferenceChangeListener((preference, newValue) -> {
            Set<String> activeSensors = (Set<String>)newValue;
            prefDecawaveUWBTagMacAddress.setEnabled(activeSensors.contains("DECAWAVE_UWB"));
            prefFtmBurstSize.setEnabled(activeSensors.contains("WIFIRTTSCAN"));
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
