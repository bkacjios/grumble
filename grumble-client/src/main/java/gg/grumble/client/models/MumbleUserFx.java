package gg.grumble.client.models;

import gg.grumble.core.models.MumbleUser;
import javafx.beans.property.*;

public class MumbleUserFx {
    private final MumbleUser user;

    private final StringProperty name = new SimpleStringProperty();
    private final BooleanProperty mute = new SimpleBooleanProperty();
    private final BooleanProperty deaf = new SimpleBooleanProperty();
    private final BooleanProperty suppressed = new SimpleBooleanProperty();
    private final BooleanProperty selfMute = new SimpleBooleanProperty();
    private final BooleanProperty selfDeaf = new SimpleBooleanProperty();
    private final BooleanProperty localMute = new SimpleBooleanProperty();
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
