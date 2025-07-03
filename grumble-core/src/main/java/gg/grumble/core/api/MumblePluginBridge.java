package gg.grumble.core.api;

import com.sun.jna.*;
import gg.grumble.core.client.MumbleClient;

import java.util.ArrayList;
import java.util.List;

public class MumblePluginBridge {
    private final MumbleClient client;
    private final MumbleAPI_v1_x_x api;
    private final long pluginId = 1; // can be any ID
    private MumblePluginModern plugin;

    private final List<MumblePluginLegacy> loadedLegacyPlugins = new ArrayList<>();
    private final List<MumblePluginLegacy2> loadedLegacyPlugins2 = new ArrayList<>();
    private final List<MumblePluginModern> loadedModernPlugins = new ArrayList<>();

    public MumblePluginBridge(MumbleClient client) {
        this.client = client;
        this.api = new MumbleAPI_v1_x_x();
        initAPI();
    }

    private void initAPI() {
        api.log = (callerID, message) -> {
            System.out.printf("[Plugin %d LOG] %s\n", callerID, message);
            return 0;
        };

        api.getActiveServerConnection = (callerID, connectionRef) -> {
            connectionRef.setValue(0); // only one connection
            return 0;
        };

        api.getAllUsers = (callerID, connection, outUsersRef, outCountRef) -> {
            var users = client.getUsers();
            int count = users.size();

            // Allocate memory for count pointers to user IDs
            Memory userArray = new Memory((long) Native.POINTER_SIZE * count);

            for (int i = 0; i < count; i++) {
                long session = users.get(i).getSession();
                Memory id = new Memory(Native.LONG_SIZE);
                id.setLong(0, session);
                userArray.setPointer((long) i * Native.POINTER_SIZE, id);
            }

            outUsersRef.setValue(userArray);

            // Fix: write the count as a pointer to int
            Memory countMem = new Memory(4); // assuming int is 4 bytes
            countMem.setInt(0, count);
            outCountRef.setValue(countMem);

            return 0;
        };

        api.getUserName = (callerID, connection, userID, outNameRef) -> {
            var user = client.getUser(userID);
            if (user == null) return 1;

            Memory name = new Memory(user.getName().length() + 1);
            name.setString(0, user.getName());
            outNameRef.setValue(name);
            return 0;
        };

        api.getLocalUserID = (callerID, connection, outUserIdRef) -> {
            var self = client.getSelf();
            if (self == null) return 1;

            outUserIdRef.setValue(self.getSession());
            return 0;
        };

        api.write();
    }

    public void loadPlugin(String dllPath) {
        NativeLibrary lib = NativeLibrary.getInstance(dllPath);

        try {
            if (hasFunction(lib, "getMumblePlugin")) {
                // Legacy plugin v1
                Function fn = lib.getFunction("getMumblePlugin");
                Pointer pluginPtr = fn.invokePointer(new Object[]{});
                if (pluginPtr == null) throw new RuntimeException("getMumblePlugin returned null");

                MumblePluginLegacy plugin = new MumblePluginLegacy(pluginPtr);
                plugin.read();

                System.out.println("Loaded legacy plugin: " + plugin.shortname.getWideString(0));
                loadedLegacyPlugins.add(plugin);
            } else if (hasFunction(lib, "getMumblePlugin2")) {
                // Legacy plugin v2
                Function fn = lib.getFunction("getMumblePlugin2");
                Pointer pluginPtr = fn.invokePointer(new Object[]{});
                if (pluginPtr == null) throw new RuntimeException("getMumblePlugin2 returned null");

                MumblePluginLegacy2 plugin = new MumblePluginLegacy2(pluginPtr);
                plugin.read();

                System.out.println("Loaded legacy v2 plugin: version " + plugin.version);
                loadedLegacyPlugins2.add(plugin);
            } else if (hasFunction(lib, "mumble_init")) {
                // Modern plugin
                MumblePluginModern plugin = Native.load(dllPath, MumblePluginModern.class);

                plugin.mumble_registerAPIFunctions(api.getPointer());

                int initResult = plugin.mumble_init(loadedModernPlugins.size());
                if (initResult != 0) {
                    throw new RuntimeException("Plugin init failed: " + initResult);
                }

                String name = plugin.mumble_getName().getString();
                System.out.println("Loaded modern plugin: " + name);
                loadedModernPlugins.add(plugin);
            } else {
                throw new RuntimeException("Unrecognized plugin format in: " + dllPath);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load plugin: " + dllPath, e);
        }
    }

    private boolean hasFunction(NativeLibrary lib, String name) {
        try {
            lib.getFunction(name);
            return true;
        } catch (UnsatisfiedLinkError | NoSuchMethodError e) {
            return false;
        }
    }

}
