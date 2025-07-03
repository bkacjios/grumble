package gg.grumble.core.api;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.List;

public class MumbleStringWrapper extends Structure {
    public Pointer data;
    public long size;
    public boolean needsReleasing;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("data", "size", "needsReleasing");
    }

    public String getString() {
        return data.getString(0);
    }

    public static class ByValue extends MumbleStringWrapper implements Structure.ByValue {}
}