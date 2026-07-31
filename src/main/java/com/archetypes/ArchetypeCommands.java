package com.archetypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /archetypes} — the developer test kit: reach a finished build in one
 * minute and watch what a blow is actually made of.
 *
 * <h2>Why it ships</h2>
 * The mod's numbers are only checkable by playing them. Nothing else in here
 * grants levels or points ({@code SkillTokenItem} is creative-only and gives a
 * fixed 1 or 60), nothing spawns a target worth measuring against, and nothing at all
 * shows the shaping chain — so a claim like "a fully built Cutpurse one-shots a
 * fully built Colossus" could be argued from source and never tested. It holds no
 * client-side state, and every mutation it performs goes through the same code
 * the real UI uses.
 *
 * <h2>Who can see it</h2>
 * Both of: permission level 2 ({@code Commands.LEVEL_GAMEMASTERS}, which is
 * literally {@code new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)}
 * — the 26.2 spelling of the old {@code hasPermission(2)}) AND a player in
 * CREATIVE ({@link #creativePlayer}). Brigadier consults {@code requires} both
 * when it builds the tree it sends a client and when it parses what that client
 * typed, so the pair hides the kit from a survival playthrough's tab-complete and
 * refuses it if typed anyway. The console has no game mode and is refused.
 *
 * <p>One thing the client-side half cannot promise: vanilla resends the command
 * tree on join and on op/deop ({@code PlayerList.sendPlayerPermissionLevel}) and
 * NOT on a game-mode change — {@code ServerPlayerGameMode.changeGameModeForPlayer}
 * broadcasts abilities and a player-info update, no {@code ClientboundCommandsPacket}
 * (read off the 26.2 bytecode). So the suggestion list a client is holding goes
 * stale until it rejoins. The server-side answer is exact either way, because it
 * re-parses against the live source.
 *
 * <h2>What it deliberately does not do</h2>
 * {@code buy} does not touch {@code PURCHASED} directly. It asks
 * {@link NodePurchases#check} and calls {@link NodePurchases#buy} node by node,
 * so a build assembled here is reachable by clicking — connectivity, the
 * exclusive capstones, the per-tree caps and both point pools all still hold. If
 * a tree fills short of its cap the command says how far it got, and that is a
 * finding about the tree, not a bug in the command.
 */
public final class ArchetypeCommands {
	/** {@code buy}'s wildcard: every tree of the caller's archetype, to its cap. */
	private static final String ALL = "all";

	private static final String KEY = "commands.archetypes.";

	/**
	 * Permission level 2, unchanged — {@code Commands.LEVEL_GAMEMASTERS} is the
	 * 26.2 spelling of the old {@code hasPermission(2)}. Its own constant only so
	 * the creative test below can be {@code and}ed onto it without the generic
	 * witness {@code Commands.<CommandSourceStack>hasPermission(...)} would
	 * otherwise need: assigning it here gives the inference its target type.
	 */
	private static final java.util.function.Predicate<CommandSourceStack> GAMEMASTER =
			Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

	private ArchetypeCommands() {
	}

	/**
	 * The second half of the gate: the caller must be a PLAYER, and that player
	 * must be in creative.
	 *
	 * <p>Two things it buys. The tree stops appearing in a survival player's
	 * tab-complete, so a test kit that grants levels and spawns armoured dummies
	 * is not sitting one keystroke away from an ordinary playthrough on a server
	 * where the author is opped. And the console stays out:
	 * {@code getPlayer()} is null for it, for a command block and for a function,
	 * none of which has a game mode to be in, and every branch below already
	 * needs a player ({@code getPlayerOrException}) — so refusing them once here
	 * is the same answer given earlier and in one place.
	 *
	 * <p>Both halves are required, not either: creative alone would hand the kit
	 * to any creative player on a server, and permission alone is what it was.
	 */
	private static boolean creativePlayer(final CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null && player.isCreative();
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
				dispatcher.register(Commands.literal("archetypes")
						.requires(GAMEMASTER.and(ArchetypeCommands::creativePlayer))
						.then(setCommand())
						.then(levelCommand())
						.then(buyCommand())
						// `trace` is registered only when the dev flag is set — see
						// DamageTrace.ENABLED. `dummy` is gone entirely (the user's
						// pre-publish rule, 2026-07-26); it lives in git history.
						.then(traceCommand())));
	}

	// --- /archetypes set <archetype> ---

	/**
	 * The existing reset path is creative-only and the pick path refuses to
	 * re-pick, so neither one alone can put a test account into a chosen
	 * archetype. This is those two, in order: {@link ModState#clear} (the
	 * {@code ResetArchetypePayload} handler's own call, which refunds every node
	 * and keeps banked levels) followed by {@link ModState#set} (the
	 * {@code PickArchetypePayload} handler's). No third way to mutate the
	 * attachment exists, and this does not add one.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> setCommand() {
		return Commands.literal("set")
				.then(Commands.argument("archetype", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(
								java.util.Arrays.stream(Archetype.values()).map(Archetype::id), builder))
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							String id = StringArgumentType.getString(context, "archetype");
							Archetype picked = Archetype.byId(id).orElse(null);

							if (picked == null) {
								context.getSource().sendFailure(
										Component.translatable(KEY + "set.unknown", id));
								return 0;
							}

							ModState.clear(player);
							ModState.set(player, picked);
							context.getSource().sendSuccess(() -> Component.translatable(
									KEY + "set.done",
									picked.tierName(SkillPoints.tier(player))), false);
							return 1;
						}));
	}

	// --- /archetypes level <n> ---

	/**
	 * One write of {@code ARCHETYPE_XP} to the curve's own cumulative cost for
	 * that level — see {@link SkillPoints#setLevel}. Not a loop of
	 * {@link SkillPoints#bank} calls, which would be scaled by the advancement
	 * multiplier and land somewhere near the level instead of on it.
	 *
	 * <p>Level 60 is the honest route to the epic trees: the epic pool is
	 * {@code level - 45} by definition, so fifteen epic points arrive because the
	 * player is level 60, not because anything here handed them over.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> levelCommand() {
		return Commands.literal("level")
				.then(Commands.argument("level",
						IntegerArgumentType.integer(0, SkillPoints.MAX_LEVEL))
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							int level = IntegerArgumentType.getInteger(context, "level");
							SkillPoints.setLevel(player, level);
							context.getSource().sendSuccess(() -> Component.translatable(
									KEY + "level.done", level,
									SkillPoints.available(player),
									SkillPoints.epicAvailable(player)), false);
							return level;
						}));
	}

	// --- /archetypes buy <subtree> [count] ---

	private static LiteralArgumentBuilder<CommandSourceStack> buyCommand() {
		return Commands.literal("buy")
				.then(Commands.argument("subtree", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(
								buyTargets(context.getSource().getPlayer()), builder))
						.executes(context -> buy(context.getSource(),
								StringArgumentType.getString(context, "subtree"),
								Integer.MAX_VALUE))
						.then(Commands.argument("count", IntegerArgumentType.integer(1))
								.executes(context -> buy(context.getSource(),
										StringArgumentType.getString(context, "subtree"),
										IntegerArgumentType.getInteger(context, "count")))));
	}

	/** The tree ids worth suggesting: the caller's own six, plus the wildcard. */
	private static List<String> buyTargets(final @Nullable ServerPlayer player) {
		List<String> names = new ArrayList<>();
		names.add(ALL);
		Archetype archetype = player == null ? null : ModState.get(player);

		if (archetype != null) {
			for (SubTree tree : treesOf(archetype)) {
				names.add(tree.id());
			}
		}

		return names;
	}

	/** A player's six trees: the three base ones and their epic siblings. */
	private static List<SubTree> treesOf(final Archetype archetype) {
		List<SubTree> trees = new ArrayList<>();

		for (SubTree tree : SubTree.of(archetype)) {
			trees.add(tree);
			SubTree epic = tree.epicCounterpart();

			if (epic != null) {
				trees.add(epic);
			}
		}

		return trees;
	}

	private static int buy(final CommandSourceStack source, final String name, final int count)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Archetype archetype = ModState.get(player);

		if (archetype == null) {
			source.sendFailure(Component.translatable(KEY + "buy.no_archetype"));
			return 0;
		}

		List<SubTree> trees;

		if (ALL.equalsIgnoreCase(name)) {
			trees = treesOf(archetype);
		} else {
			SubTree tree = SubTree.byId(name);

			if (tree == null || tree.archetype() != archetype) {
				source.sendFailure(Component.translatable(KEY + "buy.unknown", name));
				return 0;
			}

			trees = List.of(tree);
		}

		int bought = 0;

		for (SubTree tree : trees) {
			int inTree = fill(player, tree, count);
			bought += inTree;
			Set<Integer> owned = NodePurchases.owned(player, tree);
			int cap = tree.isEpic() ? SkillPoints.MAX_POINTS_PER_EPIC_SUB_TREE
					: SkillPoints.MAX_POINTS_PER_SUB_TREE;
			source.sendSuccess(() -> Component.translatable(KEY + "buy.tree",
					tree.displayName(), inTree, owned.size(), cap), false);
		}

		int total = bought;
		source.sendSuccess(() -> Component.translatable(KEY + "buy.done", total,
				SkillPoints.available(player), SkillPoints.epicAvailable(player)), false);
		return total;
	}

	/**
	 * Buy up to {@code limit} nodes in one tree, growing from the roots.
	 *
	 * <p>Every node costs one point, so "cheapest legal path" is only an ordering
	 * question, and the order chosen is bottom row first — the direction the trees
	 * are drawn to grow. Node <em>indices</em> run the other way (the grid is
	 * parsed top-down, so index 0 is the topmost cell), which is exactly why this
	 * sorts by row rather than trusting index order: buying by index would climb
	 * down from whichever high node happened to become legal first and produce
	 * builds no player would ever assemble.
	 *
	 * <p>The legality test is {@link NodePurchases#check} and the write is
	 * {@link NodePurchases#buy}, once per node, re-checked each pass — so a
	 * capstone that locks its exclusive twin locks it here too, and the loop ends
	 * the moment nothing is buyable rather than assuming the cap is reachable.
	 */
	private static int fill(final ServerPlayer player, final SubTree tree, final int limit) {
		List<Integer> order = new ArrayList<>();
		Constellation shape = tree.constellation();

		for (int index = 0; index < shape.nodes().size(); index++) {
			order.add(index);
		}

		order.sort(Comparator
				.comparingInt((Integer index) -> shape.nodes().get(index).row())
				.thenComparingInt(index -> shape.nodes().get(index).col()));

		int bought = 0;

		while (bought < limit) {
			int next = -1;

			for (int index : order) {
				if (NodePurchases.check(player, tree, index) == NodePurchases.Verdict.BUYABLE) {
					next = index;
					break;
				}
			}

			if (next < 0 || !NodePurchases.buy(player, tree, next)) {
				break;
			}

			bought++;
		}

		return bought;
	}

	// --- /archetypes trace on|off ---

	private static LiteralArgumentBuilder<CommandSourceStack> traceCommand() {
		return Commands.literal("trace")
				// The dev flag, on this node only: without it the tracer records
				// nothing, so the branch would answer every question with an empty
				// report. It stays out of tab-complete instead.
				.requires(source -> DamageTrace.ENABLED)
				.then(Commands.literal("on").executes(context -> trace(context.getSource(), true)))
				.then(Commands.literal("off").executes(context -> trace(context.getSource(), false)));
	}

	private static int trace(final CommandSourceStack source, final boolean on)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DamageTrace.watch(player, on);
		source.sendSuccess(() -> Component.translatable(KEY + "trace." + (on ? "on" : "off")), false);
		return on ? 1 : 0;
	}
}
