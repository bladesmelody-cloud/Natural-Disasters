package com.bladesmelody.naturaldisasters.command;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterManager;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import com.bladesmelody.naturaldisasters.engine.RandomDisasters;
import com.bladesmelody.naturaldisasters.protect.SafeZones;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /disaster start <type> [magnitude] [pos]}, {@code stop [id]},
 * {@code list}, {@code random on|off|now|eruption},
 * {@code safezone add <name> <radius> [pos] | remove <name> | list}. Ops only.
 */
public final class DisasterCommand {
    private DisasterCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("disaster")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DisasterTypes.all().stream().map(DisasterType::name), b))
                                .executes(c -> start(c, 0, null))
                                .then(Commands.argument("magnitude", IntegerArgumentType.integer(1, 10))
                                        .executes(c -> start(c, IntegerArgumentType.getInteger(c, "magnitude"), null))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(c -> start(c, IntegerArgumentType.getInteger(c, "magnitude"),
                                                        BlockPosArgument.getLoadedBlockPos(c, "pos")))))))
                .then(Commands.literal("stop")
                        .executes(c -> stop(c, -1))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(c -> stop(c, IntegerArgumentType.getInteger(c, "id")))))
                .then(Commands.literal("list").executes(DisasterCommand::list))
                .then(Commands.literal("random")
                        .executes(DisasterCommand::randomStatus)
                        .then(Commands.literal("on").executes(c -> setRandom(c, true)))
                        .then(Commands.literal("off").executes(c -> setRandom(c, false)))
                        .then(Commands.literal("now").executes(DisasterCommand::randomNow))
                        .then(Commands.literal("eruption").executes(DisasterCommand::greatEruptionNow)))
                .then(Commands.literal("safezone")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10_000))
                                                .executes(c -> addSafeZone(c, BlockPos.containing(c.getSource().getPosition())))
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(c -> addSafeZone(c, BlockPosArgument.getBlockPos(c, "pos")))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                SafeZones.get(c.getSource().getServer()).zones().stream().map(SafeZones.Zone::name), b))
                                        .executes(DisasterCommand::removeSafeZone)))
                        .then(Commands.literal("list").executes(DisasterCommand::listSafeZones))));
    }

    private static int addSafeZone(CommandContext<CommandSourceStack> c, BlockPos pos) {
        String name = StringArgumentType.getString(c, "name");
        int radius = IntegerArgumentType.getInteger(c, "radius");
        ServerLevel level = c.getSource().getLevel();
        SafeZones.get(c.getSource().getServer()).put(new SafeZones.Zone(name, level.dimension().identifier(), pos.getX(), pos.getZ(), radius));
        c.getSource().sendSuccess(() -> Component.literal("Safe zone '" + name + "': radius " + radius + " around "
                + pos.getX() + ", " + pos.getZ() + " in " + level.dimension().identifier() + ". Disasters won't change blocks there."), true);
        return 1;
    }

    private static int removeSafeZone(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        if (!SafeZones.get(c.getSource().getServer()).remove(name)) {
            c.getSource().sendFailure(Component.literal("No safe zone named '" + name + "'."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Removed safe zone '" + name + "'."), true);
        return 1;
    }

    private static int listSafeZones(CommandContext<CommandSourceStack> c) {
        List<SafeZones.Zone> zones = SafeZones.get(c.getSource().getServer()).zones();
        c.getSource().sendSuccess(() -> Component.literal(zones.isEmpty() ? "No safe zones." : zones.size() + " safe zone(s):"), false);
        for (SafeZones.Zone z : zones) {
            c.getSource().sendSuccess(() -> Component.literal(" " + z.name() + ": radius " + z.radius() + " around "
                    + z.x() + ", " + z.z() + " in " + z.dimension()), false);
        }
        return zones.size();
    }

    private static int start(CommandContext<CommandSourceStack> c, int magnitude, BlockPos pos) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        String name = StringArgumentType.getString(c, "type");
        DisasterType<?> type = DisasterTypes.get(name).orElse(null);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown disaster: " + name));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (magnitude <= 0) magnitude = type.randomMagnitude(level.getRandom());
        BlockPos center = pos != null ? pos : BlockPos.containing(source.getPosition());
        if (pos == null && BlockWriter.isLoaded(level, center.atY(level.getMinY()))) {
            center = center.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()));
        }
        Disaster d = DisasterManager.start(level, type, center, magnitude, 0);
        BlockPos at = center;
        source.sendSuccess(() -> Component.literal("Started #" + d.id() + " " + d.describe() + " at " + at.toShortString()), true);
        return d.id();
    }

    private static int stop(CommandContext<CommandSourceStack> c, int id) {
        int n = DisasterManager.stop(c.getSource().getServer(), id);
        if (n == 0) {
            c.getSource().sendFailure(Component.literal(id < 0 ? "No active disasters." : "No disaster #" + id + "."));
        } else {
            c.getSource().sendSuccess(() -> Component.literal("Stopped " + n + " disaster" + (n == 1 ? "" : "s") + "."), true);
        }
        return n;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<DisasterManager.Running> all = DisasterManager.all(c.getSource().getServer());
        CommandSourceStack source = c.getSource();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active disasters."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(all.size() + " active (block budget " + DisasterManager.currentBudget() + "/tick):"), false);
        for (DisasterManager.Running r : all) {
            Disaster d = r.disaster();
            String where = d.center().toShortString() + " in " + r.level().dimension().identifier();
            source.sendSuccess(() -> Component.literal(" #" + d.id() + " " + d.describe() + " at " + where
                    + (d.inWarning() ? " (warning)" : "")), false);
        }
        return all.size();
    }

    private static int randomStatus(CommandContext<CommandSourceStack> c) {
        boolean on = Config.RANDOM_ENABLED.getAsBoolean();
        c.getSource().sendSuccess(() -> Component.literal("Random disasters are " + (on ? "on" : "off")
                + " (about every " + Config.RANDOM_INTERVAL_DAYS.getAsDouble() + " in-game days)."), false);
        return on ? 1 : 0;
    }

    private static int setRandom(CommandContext<CommandSourceStack> c, boolean on) {
        Config.RANDOM_ENABLED.set(on);
        Config.RANDOM_ENABLED.save();
        c.getSource().sendSuccess(() -> Component.literal("Random disasters " + (on ? "on" : "off") + "."), true);
        return 1;
    }

    private static int greatEruptionNow(CommandContext<CommandSourceStack> c) {
        return RandomDisasters.triggerGreatEruption(c.getSource().getServer(), Config.WARNING_SECONDS.getAsInt() * 20)
                .map(d -> {
                    c.getSource().sendSuccess(() -> Component.literal("Great eruption #" + d.id() + " " + d.describe()
                            + " at " + d.center().toShortString() + " (warning first)"), true);
                    return d.id();
                })
                .orElseGet(() -> {
                    c.getSource().sendFailure(Component.literal("No player in the Overworld."));
                    return 0;
                });
    }

    private static int randomNow(CommandContext<CommandSourceStack> c) {
        return RandomDisasters.trigger(c.getSource().getServer(), Config.WARNING_SECONDS.getAsInt() * 20)
                .map(d -> {
                    c.getSource().sendSuccess(() -> Component.literal("Random #" + d.id() + " " + d.describe()
                            + " at " + d.center().toShortString() + " (warning first)"), true);
                    return d.id();
                })
                .orElseGet(() -> {
                    c.getSource().sendFailure(Component.literal("No player or no disaster type available."));
                    return 0;
                });
    }
}
