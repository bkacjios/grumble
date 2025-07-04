package gg.grumble.client.models;

import gg.grumble.core.models.MumbleUser;
import javafx.beans.property.*;

public class MumbleUserFx {
	private final MumbleUser user;

	private final BooleanProperty speaking = new SimpleBooleanProperty();
	private final BooleanProperty mute = new SimpleBooleanProperty();
	private final BooleanProperty deaf = new SimpleBooleanProperty();
	private final StringProperty name = new SimpleStringProperty();

	public MumbleUserFx(MumbleUser user) {
		this.user = user;
		refresh();
	}

	public MumbleUser getUser() {
		return user;
	}

	public BooleanProperty speakingProperty() {
		return speaking;
	}

	public boolean isSpeaking() {
		return speaking.get();
	}

	public void setSpeaking(boolean value) {
		speaking.set(value);
	}

	public BooleanProperty muteProperty() {
		return mute;
	}

	public StringProperty nameProperty() {
		return name;
	}

	public String getName() {
		return name.get();
	}

	public void refresh() {
		mute.set(user.isMute());
		deaf.set(user.isDeaf());
		name.set(user.getName());
		speaking.set(user.isSpeaking());
	}

	@Override
	public String toString() {
		return getName();
	}
}
