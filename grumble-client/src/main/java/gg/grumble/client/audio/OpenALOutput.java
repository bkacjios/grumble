package gg.grumble.client.audio;

import gg.grumble.core.audio.AudioOutput;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static gg.grumble.core.enums.MumbleAudioConfig.SAMPLE_RATE;
import static gg.grumble.core.enums.MumbleAudioConfig.SAMPLES_PER_FRAME_TOTAL;

public class OpenALOutput implements AudioOutput {
    private long device;
    private long context;
    private ALCCapabilities alcCaps;
    private int source;
    private int[] buffers;

    @Override
    public void start() {
        // 1) Open device and create context on this thread
        device = ALC10.alcOpenDevice((ByteBuffer) null);
        if (device == 0) throw new IllegalStateException("alcOpenDevice failed");

        context = ALC10.alcCreateContext(device, (IntBuffer) null);
        ALC10.alcMakeContextCurrent(context);

        // 2) Initialize capabilities properly and store them for rebinding
        alcCaps = ALC.createCapabilities(device);
        AL.createCapabilities(alcCaps);

        // 3) Generate source and N buffers
        source = AL10.alGenSources();
        buffers = new int[4];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = AL10.alGenBuffers();
        }

        // 4) Prefill buffers with silence to enqueue initial queue
        int bytesPerFrame = SAMPLES_PER_FRAME_TOTAL * 2; // 16-bit samples
        ByteBuffer silence = BufferUtils.createByteBuffer(bytesPerFrame);
        for (int bufId : buffers) {
            AL10.alBufferData(bufId, AL10.AL_FORMAT_STEREO16, silence, SAMPLE_RATE);
            AL10.alSourceQueueBuffers(source, bufId);
        }

        // 5) Start playback
        AL10.alSourcePlay(source);
    }

    @Override
    public void stop() {
        // No-op: handled in close()
    }

    @Override
    public void write(byte[] pcm, int offset, int length) {
        // Rebind context & caps on this thread to ensure valid context
        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(alcCaps);

        // Prepare PCM data
        ByteBuffer data = BufferUtils.createByteBuffer(length)
                .put(pcm, offset, length)
                .flip();

        // Unqueue any processed buffers
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        if (processed > 0) {
            IntBuffer unq = BufferUtils.createIntBuffer(processed);
            AL10.alSourceUnqueueBuffers(source, unq);
            checkError("alSourceUnqueueBuffers");

            // Refill & requeue each freed buffer
            for (int i = 0; i < processed; i++) {
                int bufId = unq.get(i);
                AL10.alBufferData(bufId, AL10.AL_FORMAT_STEREO16, data, SAMPLE_RATE);
                checkError("alBufferData");
                AL10.alSourceQueueBuffers(source, bufId);
                checkError("alSourceQueueBuffers");

                // rewind data for next fill
                data.rewind();
            }
        }

        // Ensure playback continues
        int state  = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        checkError("alGetSourcei(STATE/QUEUED)");
        if (queued > 0 && state != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);
            checkError("alSourcePlay");
        }
    }

    @Override
    public void setVolume(float volume) {
        volume = Math.max(0f, Math.min(1f, volume));
        AL10.alSourcef(source, AL10.AL_GAIN, volume);
    }

    @Override
    public float getVolume() {
        return AL10.alGetSourcef(source, AL10.AL_GAIN);
    }

    @Override
    public void close() {
        // Stop source
        AL10.alSourceStop(source);
        // Delete source and buffers
        AL10.alDeleteSources(source);
        for (int bufId : buffers) {
            AL10.alDeleteBuffers(bufId);
        }
        // Destroy context and close device
        ALC10.alcMakeContextCurrent(0);
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
    }

    private void checkError(String label) {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            System.err.println("OpenAL error after " + label + ": " + err);
        }
    }
}