package gg.grumble.core.api;// MumblePlugin.java
import com.sun.jna.*;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.ShortByReference;

public interface MumblePluginModern extends Library {

    // --- Mandatory Plugin Functions ---
    int mumble_init(long pluginID);
    void mumble_shutdown();
    MumbleStringWrapper mumble_getName();
    int mumble_getAPIVersion();
    void mumble_registerAPIFunctions(Pointer apiStruct);
    void mumble_releaseResource(Pointer constPointer);

    // --- General Functions ---
    void mumble_setMumbleInfo(int mumbleVersion, int mumbleAPIVersion, int minimumExpectedAPIVersion);
    int mumble_getVersion();
    MumbleStringWrapper mumble_getAuthor();
    MumbleStringWrapper mumble_getDescription();
    int mumble_getFeatures();
    int mumble_deactivateFeatures(int features);

    // --- Positional Data Functions ---
    byte mumble_initPositionalData(Pointer programNames, Pointer programPIDs, long programCount);
    boolean mumble_fetchPositionalData(
            float[] avatarPos, float[] avatarDir, float[] avatarAxis,
            float[] cameraPos, float[] cameraDir, float[] cameraAxis,
            PointerByReference context, PointerByReference identity);
    void mumble_shutdownPositionalData();
    MumbleStringWrapper mumble_getPositionalDataContextPrefix();

    // --- Event Callback Functions ---
    void mumble_onServerConnected(long connection);
    void mumble_onServerDisconnected(long connection);
    void mumble_onServerSynchronized(long connection);
    void mumble_onChannelEntered(long connection, long userID, long previousChannelID, long newChannelID);
    void mumble_onChannelExited(long connection, long userID, long channelID);
    void mumble_onUserTalkingStateChanged(long connection, long userID, int talkingState);
    boolean mumble_onAudioInput(ShortByReference inputPCM, int sampleCount, short channelCount, int sampleRate, boolean isSpeech);
    boolean mumble_onAudioSourceFetched(FloatByReference outputPCM, int sampleCount, short channelCount, int sampleRate, boolean isSpeech, long userID);
    boolean mumble_onAudioOutputAboutToPlay(FloatByReference outputPCM, int sampleCount, short channelCount, int sampleRate);
    boolean mumble_onReceiveData(long connection, long sender, Pointer data, long dataLength, String dataID);
    void mumble_onUserAdded(long connection, long userID);
    void mumble_onUserRemoved(long connection, long userID);
    void mumble_onChannelAdded(long connection, long channelID);
    void mumble_onChannelRemoved(long connection, long channelID);
    void mumble_onChannelRenamed(long connection, long channelID);
    void mumble_onKeyEvent(int keyCode, boolean wasPress);

    // --- Plugin Update ---
    boolean mumble_hasUpdate();
    MumbleStringWrapper mumble_getUpdateDownloadURL();

    // --- Plugin Function Version ---
    int mumble_getPluginFunctionsVersion();
}