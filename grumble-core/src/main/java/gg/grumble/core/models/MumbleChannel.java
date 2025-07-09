package gg.grumble.core.models;

import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.enums.MumbleMessageType;
import gg.grumble.mumble.MumbleProto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MumbleChannel {
	private final MumbleClient client;
	private final long channelId;

	private long parent;
	private String name;
	private String description;
	private boolean temporary;
	private int position;
	private byte[] descriptionHash;
	private int maxUsers;
	private boolean isEnterRestricted;
	private boolean canEnter;

	private final Set<Integer> links = new LinkedHashSet<>();

	public MumbleChannel(MumbleClient client, long channelId) {
		this.client = client;
		this.channelId = channelId;
	}

	public void update(MumbleProto.ChannelState state) {
		if (state.hasChannelId() && state.getChannelId() != this.channelId) {
			throw new IllegalArgumentException("Channel ID mismatch: expected " + this.channelId + ", got " + state.getChannelId());
		}

		if (state.hasParent()) this.parent = Integer.toUnsignedLong(state.getParent());
		if (state.hasName()) this.name = state.getName();
		if (state.hasDescription()) this.description = state.getDescription();
		if (state.hasTemporary()) this.temporary = state.getTemporary();
		if (state.hasPosition()) this.position = state.getPosition();
		if (state.hasDescriptionHash()) this.descriptionHash = state.getDescriptionHash().toByteArray();
		if (state.hasMaxUsers()) this.maxUsers = state.getMaxUsers();
		if (state.hasIsEnterRestricted()) this.isEnterRestricted = state.getIsEnterRestricted();
		if (state.hasCanEnter()) this.canEnter = state.getCanEnter();

		if (!state.getLinksList().isEmpty()) {
			links.clear();
			links.addAll(state.getLinksList());
		}

		links.addAll(state.getLinksAddList());

		for (int remove : state.getLinksRemoveList()) {
			links.remove(remove);
		}
	}

	public MumbleClient getClient() { return client; }

	public long getChannelId() {
		return channelId;
	}

	public long getParentId() {
		return parent;
	}

	public MumbleChannel getParent() {
		return client.getChannel(parent);
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isTemporary() {
		return temporary;
	}

	public int getPosition() {
		return position;
	}

	public byte[] getDescriptionHash() {
		return descriptionHash;
	}

	public int getMaxUsers() {
		return maxUsers;
	}

	public boolean isEnterRestricted() {
		return isEnterRestricted;
	}

	public boolean canEnter() {
		return canEnter;
	}

	public Set<Integer> getLinks() {
		return links;
	}

	public List<MumbleUser> getUsers() {
		return client.getChannelUsers(channelId);
	}

	public List<MumbleChannel> getChildren() {
		return client.getChildren(channelId);
	}

	public void message(String message) {
		message(message, false);
	}

	public void message(String message, boolean recursive) {
		MumbleProto.TextMessage.Builder textMessage = MumbleProto.TextMessage.newBuilder();
		textMessage.setMessage(message);
		if (recursive) {
			textMessage.addChannelId((int) this.channelId);
		} else {
			textMessage.addTreeId((int) this.channelId);
		}
		client.send(MumbleMessageType.TEXT_MESSAGE, textMessage.build());
	}

	public void createChannel(String name) {
		createChannel(name, "", 0, 0, false);
	}

	public void createChannel(String name, boolean temporary) {
		createChannel(name, "", 0, 0, temporary);
	}

	public void createChannel(String name, String description) {
		createChannel(name, description, 0, 0, false);
	}

	public void createChannel(String name, String description, boolean temporary) {
		createChannel(name, description, 0, 0, temporary);
	}

	public void createChannel(String name, String description, int position, long maxUsers, boolean temporary) {
		MumbleProto.ChannelState.Builder channel = MumbleProto.ChannelState.newBuilder();
		channel.setParent((int) this.channelId);
		channel.setName(name);
		channel.setDescription(description);
		channel.setPosition(position);
		channel.setMaxUsers((int) maxUsers);
		channel.setTemporary(temporary);
		client.send(MumbleMessageType.CHANNEL_STATE, channel.build());
	}

	@SuppressWarnings("unused")
	public MumbleChannel resolveRelativePath(String path) {
		if (path == null || path.isEmpty()) return this;

		String[] parts = path.split("[/\\\\]+"); // supports both / and \
		MumbleChannel current = this;

		for (String part : parts) {
			if (part.isEmpty() || part.equals(".")) {
				continue;
			}
			if (part.equals("..")) {
				current = current.getParent();
				if (current == null) return null;
			} else {
				// Scan only direct children
				MumbleChannel found = null;
				for (MumbleChannel chan : current.getChildren()) {
					if (Objects.equals(chan.getParentId(), current.getChannelId()) &&
							part.equals(chan.getName())) {
						found = chan;
						break;
					}
				}
				if (found == null) return null;
				current = found;
			}
		}
		return current;
	}

	@Override
	public String toString() {
		return "MumbleChannel{" +
				"channelId=" + channelId +
				", name='" + name + '\'' +
				", parent=" + parent +
				", temporary=" + temporary +
				", position=" + position +
				", maxUsers=" + maxUsers +
				'}';
	}
}
