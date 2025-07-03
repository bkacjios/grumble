package gg.grumble.core.api;

import com.sun.jna.*;

import java.util.List;

public class MumblePluginLegacy2 extends Structure {
    public static class ByReference extends MumblePluginLegacy2 implements Structure.ByReference {}

    public MumblePluginLegacy2() {
        super();
    }

    public MumblePluginLegacy2(Pointer p) {
        super(p);
    }

    public int magic;
    public int version;
    public Callback trylock;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("magic", "version", "trylock");
    }
}
