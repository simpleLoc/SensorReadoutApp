package de.fhws.indoor.sensorreadout.loggers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.google.android.gms.tagmanager.Container;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import de.fhws.indoor.sensorreadout.MyException;

/**
 * Live (ordered) logger.
 * <p>
 *     This logger contains an internal caching structure that regularly sorts the contained entries
 *     and commits the oldest ones to the logfile using the correct order.
 *     This buffer determines the amount of items to store based on the time-window they represent,
 *     instead of a hard-coded amount of entries.
 * </p>
 * @author Markus Ebner
 */
public final class TimedOrderedLogger extends Logger {

    // If the entries in the ReorderBuffer represent at least a timespan of REORDER_TIMEFRAME_UPPER_NS,
    // a commit is scheduled, that sorts the buffer and commits all items older than REORDER_TIMEFRAME_LOWER_NS.
    private static final long REORDER_TIMEFRAME_UPPER_NS = 10L * 1000 * 1000 * 1000;
    private static final long REORDER_TIMEFRAME_LOWER_NS = 7L * 1000 * 1000 * 1000;

    // members
    private File file;
    private BufferedOutputStream fos;
    private ReorderBuffer reorderBuffer;
    //private long oldestEntryTimestamp = 0;

    public TimedOrderedLogger(Context context) {
        super(context);
    }

    @Override
    protected final void onStart() {
        reorderBuffer = new ReorderBuffer((commitSlice) -> {
            for(LogEntry entry : commitSlice) {
//                if(oldestEntryTimestamp > entry.timestamp) {
//                    throw new MyException("Order Issue!");
//                } else {
//                    oldestEntryTimestamp = entry.timestamp;
//                }
                try {
                    fos.write(entry.csv.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // open the output-file immediately (to get permission errors)
        // but do NOT yet write anything to the file
        final DataFolder folder = new DataFolder(context, "sensorOutFiles");
        file = new File(folder.getFolder(), startTs + ".csv");

        try {
            fos = new BufferedOutputStream(new FileOutputStream(file));
            Log.d("logger", "will write to: " + file.toString());
        } catch (final Exception e) {
            throw new MyException("error while opening log-file", e);
        }
        // oldestEntryTimestamp = 0;
    }

    @Override
    protected void onStop() {
        synchronized (reorderBuffer) {
            reorderBuffer.flush();
        }
        try {
            fos.flush();
            fos.close();
        } catch (final Exception e) {
            throw new MyException("error while writing log-file", e);
        }
    }

    @Override
    protected void log(LogEntry logEntry) {
        synchronized (reorderBuffer) {
            reorderBuffer.add(logEntry);
        }
    }

    @Override
    public long getEntriesCached() {
        synchronized (reorderBuffer) {
            return reorderBuffer.size();
        }
    }

    @Override
    public float getCacheLevel() {
        synchronized (reorderBuffer) {
            return ((float)reorderBuffer.timespan() / (float)REORDER_TIMEFRAME_UPPER_NS);
        }
    }

    @Override
    public String getName() {
        if(file != null) {
            return file.getName();
        }
        return "OrderedLogger";
    }

    @Override
    public void shareLast(Activity activity) {
        Uri path = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, file);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_TEXT, "Share Recording");
        i.putExtra(Intent.EXTRA_STREAM, path);
        i.setType("text/csv");
        List<ResolveInfo> resInfoList = activity.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            activity.grantUriPermission(packageName, path, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        activity.startActivity(Intent.createChooser(i, "Share Recording"));
    }

    private interface ReorderBufferCommitListener {
        void onCommit(List<LogEntry> commitSlice);
    }

    private static class ReorderBuffer {
        private long oldestTs = Long.MAX_VALUE;
        private long newestTs = Long.MIN_VALUE;
        private ArrayList<LogEntry> reorderBuffer = new ArrayList<>();
        private ReorderBufferCommitListener listener;

        public ReorderBuffer(ReorderBufferCommitListener listener) {
            this.listener = listener;
        }

        public void add(LogEntry logEntry) {
            if(logEntry.timestamp < oldestTs) { oldestTs = logEntry.timestamp; }
            if(logEntry.timestamp > newestTs) { newestTs = logEntry.timestamp; }
            reorderBuffer.add(logEntry);
            if((newestTs - oldestTs) > REORDER_TIMEFRAME_UPPER_NS) { // commit required
                Collections.sort(reorderBuffer);
                long commitEndTs = (newestTs - REORDER_TIMEFRAME_LOWER_NS);
                int commitEndIdx = 0;
                //TODO: binary search
                for(; commitEndIdx < reorderBuffer.size() && reorderBuffer.get(commitEndIdx).timestamp <= commitEndTs; ++commitEndIdx) {}

                List<LogEntry> commit = reorderBuffer.subList(0, commitEndIdx);
                this.listener.onCommit(commit);
                // remove committed elements from reorderBuffer
                commit.clear();

                if(reorderBuffer.size() > 0) {
                    oldestTs = reorderBuffer.get(0).timestamp;
                    newestTs = reorderBuffer.get(reorderBuffer.size() - 1).timestamp;
                } else {
                    oldestTs = newestTs;
                }
            }
        }

        public void flush() {
            Collections.sort(reorderBuffer);
            this.listener.onCommit(reorderBuffer);
            reorderBuffer.clear();
            newestTs = 0;
            oldestTs = 0;
        }

        public int size() {
            return reorderBuffer.size();
        }

        public long timespan() {
            return (newestTs - oldestTs);
        }

    }
}
