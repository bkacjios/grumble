package gg.grumble.core.client;

import gg.grumble.mumble.MumbleProto;
import gg.grumble.mumble.MumbleUDPProto;

public final class MumbleEvents {

    public interface MumbleEvent {}

    public static class Connected implements MumbleEvent {}

    public record Disconnected(String reason) implements MumbleEvent {
    }

    public record ServerVersion(MumbleProto.Version version) implements MumbleEvent {
    }

    public record ServerPongTcp(MumbleProto.Ping ping) implements MumbleEvent {
    }

    public record ServerPongUdpLegacy(float ping) implements MumbleEvent {
    }

    public record ServerPongUdpProtobuf(MumbleUDPProto.Ping ping) implements MumbleEvent {
    }

    public record TcpTunnelActive(boolean active) implements MumbleEvent {
    }

    public record ServerReject(MumbleProto.Reject reject) implements MumbleEvent {
    }

    public record ServerSync(MumbleUser me, MumbleProto.ServerSync sync) implements MumbleEvent {
    }

    public record ChannelRemove(MumbleChannel channel, MumbleProto.ChannelRemove remove) implements MumbleEvent {
    }

    public record ChannelState(MumbleChannel channel, MumbleProto.ChannelState state) implements MumbleEvent {
    }

    public record UserRemove(MumbleUser user, MumbleProto.UserRemove remove) implements MumbleEvent {
    }

    public record UserState(MumbleProto.UserState state) implements MumbleEvent {
    }

    public record UserChangedChannel(MumbleUser user, MumbleChannel from, MumbleChannel to) implements MumbleEvent {
    }

    public record BanList(MumbleProto.BanList banList) implements MumbleEvent {
    }

    public record TextMessage(MumbleProto.TextMessage message) implements MumbleEvent {
    }

    public record PermissionDenied(MumbleProto.PermissionDenied denial) implements MumbleEvent {
    }

    public record Acl(MumbleProto.ACL acl) implements MumbleEvent {
    }

    public record QueryUsers(MumbleProto.QueryUsers query) implements MumbleEvent {
    }

    public record CryptSetup(MumbleProto.CryptSetup cryptSetup) implements MumbleEvent {
    }

    public record ContextActionModify(MumbleProto.ContextActionModify modify) implements MumbleEvent {
    }

    public record UserList(MumbleProto.UserList userList) implements MumbleEvent {
    }

    public record PermissionQuery(MumbleProto.PermissionQuery query) implements MumbleEvent {
    }

    public record CodecVersion(MumbleProto.CodecVersion version) implements MumbleEvent {
    }

    public record UserStats(MumbleProto.UserStats stats) implements MumbleEvent {
    }

    public record ServerConfig(MumbleProto.ServerConfig config) implements MumbleEvent {
    }

    public record SuggestConfig(MumbleProto.SuggestConfig config) implements MumbleEvent {
    }

    public record PluginDataTransmission(MumbleProto.PluginDataTransmission transmission) implements MumbleEvent {
    }
}

