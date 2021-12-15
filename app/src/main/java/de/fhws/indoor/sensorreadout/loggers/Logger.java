package de.fhws.indoor.sensorreadout.loggers;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import de.fhws.indoor.libsmartphonesensors.sensors.SensorType;

/**
 * Base-Class for all Logger implementations.
 * <p>
 *     This class defines the public interface of all logging implementations.
 * </p>
 * @author Markus Ebner
 */
public abstract class Logger {

    public static final long BEGINNING_TS = -1;

    // work data
    private StringBuilder stringBuilder = new StringBuilder();
    protected Context context;
    /** timestamp of logging start. all entries are relative to this one */
    protected long startTs = 0;

    //statistics
    private AtomicLong statEntryCnt = new AtomicLong(0);
    private AtomicLong statSizeTotal = new AtomicLong(0);

    public Logger(Context context) {
        this.context = context;
    }


    public final void start(FileMetadata metadata) {
        statEntryCnt.set(0);
        statSizeTotal.set(0);
        stringBuilder.setLength(0);
        // starting timestamp
        startTs = SystemClock.elapsedRealtimeNanos();
        onStart();

        // commit metadata
        addCSV(SensorType.FILE_METADATA, BEGINNING_TS, metadata.toCsv());
    }
    public abstract void onStart();

    public final void stop() {
        onStop();
    }
    public abstract void onStop();

    protected abstract void log(LogEntry logEntry);

    public final long getStartTS() {
        return startTs;
    }
    public final long getSizeTotal() { return statSizeTotal.get(); }
    public final long getEventCnt() { return statEntryCnt.get(); }
    public abstract long getEntriesCached();
    public abstract float getCacheLevel();
    public abstract String getName();

    /** add a new CSV entry for the given sensor number to the internal buffer */
    public final void addCSV(final SensorType sensorNr, final long timestamp, final String csv) {
        final long relTS = (timestamp == Logger.BEGINNING_TS) ? 0 : (timestamp - startTs);
        if(relTS >= 0) { // drop pre startTS logs (at the beginning, sensors sometimes deliver old values)
            stringBuilder.append(relTS);	// relative timestamp (uses less space)
            stringBuilder.append(';');
            stringBuilder.append(sensorNr.id());
            stringBuilder.append(';');
            stringBuilder.append(csv);
            stringBuilder.append('\n');
            log(new LogEntry(relTS, stringBuilder.toString()));
            statEntryCnt.incrementAndGet();
            statSizeTotal.addAndGet(stringBuilder.length());
            stringBuilder.setLength(0);
        }
    }

    public static class LogEntry implements Comparable<LogEntry> {
        public long timestamp;
        public String csv;

        public LogEntry(long timestamp, String csv) {
            this.timestamp = timestamp;
            this.csv = csv;
        }

        @Override
        public int compareTo(@NonNull LogEntry another) {
            return Long.compare(timestamp, another.timestamp);
        }
    }

    public static class FileMetadata {
        private String person;
        private String comment;
        private Date date;

        public FileMetadata(String person, String comment) {
            this(person, comment, new Date());
        }
        public FileMetadata(String person, String comment, Date date) {
            this.person = person;
            this.comment = comment;
            this.date = date;
        }

        protected String toCsv() {
            return date.toString() + ";" + person + ";" + comment;
        }
    }

}
