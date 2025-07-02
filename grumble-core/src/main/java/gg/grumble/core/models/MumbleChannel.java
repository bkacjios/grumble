package gg.grumble.core.models;

import gg.grumble.core.client.MumbleClient;
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
		return client.getUsers().stream()
				.filter(user -> Objects.equals(this.channelId, user.getChannelId()))
				.toList();
	}

	public List<MumbleChannel> getChildren() {
		return client.getChildren(this.channelId);
	}

	@SuppressWarnings("unused")
	public MumbleChannel resolveRelativePath(String path) {
		if (path == null || path.isEmpty()) return this;

		String[] parts = path.split("[/\\\\]+"); // supports both / and \
		MumbleChannel current = this;

		for (String part : parts) {
			if (part.isEmpty() || part.equals(".")) {
				continue;
			} else if (part.equals("..")) {
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
