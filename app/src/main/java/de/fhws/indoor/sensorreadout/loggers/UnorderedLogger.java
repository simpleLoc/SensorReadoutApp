package de.fhws.indoor.sensorreadout.loggers;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import de.fhws.indoor.sensorreadout.MyException;

/**
 * Live (unordered) Logger.
 * <p>
 *     This logger takes the incomming events and commits them to the logfile in a background thread.
 *
 *     WARNING: This produces files with non-monotonic increasing timestamps.
 *     Reordering is required on the parser-side, or as post-processing step.
 * </p>
 * @author Frank Ebner
 * @author Markus Ebner
 */
public final class UnorderedLogger extends Logger {

    private static final int LINE_BUFFER_SIZE = 5000;

    private File file;
    private FileOutputStream fos;

    private volatile boolean addingStopped = false; // Just to be sure
    private ArrayBlockingQueue<LogEntry> lineBuffer = new ArrayBlockingQueue<>(LINE_BUFFER_SIZE);
    private WriteBackWorker writeBackWorker;

    public UnorderedLogger(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        writeBackWorker = new WriteBackWorker();

        // open the output-file immediately (to get permission errors)
        // but do NOT yet write anything to the file
        final DataFolder folder = new DataFolder(context, "sensorOutFiles");
        file = new File(folder.getFolder(), startTs + ".csv");

        try {
            fos = new FileOutputStream(file);
            Log.d("logger", "will write to: " + file.toString());
        } catch (final Exception e) {
            throw new MyException("error while opening log-file", e);
        }
        writeBackWorker.start();
    }

    @Override
    public void onStop() {
        addingStopped = true;
        try {
            writeBackWorker.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            fos.close();
        } catch (final Exception e) {
            throw new MyException("error while writing log-file", e);
        }
    }

    @Override
    protected void log(LogEntry logEntry) {
        lineBuffer.add(logEntry);
    }

    @Override
    public long getEntriesCached() {
        return lineBuffer.size();
    }

    @Override
    public float getCacheLevel() {
        return 1.0f - (float)lineBuffer.remainingCapacity() / (float)LINE_BUFFER_SIZE;
    }

    @Override
    public String getName() {
        if(file != null) {
            return file.getName();
        }
        return "OrderedLogger";
    }




    private class WriteBackWorker extends Thread {

        public WriteBackWorker() {
            setName("WriteBackWorker");
            setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    LogEntry entry = lineBuffer.poll();
                    if (entry == null) {
                        if (addingStopped) { // Queue empty, recording stopped. exit
                            return;
                        } else { // Currently no line in queue, wait 10 ms
                            Thread.sleep(10);
                        }
                    } else { // print log line
                        fos.write(entry.csv.getBytes());
                    }
                }
            } catch(InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
