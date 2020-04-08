package de.fhws.indoor.sensorreadout.loggers;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
 * </p>
 * @author Markus Ebner
 */
public final class OrderedLogger extends Logger {

    private static final long CACHED_LINES = 10000;

    // members
    private File file;
    private FileOutputStream fos;
    private ReorderBuffer reorderBuffer;

    public OrderedLogger(Context context) {
        super(context);
    }

    @Override
    public final void onStart() {
        reorderBuffer = new ReorderBuffer(CACHED_LINES, new ReorderBufferListener() {
            @Override
            public void onCommit(List<LogEntry> commitSlice) {
                for(LogEntry entry : commitSlice) {
                    try {
                        fos.write(entry.csv.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

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
        reorderBuffer.start();
    }

    @Override
    public void onStop() {
        reorderBuffer.flushAndStop();
        try {
            fos.close();
        } catch (final Exception e) {
            throw new MyException("error while wriyting log-file", e);
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
        return reorderBuffer.getEntriesCached();
    }

    @Override
    public float getCacheLevel() {
        // 5 InputCache slots + ReorderBuffer
        return (float)reorderBuffer.getEntriesCached() / (float)(6 * CACHED_LINES);
    }

    @Override
    public String getName() {
        if(file != null) {
            return file.getName();
        }
        return "OrderedLogger";
    }


    private interface ReorderBufferListener {
        void onCommit(final List<LogEntry> commitSlice);
    }

    private static class ReorderBuffer {
        private long cacheSize = 0;
        private ArrayList<LogEntry> inputCache;
        private BlockingQueue<ArrayList<LogEntry>> pendingInputCaches;
        private ReorderThread reorderThread;
        private ReorderBufferListener commitListener;

        //statistics
        private AtomicInteger cachedEntries = new AtomicInteger(0);
        private AtomicInteger writtenEntries = new AtomicInteger(0);
        private long sizeTotal = 0;

        //                   #################
        //                   # ReorderBuffer # -> File
        // ##############    # ------------- #
        // # InputCache # -> #               #
        // ##############    #################
        //
        // The InputCache is the fast-access side used by the logger, to append entries.
        // If the InputCache is full, it is committed to the ReorderThread, and swapped
        // for a new / empty InputCache.
        // The ReorderThread sorts the ReorderBuffer, commits the upper half to the writer
        // and inserts the InputCache into the ReorderBuffer

        public ReorderBuffer(long cacheSize, ReorderBufferListener commitListener) {
            this.cacheSize = cacheSize;
            this.commitListener = commitListener;
        }

        public synchronized void start() {
            inputCache = new ArrayList<>();
            inputCache.ensureCapacity((int) cacheSize);
            pendingInputCaches = new ArrayBlockingQueue<>(5);
            reorderThread = new ReorderThread();
            cachedEntries.set(0);
            writtenEntries.set(0);
            sizeTotal = 0;
            reorderThread.start();
        }

        public synchronized void add(LogEntry entry) {
            inputCache.add(entry);
            cachedEntries.incrementAndGet();
            sizeTotal += entry.csv.length();
            if(inputCache.size() == cacheSize) {
                // InputCache full, commit to ReorderThread for processing
                commitInputCache();
            }
        }

        public synchronized void flushAndStop() {
            if(inputCache.size() > 0) {
                commitInputCache();
            }
            // be sure to commit an empty InputCache to signal the ReorderThread
            commitInputCache();
            try {
                reorderThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void commitInputCache() {
            if(!pendingInputCaches.add(inputCache)) {
                throw new RuntimeException("InputCaches overrun. ReorderThread can't keep up.");
            }
            inputCache = new ArrayList<>();
            inputCache.ensureCapacity((int) cacheSize);
        }

        public int getEntriesWritten() { return writtenEntries.get(); }
        public int getEntriesCached() { return cachedEntries.get(); }
        public long getSizeTotal() { return sizeTotal; }

        private class ReorderThread extends Thread {

            public ReorderThread() {
                setName("ReorderThread");
                setPriority(Thread.MIN_PRIORITY);
            }

            @Override
            public void run() {
                ArrayList<LogEntry> reorderBuffer = new ArrayList<>();
                reorderBuffer.ensureCapacity((int) cacheSize * 2);

                try {
                    ArrayList<LogEntry> inputCache = null;
                    while((inputCache = pendingInputCaches.take()) != null) {
                        boolean draining = inputCache.size() == 0;
                        Log.d("OrderedLogger[Thread]", "Received InputCache[size:" + inputCache.size() + " -> draining:" + draining + "]");
                        if(reorderBuffer.size() > (int) cacheSize || draining) {
                            // we are (either):
                            // - in running phase, reorderBuffer is filled and we need to commit
                            // - in draining phase, commit even if reorderBuffer is not full
                            Collections.sort(reorderBuffer);
                            List<LogEntry> commitSlice;
                            if(draining) {
                                // we received an empty InputCache -> drain the complete ReorderBuffer into the writer.
                                commitSlice = reorderBuffer.subList(0, reorderBuffer.size());
                            } else {
                                // we are mid-flight, drain cacheSize at max
                                commitSlice = reorderBuffer.subList(0, Math.min((int) cacheSize, reorderBuffer.size()));
                            }
                            Log.d("OrderedLogger[Thread]", "Committing[drain:" + draining + "] Chunk[size:" + commitSlice.size() + "]");
                            commitListener.onCommit(commitSlice);
                            writtenEntries.addAndGet(commitSlice.size());
                            cachedEntries.addAndGet(-commitSlice.size());
                            commitSlice.clear();
                        }
                        reorderBuffer.addAll(inputCache);

                        if(reorderBuffer.size() == 0 && draining) {
                            Log.d("OrderedLogger[Thread]", "Draining finished");
                            return; // we are done with draining the buffer
                        }
                    }
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
