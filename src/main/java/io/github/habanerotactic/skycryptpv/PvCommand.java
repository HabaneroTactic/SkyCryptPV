package io.github.habanerotactic.skycryptpv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class PvCommand {

	/** Runs after every other mod's client commands, so our /pv replaces theirs instead of merging with it. */
	private static final Identifier LATE_PHASE = Identifier.fromNamespaceAndPath(SkyCryptPV.MOD_ID, "late");
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final List<String> PROFILE_NAMES = List.of(
			"Apple", "Banana", "Blueberry", "Coconut", "Cucumber", "Grapes", "Kiwi", "Lemon", "Lime", "Mango",
			"Orange", "Papaya", "Peach", "Pear", "Pineapple", "Pomegranate", "Raspberry", "Strawberry",
			"Tomato", "Watermelon", "Zucchini"
	);

	private static final SuggestionProvider<FabricClientCommandSource> PLAYERS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(knownPlayers(), builder);
	private static final SuggestionProvider<FabricClientCommandSource> PROFILES =
			(ctx, builder) -> SharedSuggestionProvider.suggest(PROFILE_NAMES, builder);

	private PvCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
		ClientCommandRegistrationCallback.EVENT.register(LATE_PHASE, (dispatcher, buildContext) -> {
			register(dispatcher, "skycrypt");
			if (PvConfig.overridePv) {
				removeRootCommand(dispatcher, "pv");
				register(dispatcher, "pv");
			}
		});
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		LiteralArgumentBuilder<FabricClientCommandSource> command = literal(name)
				.executes(ctx -> run(ctx, PvConfig.defaultPlayer.isBlank() ? Minecraft.getInstance().getUser().getName() : PvConfig.defaultPlayer, null))
				.then(literal("settings").executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.schedule(() -> mc.gui.setScreen(new PvSettingsScreen(null)));
					return 1;
				}))
				.then(argument("player", StringArgumentType.word())
						.suggests(PLAYERS)
						.executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "player"), null))
						.then(argument("profile", StringArgumentType.word())
								.suggests(PROFILES)
								.executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "player"),
										StringArgumentType.getString(ctx, "profile")))));
		dispatcher.register(command);
	}

	private static int run(CommandContext<FabricClientCommandSource> ctx, String player, String profile) {
		SkyCryptPV.open(player, profile);
		return 1;
	}

	/**
	 * Names worth suggesting: real players in the tab list and nearby. Hypixel fills the tab list and
	 * world with fake entries (NPCs, tab columns) whose UUIDs are not version 4, so those are skipped.
	 */
	private static TreeSet<String> knownPlayers() {
		TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(PvConfig.favourites);
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				addIfReal(names, info.getProfile().id(), info.getProfile().name());
			}
		}
		if (mc.level != null) {
			for (Player player : mc.level.players()) {
				addIfReal(names, player.getUUID(), player.getGameProfile().name());
			}
		}
		return names;
	}

	public static boolean isKnownPlayer(String name) {
		return knownPlayers().contains(name);
	}

	private static void addIfReal(TreeSet<String> names, UUID id, String name) {
		if (id != null && id.version() == 4 && name != null && USERNAME.matcher(name).matches()) {
			names.add(name);
		}
	}

	/** Brigadier has no remove API, and merging two /pv trees would make the arguments ambiguous. */
	private static void removeRootCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		CommandNode<FabricClientCommandSource> root = dispatcher.getRoot();
		if (root.getChild(name) == null) {
			return;
		}
		try {
			for (String fieldName : new String[]{"children", "literals"}) {
				Field field = CommandNode.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				((Map<?, ?>) field.get(root)).remove(name);
			}
			SkyCryptPV.LOGGER.info("Replaced another mod's /{} command with SkyCrypt", name);
		} catch (ReflectiveOperationException | RuntimeException e) {
			SkyCryptPV.LOGGER.warn("Could not replace existing /{} command; use /skycrypt instead", name, e);
		}
	}

}
