package gg.grumble.core.client;

import gg.grumble.mumble.MumbleProto;

import java.util.Collections;
import java.util.List;

public class MumbleChannel {
	private final MumbleClient client;
	private final int channelId;

	private Integer parent;
	private String name;
	private String description;
	private boolean temporary;
	private int position;
	private byte[] descriptionHash;
	private int maxUsers;
	private boolean isEnterRestricted;
	private boolean canEnter;

	private List<Integer> links = Collections.emptyList();
	private List<Integer> linksAdd = Collections.emptyList();
	private List<Integer> linksRemove = Collections.emptyList();

	public MumbleChannel(MumbleClient client, int channelId) {
		this.client = client;
		this.channelId = channelId;
	}

	public void update(MumbleProto.ChannelState state) {
		if (state.hasChannelId() && state.getChannelId() != this.channelId) {
			throw new IllegalArgumentException("Channel ID mismatch: expected " + this.channelId + ", got " + state.getChannelId());
		}

		if (state.hasParent()) this.parent = state.getParent();
		if (state.hasName()) this.name = state.getName();
		if (state.hasDescription()) this.description = state.getDescription();
		if (state.hasTemporary()) this.temporary = state.getTemporary();
		if (state.hasPosition()) this.position = state.getPosition();
		if (state.hasDescriptionHash()) this.descriptionHash = state.getDescriptionHash().toByteArray();
		if (state.hasMaxUsers()) this.maxUsers = state.getMaxUsers();
		if (state.hasIsEnterRestricted()) this.isEnterRestricted = state.getIsEnterRestricted();
		if (state.hasCanEnter()) this.canEnter = state.getCanEnter();

		if (!state.getLinksList().isEmpty()) {
			this.links = state.getLinksList();
		}

		if (!state.getLinksAddList().isEmpty()) {
			this.linksAdd = state.getLinksAddList();
		}

		if (!state.getLinksRemoveList().isEmpty()) {
			this.linksRemove = state.getLinksRemoveList();
		}
	}

	public MumbleClient getClient() { return client; }

	public int getChannelId() {
		return channelId;
	}

	public Integer getParent() {
		return parent;
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

	public List<Integer> getLinks() {
		return links;
	}

	public List<Integer> getLinksAdd() {
		return linksAdd;
	}

	public List<Integer> getLinksRemove() {
		return linksRemove;
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
