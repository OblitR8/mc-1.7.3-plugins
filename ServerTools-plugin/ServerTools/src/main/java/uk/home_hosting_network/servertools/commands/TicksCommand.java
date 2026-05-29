package uk.home_hosting_network.servertools.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import uk.home_hosting_network.servertools.ServerTools;
import uk.home_hosting_network.servertools.util.ColorUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * /ticks — Displays server TPS via reflection against net.minecraft.server.MinecraftServer.
 *
 * Poseidon's TPS tracking lives in MinecraftServer.recentTps (a double[3]).
 * We reach it by:
 *   1. CraftServer  → getHandle() → MinecraftServer instance
 *   2. MinecraftServer.recentTps field  (double[3]: 1m, 5m, 15m)
 *
 * Fallback: if recentTps isn't found we scan all double[] fields and pick the first one,
 * then list everything found to the console so you can identify the right name.
 */
public class TicksCommand implements CommandExecutor {

    private static final double TARGET_TPS = 20.0;

    private final ServerTools plugin;

    public TicksCommand(ServerTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ColorUtil.header("Server TPS  (target: 20.0)"));

        double[] tps = getTps();

        if (tps == null) {
            sender.sendMessage(ChatColor.RED + "TPS data is unavailable. Check the server console for details.");
            return true;
        }

        if (tps.length >= 3) {
            sender.sendMessage(
                " " + ChatColor.YELLOW + "1m:  " + fmt(tps[0]) +
                ChatColor.GRAY  + "  |  " +
                ChatColor.YELLOW + "5m:  " + fmt(tps[1]) +
                ChatColor.GRAY  + "  |  " +
                ChatColor.YELLOW + "15m: " + fmt(tps[2])
            );
        } else {
            sender.sendMessage(ChatColor.YELLOW + "TPS: " + fmt(tps[0]));
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // TPS retrieval — three strategies tried in order
    // -----------------------------------------------------------------------

    private double[] getTps() {
        // Strategy 1: CraftServer.getHandle() → MinecraftServer → recentTps field
        double[] result = tryMinecraftServerField();
        if (result != null) return result;

        // Strategy 2: CraftServer.getTPS() method (Poseidon adds this directly)
        result = tryCraftServerMethod("getTPS");
        if (result != null) return result;

        // Strategy 3: CraftServer.getRecentTps() (alternative name used in some builds)
        result = tryCraftServerMethod("getRecentTps");
        if (result != null) return result;

        // Nothing worked — dump available info so the server owner can report it
        dumpDebugInfo();
        return null;
    }

    /**
     * Reach MinecraftServer via CraftServer.getHandle(), then read the recentTps double[] field.
     */
    private double[] tryMinecraftServerField() {
        try {
            Object craftServer = plugin.getServer();

            // CraftServer.getHandle() returns the NMS MinecraftServer
            Method getHandle = craftServer.getClass().getMethod("getHandle");
            Object minecraftServer = getHandle.invoke(craftServer);

            // Try field named "recentTps" first (standard Poseidon name)
            for (String fieldName : new String[]{"recentTps", "recentTickTimes", "tpsHistory"}) {
                try {
                    Field f = getField(minecraftServer.getClass(), fieldName);
                    if (f != null) {
                        f.setAccessible(true);
                        Object val = f.get(minecraftServer);
                        if (val instanceof double[]) return (double[]) val;
                    }
                } catch (Exception ignored) {}
            }

            // Fallback: scan all double[] fields in MinecraftServer and return the first one
            return scanForDoubleArrayField(minecraftServer);

        } catch (Exception e) {
            plugin.log().warning("[ServerTools] tryMinecraftServerField failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Call a method by name directly on CraftServer.
     */
    private double[] tryCraftServerMethod(String methodName) {
        try {
            Object craftServer = plugin.getServer();
            Method m = craftServer.getClass().getMethod(methodName);
            Object result = m.invoke(craftServer);
            if (result instanceof double[]) return (double[]) result;
        } catch (NoSuchMethodException ignored) {
            // method doesn't exist in this build — normal
        } catch (Exception e) {
            plugin.log().warning("[ServerTools] " + methodName + "() failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Walk up the class hierarchy to find a field by name.
     */
    private Field getField(Class<?> clazz, String name) {
        while (clazz != null) {
            try { return clazz.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Scan all fields in the class hierarchy for a double[].
     * Returns the first double[] that looks like TPS data (length >= 1, values > 0 and <= 25).
     */
    private double[] scanForDoubleArrayField(Object obj) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == double[].class) {
                    try {
                        f.setAccessible(true);
                        double[] arr = (double[]) f.get(obj);
                        if (arr != null && arr.length >= 1 && arr[0] > 0 && arr[0] <= 25) {
                            plugin.log().info("[ServerTools] Found TPS via field: "
                                + clazz.getSimpleName() + "." + f.getName());
                            return arr;
                        }
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Log all methods and double[] fields on CraftServer and its NMS server
     * to help diagnose missing TPS access.
     */
    private void dumpDebugInfo() {
        plugin.log().warning("[ServerTools] /ticks: could not find TPS data. Dumping debug info:");
        try {
            Object craftServer = plugin.getServer();
            plugin.log().warning("[ServerTools] CraftServer class: " + craftServer.getClass().getName());

            // List methods returning double[]
            for (Method m : craftServer.getClass().getMethods()) {
                if (m.getReturnType() == double[].class) {
                    plugin.log().warning("[ServerTools]   CraftServer method returning double[]: " + m.getName());
                }
            }

            // Try to get MinecraftServer and list its double[] fields
            try {
                Method getHandle = craftServer.getClass().getMethod("getHandle");
                Object nmsServer = getHandle.invoke(craftServer);
                plugin.log().warning("[ServerTools] NMS server class: " + nmsServer.getClass().getName());
                Class<?> c = nmsServer.getClass();
                while (c != null) {
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType() == double[].class) {
                            plugin.log().warning("[ServerTools]   double[] field: "
                                + c.getSimpleName() + "." + f.getName());
                        }
                    }
                    c = c.getSuperclass();
                }
            } catch (Exception e2) {
                plugin.log().warning("[ServerTools] getHandle() failed: " + e2.getMessage());
            }
        } catch (Exception e) {
            plugin.log().warning("[ServerTools] Debug dump failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------

    private String fmt(double tps) {
        double capped = Math.min(tps, TARGET_TPS);
        String s = String.format("%.2f", capped);
        if (capped >= 18.0) return ChatColor.GREEN  + s;
        if (capped >= 15.0) return ChatColor.YELLOW + s;
        return ChatColor.RED + s;
    }
}
