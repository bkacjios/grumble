package gg.grumble.core.utils;

import java.util.Arrays;

public class FloatRingBuffer {
	private final float[] buffer;
	private int writePos = 0;
	private int readPos = 0;
	private int size = 0;

	private final int jitterThreshold; // samples to buffer before playback
	private boolean jitterSatisfied = false;

	public FloatRingBuffer(int capacity, int jitterThreshold) {
		this.buffer = new float[capacity];
		this.jitterThreshold = jitterThreshold;
	}

	public synchronized int write(float[] src, int offset, int length) {
		int written = 0;
		for (int i = 0; i < length; i++) {
			if (size == buffer.length) {
				// Optional: drop oldest if overflow
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

	public synchronized int read(float[] dst, int offset, int length) {
		if (!jitterSatisfied) {
			Arrays.fill(dst, offset, offset + length, 0.0f);
			return 0;
		}

		int actualRead = Math.min(size, length);

		// Copy available samples
		for (int i = 0; i < actualRead; i++) {
			dst[offset + i] = buffer[readPos];
			readPos = (readPos + 1) % buffer.length;
			size--;
		}

		// Fill rest with silence
		Arrays.fill(dst, offset + actualRead, offset + length, 0.0f);

		return actualRead;
	}

	public synchronized int available() {
		return size;
	}

	public synchronized void resetJitter() {
		jitterSatisfied = false;
	}

	public synchronized void clear() {
		writePos = 0;
		readPos = 0;
		size = 0;
		jitterSatisfied = false;
	}
}
