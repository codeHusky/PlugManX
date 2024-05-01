package com.rylinaux.plugman.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class BukkitCommandWrap {
    private final boolean craftBukkitVersioning;
    private final boolean nmsVersioning;
    private String nmsVersion;

    private Field bField;
    private Method removeCommandMethod;
    private Class minecraftServerClass;
    private Method aMethod;
    private Method getServerMethod;
    private Field vanillaCommandDispatcherField;
    private Method getCommandDispatcherMethod;
    private Method registerMethod;
    private Method syncCommandsMethod;
    private Constructor bukkitcommandWrapperConstructor;

    public BukkitCommandWrap() {
        String prefix = cbPrefix();
        String[] packageParts = prefix.split("\\.");
        String versionPart = packageParts[packageParts.length - 1];
        this.craftBukkitVersioning = versionPart.startsWith("v1_");
        if (this.craftBukkitVersioning) {
            this.nmsVersion = versionPart;
        }

        boolean nmsVers = true;
        try {
            Class.forName("net.minecraft.server.MinecraftServer");
            nmsVers = false;
        } catch (ClassNotFoundException ignore) {}
        this.nmsVersioning = nmsVers;
    }

    private static @NotNull String cbPrefix() {
        return Bukkit.getServer().getClass().getPackage().getName();
    }

    public static @NotNull String cbPrefix(String cbClassName) {
        return cbPrefix() + "." + cbClassName;
    }

    private @NotNull String nmsPrefix(String nmsClassName) {
        if (nmsVersioning) return "net.minecraft.server." + this.nmsVersion + "." + nmsClassName;
        return "net.minecraft.server." + nmsClassName;
    }

    public void wrap(Command command, String alias) {
        if (this.nmsVersion == null) return;

        // Get the MinecraftServer class
        if (!resolveMcServerClass());

        // getServer method (nms server)
        if (!resolveGetServerMethod()) return;

        // Get nms server instance
        Object minecraftServer = getServerInstance();

        // Get vanillaCommandDispatcher field
        if (!resolveVanillaCommandDispatcherField()) return;

        // Get the vanilla command dispatcher instance
        Object commandDispatcher = getCommandDispatcher(minecraftServer);
        if (commandDispatcher == null) return;

        // Get the b field
        if (!resolveBField()) return;

        // Get the a method
        if (!resolveAMethod(commandDispatcher)) return;

        // Get the BukkitCommandWrapper constructor
        if (!resolveBukkitCmdWrapperConstructor()) return;

        Object commandWrapper = getCommandWrapper(command);
        if (commandWrapper == null) return;

        Object aInstance = getAInstance(commandDispatcher);
        if (aInstance == null) return;

        if (!resolveRegisterCmdMethod()) return;

        try {
            this.registerMethod.invoke(commandWrapper, aInstance, alias);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void sync() {
        if (this.syncCommandsMethod == null) try {
            this.syncCommandsMethod = Class.forName(cbPrefix("CraftServer")).getDeclaredMethod("syncCommands");
            this.syncCommandsMethod.setAccessible(true);

            if (Bukkit.getOnlinePlayers().size() >= 1)
                for (Player player : Bukkit.getOnlinePlayers())
                    player.updateCommands();
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        try {
            this.syncCommandsMethod.invoke(Bukkit.getServer());
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void unwrap(String command) {
        if (this.nmsVersion == null) return;

        if (!resolveMcServerClass()) return;

        if (!resolveGetServerMethod()) return;
        Object server = getServerInstance();

        if (!resolveVanillaCommandDispatcherField()) return;
        Object commandDispatcher = getCommandDispatcher(server);

        if (!resolveBField()) return;

        CommandDispatcher b = getDispatcher(commandDispatcher);
        if (b == null) return;

        if (resolveRemoveCommandMethod()) return;

        try {
            this.removeCommandMethod.invoke(b.getRoot(), command);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private boolean resolveRemoveCommandMethod() {
        if (this.removeCommandMethod == null) try {
            try {
                this.removeCommandMethod = RootCommandNode.class.getDeclaredMethod("removeCommand", String.class);
            } catch (NoSuchMethodException | NoSuchMethodError ex) {
                this.removeCommandMethod = CommandNode.class.getDeclaredMethod("removeCommand", String.class);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return true;
        }
        return false;
    }

    private @Nullable CommandDispatcher getDispatcher(Object commandDispatcher) {
        CommandDispatcher b = null;
        try {
            b = (CommandDispatcher) this.bField.get(commandDispatcher);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return b;
    }

    private @Nullable Object getCommandDispatcher(Object server) {
        Object commandDispatcher = null;
        try {
            commandDispatcher = this.vanillaCommandDispatcherField.get(server);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return commandDispatcher;
    }

    private Object getServerInstance() {
        Object server = null;
        try {
            server = this.getServerMethod.invoke(this.minecraftServerClass);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return server;
    }

    private boolean resolveMcServerClass() {
        if (this.minecraftServerClass == null) {
            try {
                this.minecraftServerClass = Class.forName(nmsPrefix("MinecraftServer"));
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private boolean resolveGetServerMethod() {
        if (this.getServerMethod == null) {
            try {
                this.getServerMethod = this.minecraftServerClass.getMethod("getServer");
                this.getServerMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private boolean resolveVanillaCommandDispatcherField() {
        if (this.vanillaCommandDispatcherField == null) {
            try {
                this.vanillaCommandDispatcherField = this.minecraftServerClass.getDeclaredField("vanillaCommandDispatcher");
                this.vanillaCommandDispatcherField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private boolean resolveBField() {
        if (this.bField == null) {
            try {
                this.bField = Class.forName(nmsPrefix("CommandDispatcher")).getDeclaredField("b");
                this.bField.setAccessible(true);
            } catch (NoSuchFieldException | ClassNotFoundException e) {
                if (this.bField == null) {
                    try {
                        Class<?> clazz = Class.forName("net.minecraft.commands.CommandDispatcher");
                        Field gField = clazz.getDeclaredField("g");
                        if (gField.getType() == com.mojang.brigadier.CommandDispatcher.class) {
                            this.bField = gField;
                        } else {
                            this.bField = clazz.getDeclaredField("h");
                        }
                        this.bField.setAccessible(true);
                    } catch (NoSuchFieldException | ClassNotFoundException ex) {
                        ex.addSuppressed(e);
                        e.printStackTrace();
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean resolveRegisterCmdMethod() {
        if (this.registerMethod == null) try {
            this.registerMethod = Class.forName(cbPrefix("command.BukkitCommandWrapper"))
                    .getMethod("register", CommandDispatcher.class, String.class);
            this.registerMethod.setAccessible(true);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private Object getAInstance(Object commandDispatcher) {
        Object a = null;
        try {
            a = this.aMethod.invoke(commandDispatcher);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return a;
    }

    private @Nullable Object getCommandWrapper(Command command) {
        Object commandWrapper;

        try {
            commandWrapper = this.bukkitcommandWrapperConstructor.newInstance(Bukkit.getServer(), command);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
        return commandWrapper;
    }

    private boolean resolveBukkitCmdWrapperConstructor() {
        if (this.bukkitcommandWrapperConstructor == null) {
            try {
                this.bukkitcommandWrapperConstructor = Class.forName(cbPrefix("command.BukkitCommandWrapper")).getDeclaredConstructor(Class.forName("org.bukkit.craftbukkit." + this.nmsVersion + ".CraftServer"), Command.class);
                this.bukkitcommandWrapperConstructor.setAccessible(true);
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private boolean resolveAMethod(Object commandDispatcher) {
        if (this.aMethod == null) try {
            this.aMethod = commandDispatcher.getClass().getDeclaredMethod("a");
            this.aMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
