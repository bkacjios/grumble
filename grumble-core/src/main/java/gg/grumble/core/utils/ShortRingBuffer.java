package gg.grumble.core.utils;

import java.util.Arrays;

public class ShortRingBuffer {
	private final short[] buffer;
	private int writePos = 0;
	private int readPos = 0;
	private int size = 0;

	private final int jitterThreshold; // number of samples to wait before allowing read
	private boolean jitterSatisfied = false;

	public ShortRingBuffer(int capacity, int jitterThreshold) {
		this.buffer = new short[capacity];
		this.jitterThreshold = jitterThreshold;
	}

	public synchronized int write(short[] src, int offset, int length) {
		int written = 0;
		for (int i = 0; i < length; i++) {
			if (size == buffer.length) {
				// Optional: drop oldest data if overflow
				readPos = (readPos + 1) % buffer.length;
				size--;
			}
			buffer[writePos] = src[offset + i];
			writePos = (writePos + 1) % buffer.length;
			size++;
			written++;
		}

		if (!jitterSatisfied && size >= jitterThreshold) {
			jitterSatisfied = true;
		}

		return written;
	}

	public synchronized int read(short[] dst, int offset, int length) {
		if (!jitterSatisfied) {
			Arrays.fill(dst, offset, offset + length, (short) 0);
			return length; // silence
		}

		int read = 0;
		for (int i = 0; i < length; i++) {
			if (size == 0) {
				dst[offset + i] = 0; // pad with silence
			} else {
				dst[offset + i] = buffer[readPos];
				readPos = (readPos + 1) % buffer.length;
				size--;
			}
			read++;
		}
		return read;
	}

	public synchronized int available() {
		return size;
	}

	public synchronized void resetJitter() {
		jitterSatisfied = false;
	}

	public synchronized void clear() {
		size = 0;
		readPos = 0;
		writePos = 0;
		jitterSatisfied = false;
	}
}
