package gg.grumble.client.models;

import gg.grumble.core.models.MumbleUser;
import javafx.beans.property.*;

public class MumbleUserFx {
    private final MumbleUser user;

    private final BooleanProperty authenticated = new SimpleBooleanProperty();
    private final LongProperty userId = new SimpleLongProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final BooleanProperty mute = new SimpleBooleanProperty();
    private final BooleanProperty deaf = new SimpleBooleanProperty();
    private final BooleanProperty suppressed = new SimpleBooleanProperty();
    private final BooleanProperty selfMute = new SimpleBooleanProperty();
    private final BooleanProperty selfDeaf = new SimpleBooleanProperty();
    private final BooleanProperty localMute = new SimpleBooleanProperty();
    private final FloatProperty localVolume = new SimpleFloatProperty();
    private final BooleanProperty prioritySpeaker = new SimpleBooleanProperty();
    private final BooleanProperty recording = new SimpleBooleanProperty();
    private final BooleanProperty speaking = new SimpleBooleanProperty();
    private final StringProperty comment = new SimpleStringProperty();

    public MumbleUserFx(MumbleUser user) {
        this.user = user;
        refresh();
    }

    public MumbleUser getUser() {
        return user;
    }

    // authenticated
    public BooleanProperty authenticatedProperty() {
        return authenticated;
    }

    public boolean isAuthenticated() {
        return authenticated.get();
    }

    public void setAuthenticated(boolean v) {
        authenticated.set(v);
    }

    // userId
    public LongProperty userIdProperty() {
        return userId;
    }

    public long getUserId() {
        return userId.get();
    }

    public void setUserId(long v) {
        userId.set(v);
    }

    // name
    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String v) {
        name.set(v);
    }

    // mute
    public BooleanProperty muteProperty() {
        return mute;
    }

    public boolean isMute() {
        return mute.get();
    }

    public void setMute(boolean v) {
        mute.set(v);
    }

    // deaf
    public BooleanProperty deafProperty() {
        return deaf;
    }

    public boolean isDeaf() {
        return deaf.get();
    }

    public void setDeaf(boolean v) {
        deaf.set(v);
    }

    // suppressed
    public BooleanProperty suppressedProperty() {
        return suppressed;
    }

    public boolean isSuppressed() {
        return suppressed.get();
    }

    public void setSuppressed(boolean v) {
        suppressed.set(v);
    }

    // selfMute
    public BooleanProperty selfMuteProperty() {
        return selfMute;
    }

    public boolean isSelfMute() {
        return selfMute.get();
    }

    public void setSelfMute(boolean v) {
        selfMute.set(v);
    }

    // selfDeaf
    public BooleanProperty selfDeafProperty() {
        return selfDeaf;
    }

    public boolean isSelfDeaf() {
        return selfDeaf.get();
    }

    public void setSelfDeaf(boolean v) {
        selfDeaf.set(v);
    }

    // localVolume
    public FloatProperty localVolumeProperty() {
        return localVolume;
    }

    public float getLocalVolume() {
        return localVolume.get();
    }

    public void setLocalVolume(float v) {
        localVolume.set(v);
    }


    // localMute
    public BooleanProperty localMuteProperty() {
        return localMute;
    }

    public boolean isLocalMute() {
        return localMute.get();
    }

    public void setLocalMute(boolean v) {
        localMute.set(v);
    }

    // prioritySpeaker
    public BooleanProperty prioritySpeakerProperty() {
        return prioritySpeaker;
    }

    public boolean isPrioritySpeaker() {
        return prioritySpeaker.get();
    }

    public void setPrioritySpeaker(boolean v) {
        prioritySpeaker.set(v);
    }

    // recording
    public BooleanProperty recordingProperty() {
        return recording;
    }

    public boolean isRecording() {
        return recording.get();
    }

    public void setRecording(boolean v) {
        recording.set(v);
    }

    // speaking
    public BooleanProperty speakingProperty() {
        return speaking;
    }

    public boolean isSpeaking() {
        return speaking.get();
    }

    public void setSpeaking(boolean v) {
        speaking.set(v);
    }

    // comment
    public StringProperty commentProperty() {
        return comment;
    }

    public String getComment() {
        return comment.get();
    }

    public void setComment(String v) {
        comment.set(v);
    }

    // copy values from model
    public void refresh() {
        long userId = user.getUserId();
        setAuthenticated(userId > 0);
        setUserId(userId);
        setName(user.getName());
        setMute(user.isMute());
        setDeaf(user.isDeaf());
        setSuppressed(user.isSuppressed());
        setSelfMute(user.isSelfMute());
        setSelfDeaf(user.isSelfDeaf());
        setPrioritySpeaker(user.isPrioritySpeaker());
        setRecording(user.isRecording());
        setSpeaking(user.isSpeaking());
        setComment(user.getComment());
    }

    // update underlying model and refresh properties
    public void update() {
        refresh();
    }

    @Override
    public String toString() {
        return getName();
    }
}
