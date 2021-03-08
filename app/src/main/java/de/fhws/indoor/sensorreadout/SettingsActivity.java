package de.fhws.indoor.sensorreadout;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

/**
 * @author Markus Ebner
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
}
