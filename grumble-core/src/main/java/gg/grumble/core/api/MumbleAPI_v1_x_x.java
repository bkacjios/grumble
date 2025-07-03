package gg.grumble.core.api;

import com.sun.jna.*;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.DoubleByReference;

import java.util.List;

public class MumbleAPI_v1_x_x extends Structure {

    public interface FreeMemory extends Callback {
        int invoke(long callerID, Pointer pointer);
    }

    public interface GetActiveServerConnection extends Callback {
        int invoke(long callerID, LongByReference connection);
    }

    public interface IsConnectionSynchronized extends Callback {
        int invoke(long callerID, long connection, IntByReference synchronizedRef);
    }

    public interface GetLocalUserID extends Callback {
        int invoke(long callerID, long connection, LongByReference userID);
    }

    public interface GetUserName extends Callback {
        int invoke(long callerID, long connection, long userID, PointerByReference userName);
    }

    public interface GetChannelName extends Callback {
        int invoke(long callerID, long connection, long channelID, PointerByReference channelName);
    }

    public interface GetAllUsers extends Callback {
        int invoke(long callerID, long connection, PointerByReference users, PointerByReference userCount);
    }

    public interface GetAllChannels extends Callback {
        int invoke(long callerID, long connection, PointerByReference channels, PointerByReference channelCount);
    }

    public interface GetChannelOfUser extends Callback {
        int invoke(long callerID, long connection, long userID, LongByReference channelID);
    }

    public interface GetUsersInChannel extends Callback {
        int invoke(long callerID, long connection, long channelID, PointerByReference userList, PointerByReference userCount);
    }

    public interface GetUserHash extends Callback {
        int invoke(long callerID, long connection, long userID, PointerByReference hash);
    }

    public interface GetServerHash extends Callback {
        int invoke(long callerID, long connection, PointerByReference hash);
    }

    public interface GetUserComment extends Callback {
        int invoke(long callerID, long connection, long userID, PointerByReference comment);
    }

    public interface GetChannelDescription extends Callback {
        int invoke(long callerID, long connection, long channelID, PointerByReference description);
    }

    public interface GetLocalUserTransmissionMode extends Callback {
        int invoke(long callerID, IntByReference transmissionMode);
    }

    public interface IsUserLocallyMuted extends Callback {
        int invoke(long callerID, long connection, long userID, IntByReference muted);
    }

    public interface IsLocalUserMuted extends Callback {
        int invoke(long callerID, IntByReference muted);
    }

    public interface IsLocalUserDeafened extends Callback {
        int invoke(long callerID, IntByReference deafened);
    }

    public interface RequestLocalUserTransmissionMode extends Callback {
        int invoke(long callerID, int transmissionMode);
    }

    public interface RequestUserMove extends Callback {
        int invoke(long callerID, long connection, long userID, long channelID, String password);
    }

    public interface RequestMicrophoneActivationOverwrite extends Callback {
        int invoke(long callerID, boolean activate);
    }

    public interface RequestLocalMute extends Callback {
        int invoke(long callerID, long connection, long userID, boolean muted);
    }

    public interface RequestLocalUserMute extends Callback {
        int invoke(long callerID, boolean muted);
    }

    public interface RequestLocalUserDeaf extends Callback {
        int invoke(long callerID, boolean deafened);
    }

    public interface RequestSetLocalUserComment extends Callback {
        int invoke(long callerID, long connection, String comment);
    }

    public interface FindUserByName extends Callback {
        int invoke(long callerID, long connection, String userName, LongByReference userID);
    }

    public interface FindChannelByName extends Callback {
        int invoke(long callerID, long connection, String channelName, LongByReference channelID);
    }

    public interface GetMumbleSetting_bool extends Callback {
        int invoke(long callerID, int key, IntByReference outValue);
    }

    public interface GetMumbleSetting_int extends Callback {
        int invoke(long callerID, int key, LongByReference outValue);
    }

    public interface GetMumbleSetting_double extends Callback {
        int invoke(long callerID, int key, DoubleByReference outValue);
    }

    public interface GetMumbleSetting_string extends Callback {
        int invoke(long callerID, int key, PointerByReference outValue);
    }

