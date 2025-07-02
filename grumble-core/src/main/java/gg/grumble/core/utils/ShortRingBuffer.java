package gg.grumble.core.utils;

public class ShortRingBuffer {
	private final short[] buffer;
	private int writePos = 0;
	private int readPos = 0;
	private int size = 0;

	public ShortRingBuffer(int capacity) {
		this.buffer = new short[capacity];
	}

	public synchronized int write(short[] src, int offset, int length) {
		int written = 0;
		for (int i = 0; i < length; i++) {
			if (size == buffer.length) break; // full
			buffer[writePos] = src[offset + i];
			writePos = (writePos + 1) % buffer.length;
			size++;
			written++;
		}
		return written;
	}

	public synchronized int read(short[] dst, int offset, int length) {
		int read = 0;
		for (int i = 0; i < length; i++) {
			if (size == 0) break; // empty
			dst[offset + i] = buffer[readPos];
			readPos = (readPos + 1) % buffer.length;
			size--;
			read++;
		}
		return read;
	}

	public synchronized int available() {
		return size;
	}

	public synchronized void clear() {
		size = 0;
		readPos = 0;
		writePos = 0;
	}
}
