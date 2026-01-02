package com.scribble.server.monitor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * A fixed-size ring buffer for storing time-series data samples.
 * Thread-safe for single-writer, multi-reader scenarios.
 * Used for latency history, throughput graphs, etc.
 */
public final class TimeSeriesRingBuffer implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long[] timestamps;
    private final double[] values;
    private final int capacity;
    private volatile int head;
    private volatile int size;
    private final Object lock = new Object();

    public TimeSeriesRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.timestamps = new long[capacity];
        this.values = new double[capacity];
        this.head = 0;
        this.size = 0;
    }

    /**
     * Add a sample with current timestamp.
     */
    public void add(double value) {
        add(System.currentTimeMillis(), value);
    }

    /**
     * Add a sample with specific timestamp.
     */
    public void add(long timestamp, double value) {
        synchronized (lock) {
            timestamps[head] = timestamp;
            values[head] = value;
            head = (head + 1) % capacity;
            if (size < capacity) {
                size++;
            }
        }
    }

    /**
     * Get all samples in chronological order.
     */
    public Sample[] getSamples() {
        synchronized (lock) {
            Sample[] samples = new Sample[size];
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                int idx = (start + i) % capacity;
                samples[i] = new Sample(timestamps[idx], values[idx]);
            }
            return samples;
        }
    }

    /**
     * Get only the values in chronological order (for simple graphs).
     */
    public double[] getValues() {
        synchronized (lock) {
            double[] result = new double[size];
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                result[i] = values[(start + i) % capacity];
            }
            return result;
        }
    }

    /**
     * Get only the timestamps in chronological order.
     */
    public long[] getTimestamps() {
        synchronized (lock) {
            long[] result = new long[size];
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                result[i] = timestamps[(start + i) % capacity];
            }
            return result;
        }
    }

    public int getSize() {
        return size;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getAverage() {
        synchronized (lock) {
            if (size == 0)
                return 0;
            double sum = 0;
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                sum += values[(start + i) % capacity];
            }
            return sum / size;
        }
    }

    public double getMin() {
        synchronized (lock) {
            if (size == 0)
                return 0;
            double min = Double.MAX_VALUE;
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                min = Math.min(min, values[(start + i) % capacity]);
            }
            return min;
        }
    }

    public double getMax() {
        synchronized (lock) {
            if (size == 0)
                return 0;
            double max = Double.MIN_VALUE;
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                max = Math.max(max, values[(start + i) % capacity]);
            }
            return max;
        }
    }

    public double getLatest() {
        synchronized (lock) {
            if (size == 0)
                return 0;
            return values[(head - 1 + capacity) % capacity];
        }
    }

    public void clear() {
        synchronized (lock) {
            head = 0;
            size = 0;
            Arrays.fill(timestamps, 0);
            Arrays.fill(values, 0);
        }
    }

    /**
     * Represents a single time-series sample.
     */
    public static class Sample implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final long timestamp;
        public final double value;

        public Sample(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