    public interface SetMumbleSetting_bool extends Callback {
        int invoke(long callerID, int key, boolean value);
    }

    public interface SetMumbleSetting_int extends Callback {
        int invoke(long callerID, int key, long value);
    }

    public interface SetMumbleSetting_double extends Callback {
        int invoke(long callerID, int key, double value);
    }

    public interface SetMumbleSetting_string extends Callback {
        int invoke(long callerID, int key, String value);
    }

    public interface SendData extends Callback {
        int invoke(long callerID, long connection, Pointer users, long userCount, Pointer data, long dataLength, String dataID);
    }

    public interface Log extends Callback {
        int invoke(long callerID, String message);
    }

    public interface PlaySample extends Callback {
        int invoke(long callerID, String samplePath, float volume);
    }

    public FreeMemory freeMemory;
    public GetActiveServerConnection getActiveServerConnection;
    public IsConnectionSynchronized isConnectionSynchronized;
    public GetLocalUserID getLocalUserID;
    public GetUserName getUserName;
    public GetChannelName getChannelName;
    public GetAllUsers getAllUsers;
    public GetAllChannels getAllChannels;
    public GetChannelOfUser getChannelOfUser;
    public GetUsersInChannel getUsersInChannel;
    public GetUserHash getUserHash;
    public GetServerHash getServerHash;
    public GetUserComment getUserComment;
    public GetChannelDescription getChannelDescription;
    public GetLocalUserTransmissionMode getLocalUserTransmissionMode;
    public IsUserLocallyMuted isUserLocallyMuted;
    public IsLocalUserMuted isLocalUserMuted;
    public IsLocalUserDeafened isLocalUserDeafened;
    public RequestLocalUserTransmissionMode requestLocalUserTransmissionMode;
    public RequestUserMove requestUserMove;
    public RequestMicrophoneActivationOverwrite requestMicrophoneActivationOverwrite;
    public RequestLocalMute requestLocalMute;
    public RequestLocalUserMute requestLocalUserMute;
    public RequestLocalUserDeaf requestLocalUserDeaf;
    public RequestSetLocalUserComment requestSetLocalUserComment;
    public FindUserByName findUserByName;
    public FindChannelByName findChannelByName;
    public GetMumbleSetting_bool getMumbleSetting_bool;
    public GetMumbleSetting_int getMumbleSetting_int;
    public GetMumbleSetting_double getMumbleSetting_double;
    public GetMumbleSetting_string getMumbleSetting_string;
    public SetMumbleSetting_bool setMumbleSetting_bool;
    public SetMumbleSetting_int setMumbleSetting_int;
    public SetMumbleSetting_double setMumbleSetting_double;
    public SetMumbleSetting_string setMumbleSetting_string;
    public SendData sendData;
    public Log log;
    public PlaySample playSample;

    @Override
    protected List<String> getFieldOrder() {
        return List.of(
                "freeMemory",
                "getActiveServerConnection",
                "isConnectionSynchronized",
                "getLocalUserID",
                "getUserName",
                "getChannelName",
                "getAllUsers",
                "getAllChannels",
                "getChannelOfUser",
                "getUsersInChannel",
                "getUserHash",
                "getServerHash",
                "getUserComment",
                "getChannelDescription",
                "getLocalUserTransmissionMode",
                "isUserLocallyMuted",
                "isLocalUserMuted",
                "isLocalUserDeafened",
                "requestLocalUserTransmissionMode",
                "requestUserMove",
                "requestMicrophoneActivationOverwrite",
                "requestLocalMute",
                "requestLocalUserMute",
                "requestLocalUserDeaf",
                "requestSetLocalUserComment",
                "findUserByName",
                "findChannelByName",
                "getMumbleSetting_bool",
                "getMumbleSetting_int",
                "getMumbleSetting_double",
                "getMumbleSetting_string",
                "setMumbleSetting_bool",
                "setMumbleSetting_int",
                "setMumbleSetting_double",
                "setMumbleSetting_string",
                "sendData",
                "log",
                "playSample"
        );
    }

    public static class ByReference extends MumbleAPI_v1_x_x implements Structure.ByReference {
    }
}
