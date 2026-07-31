package com.archetypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
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

	/** The scoreboard tag every spawned dummy wears — the cheap test the
	 * nameplate refresh does before it looks at anything else. */
	private static final String DUMMY_TAG = "archetypes_dummy";

	/** A player's pool, so a dummy dies to the same arithmetic a player would. */
	private static final double DUMMY_HEALTH = 40.0;

	/** How far in front of the caller the dummy lands. */
	private static final double DUMMY_DISTANCE = 3.0;

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
						// dummyCommand() deliberately NOT registered in shipped builds —
						// the user's pre-publish rule (2026-07-26). The implementation
						// stays for dev sessions: re-add the line to test.
						.then(traceCommand())));

		// The dummy's nameplate is its health readout, and this is the cheapest
		// honest way to keep it current: an event that fires exactly when the
		// number changed, filtered on a tag no other entity carries. Polling every
		// entity every tick to redraw a name would be a real cost paid by every
		// server that never spawns one.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, blocked, taken, blockedByShield) -> {
			if (entity.entityTags().contains(DUMMY_TAG)) {
				nameDummy(entity);
			}
		});
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

	// --- /archetypes dummy ---

	private static LiteralArgumentBuilder<CommandSourceStack> dummyCommand() {
		return Commands.literal("dummy").executes(context -> {
			ServerPlayer player = context.getSource().getPlayerOrException();
			ServerLevel level = player.level();
			Vindicator dummy = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.COMMAND);

			if (dummy == null) {
				context.getSource().sendFailure(Component.translatable(KEY + "dummy.failed"));
				return 0;
			}

			Vec3 look = player.getLookAngle();
			Vec3 spot = player.position().add(look.x * DUMMY_DISTANCE, 0.0, look.z * DUMMY_DISTANCE);
			dummy.snapTo(spot.x, spot.y, spot.z, player.getYRot() + 180.0F, 0.0F);
			dress(dummy, level);
			level.addFreshEntity(dummy);
			context.getSource().sendSuccess(
					() -> Component.translatable(KEY + "dummy.done"), false);
			return 1;
		});
	}

	/**
	 * The target: full netherite with Protection IV in all four slots, a player's
	 * forty-point health pool, immovable, and asleep.
	 *
	 * <h2>Why a vindicator</h2>
	 * Everything here has to be true of the dummy for the numbers to mean
	 * anything, and each one rules something out. It has to take damage through
	 * the ordinary {@code LivingEntity} pipeline, which is what an armor stand
	 * does not (it overrides {@code hurtServer} outright and skips armour
	 * absorption entirely). It has to not be undead: a zombie is immune to Venom's
	 * Poison and Blight's Wither and takes a Smite bonus it should not, so an
	 * Assassin build measured on one would read low on the coatings and high on
	 * the steel. It has to not burn at dawn, which rules the skeletons out and
	 * husks back in were they not undead too. It has to not convert, which rules
	 * out a piglin. And it has to render armour so the target LOOKS like what it
	 * is, which villagers do not. An illager is what is left: a humanoid,
	 * armour-wearing, living, sunlight-proof mob with no enchantment type
	 * attached to it.
	 *
	 * <p>Everything below goes through the Java API rather than an NBT string,
	 * because a string is not type-checked against 26.2 and a component that moved
	 * would fail silently on a dummy that then quietly ate the wrong damage.
	 */
	private static void dress(final Vindicator dummy, final ServerLevel level) {
		dummy.setItemSlot(EquipmentSlot.HEAD, protectedPiece(level, Items.NETHERITE_HELMET));
		dummy.setItemSlot(EquipmentSlot.CHEST, protectedPiece(level, Items.NETHERITE_CHESTPLATE));
		dummy.setItemSlot(EquipmentSlot.LEGS, protectedPiece(level, Items.NETHERITE_LEGGINGS));
		dummy.setItemSlot(EquipmentSlot.FEET, protectedPiece(level, Items.NETHERITE_BOOTS));

		// Nothing it wears may drop: a dummy that scatters Protection IV netherite
		// every time it dies re-arms whoever killed it.
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			dummy.setDropChance(slot, 0.0F);
		}

		setBase(dummy, Attributes.MAX_HEALTH, DUMMY_HEALTH);
		// Knockback resistance rather than a movement freeze, because being shoved
		// is being moved out of reach mid-combo, which changes what the next swing
		// is worth. 1.0 is total.
		setBase(dummy, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		dummy.setHealth((float) DUMMY_HEALTH);
		dummy.setNoAi(true);
		dummy.setPersistenceRequired();
		dummy.addTag(DUMMY_TAG);
		dummy.setCustomNameVisible(true);
		nameDummy(dummy);
	}

	/** One piece of netherite with Protection IV, the enchantment resolved off
	 * the level's registries — {@code Enchantments.PROTECTION} is only a key,
	 * because enchantments are datapack content. */
	private static ItemStack protectedPiece(final ServerLevel level, final Item item) {
		ItemStack stack = new ItemStack(item);
		Holder<Enchantment> protection = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
		stack.enchant(protection, 4);
		return stack;
	}

	private static void setBase(final LivingEntity entity, final Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			final double value) {
		AttributeInstance instance = entity.getAttribute(attribute);

		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/**
	 * The nameplate, which is the dummy's health readout. Mobs cannot use the
	 * scoreboard's {@code below_name} slot — vanilla renders that for players
	 * only — so the number has to live in the custom name itself and be
	 * rewritten when it changes.
	 */
	private static void nameDummy(final LivingEntity dummy) {
		dummy.setCustomName(Component.translatable(KEY + "dummy.name",
				String.format(java.util.Locale.ROOT, "%.1f", dummy.getHealth()),
				String.format(java.util.Locale.ROOT, "%.1f", dummy.getMaxHealth()))
						.withStyle(ChatFormatting.RED));
	}

	// --- /archetypes trace on|off ---

	private static LiteralArgumentBuilder<CommandSourceStack> traceCommand() {
		return Commands.literal("trace")
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
