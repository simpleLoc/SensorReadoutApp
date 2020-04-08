package de.fhws.indoor.sensorreadout;

import android.widget.Toast;

import de.fhws.indoor.sensorreadout.MainActivity;
import de.fhws.indoor.sensorreadout.R;

/**
 * @author Frank Ebner
 */
public class MyException extends RuntimeException {

    public MyException(final String err, final Throwable t) {
        super(err, t);
        Toast.makeText(MainActivity.getAppContext(), err, Toast.LENGTH_LONG);
    }

    public MyException(final String err) {
        super(err);
        Toast.makeText(MainActivity.getAppContext(), err, Toast.LENGTH_LONG);
    }

}
