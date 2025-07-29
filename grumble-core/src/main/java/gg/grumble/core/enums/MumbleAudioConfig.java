package gg.grumble.core.enums;

public class MumbleAudioConfig {
	public static final int SAMPLE_RATE = 48000;
	public static final int PLAYBACK_DURATION_MS = 20;
	public static final int CHANNELS = 2;
	public static final int SAMPLES_PER_FRAME = (SAMPLE_RATE * PLAYBACK_DURATION_MS) / 1000;
	public static final int SAMPLES_PER_FRAME_TOTAL = SAMPLES_PER_FRAME * CHANNELS;
	public static final int JITTER_MAX_PLC_FRAMES = 10;
	public static final int JITTER_PREFILL_FRAMES = 5;
	public static final int JITTER_PREFILL_TIMEOUT_MS = JITTER_PREFILL_FRAMES * PLAYBACK_DURATION_MS;
	public static final int JITTER_MAX_TOTAL_FRAMES = JITTER_PREFILL_FRAMES + JITTER_MAX_PLC_FRAMES;

}
