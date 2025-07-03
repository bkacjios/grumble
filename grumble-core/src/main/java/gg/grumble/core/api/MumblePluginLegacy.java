package gg.grumble.core.api;

import com.sun.jna.*;

import java.util.List;

public class MumblePluginLegacy extends Structure {
    public static class ByReference extends MumblePluginLegacy implements Structure.ByReference {}

    public MumblePluginLegacy() {
        super();
    }

    public MumblePluginLegacy(Pointer p) {
        super(p);
    }

    public int magic;
    public Pointer description;
    public Pointer shortname;
    public Callback init;
    public Callback shutdown;
    public Callback trylock1;
    public Callback unlock;
    public Pointer longdesc;
    public Callback fetch;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("magic", "description", "shortname", "init", "shutdown", "trylock1", "unlock", "longdesc", "fetch");
    }
}
