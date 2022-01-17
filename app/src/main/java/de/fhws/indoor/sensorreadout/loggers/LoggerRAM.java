package de.fhws.indoor.sensorreadout.loggers;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;

import de.fhws.indoor.sensorreadout.MyException;

/**
 * Simple (ordered) RAM Logger.
 * <p>
 *     This logger stores all events in memory first.
 *     Only when the logger is stopped does it sort them and flush them to the logfile.
 *
 *     WARNING: This overflows the RAM if the recording gets too long.
 *     If this happens, all the data is gone.
 * </p>
 * @author Frank Ebner
 */
public final class LoggerRAM extends Logger {

    private File file;
    private FileOutputStream fos;

    private ArrayList<LogEntry> buffer = new ArrayList<>();

    public LoggerRAM(Context context) {
        super(context);
    }

    @Override
    protected void onStart() {
        buffer.clear();
        // open the output-file immeditaly (to get permission errors)
        // but do NOT yet write anything to the file
        final DataFolder folder = new DataFolder(context, "sensorOutFiles");
        file = new File(folder.getFolder(), startTs + ".csv");

        try {
            fos = new FileOutputStream(file);
            Log.d("logger", "will write to: " + file.toString());
        } catch (final Exception e) {
            throw new MyException("error while opening log-file", e);
        }
    }

    @Override
    protected void onStop() {
        synchronized (buffer) {
            // sort by TS (ensure strict ordering)
            Collections.sort(buffer);

            // export each entry
            for (LogEntry e : buffer) {
                try {
                    fos.write(e.csv.getBytes());
                } catch (final Exception ex) {
                    ex.printStackTrace();;
                }
            }
            // done
            buffer.clear();
        }

        try {
            fos.close();
        } catch (final Exception e) {
            throw new MyException("error while writing log-file", e);
        }
    }

    @Override
    protected void log(LogEntry logEntry) {
        synchronized (buffer) {
            buffer.add(logEntry);
        }
    }

    @Override
    public long getEntriesCached() {
        return 0;
    }

    @Override
    public float getCacheLevel() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void shareLast(Activity activity) {
        //TODO: implement
    }
}
