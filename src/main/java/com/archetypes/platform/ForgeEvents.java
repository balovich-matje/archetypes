package com.archetypes.platform;

import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;

/**
 * The LexForge half of the event seam for the {@code 1.20.1-forge} node: one small
 * method per fabric-api registration that the shared tree forks on. Every listener body
 * those files pass in stays shared — only the registration line crosses into this file,
 * which is why this file names LexForge API and nothing outside
 * {@code com.archetypes.platform} does (Skill Proficiencies' conventions §5g).
 *
 * <p><b>THE CONTRACT EACH METHOD OWES ITS CALLER is written at the call site</b>, and
 * R-20 is why: a re-rooted event has to reproduce the original's CONTRACT, not merely
 * fire somewhere plausible. What follows is that contract checked one method at a time
 * against {@code forge-1.20.1-47.4.22-sources.jar} — specifically its {@code patches/}
 * tree, which is the only place a Forge event's FIRING SITE can be read.
 *
 * <p><b>Five of these are Skill Proficiencies' own, reused unchanged</b> —
 * {@link #playerJoin}, {@link #endServerTick}, {@link #creativeTabOutput},
 * {@link #registerCommands} and (as a widened three-argument form)
 * {@code registerCommands}. Nine are new here and have no precedent next door.
 *
 * <p><b>{@code afterDamage} is deliberately absent, and that is not an omission.</b>
 * {@link LegacyDamageEvents} is a {@code <1.20.5} whole-file compilation unit, so this
 * node already has it, and the shared
 * {@code mixin/LivingEntityMixin.archetypes$afterDamage} already fires it from
 * {@code hurt(DamageSource,F)Z}. Adding a {@code LivingHurtEvent} listener on top would
 * run every consumer twice. Conventions settle which wins: "a mixin whose target
 * resolves on the platform stays a mixin there, even when the platform offers a tidier
 * event".
 */
public final class ForgeEvents {
	private ForgeEvents() {
	}

	/**
	 * Fires once the joining player's connection can receive packets — which is
	 * MANDATORY on this node rather than merely tidy, because
	 * {@code ArchetypeStore.resyncAll} sends the client its entire view of its own tree,
	 * HUD and cooldowns and this node has no other sync at all.
	 *
	 * <p>{@code patches/net/minecraft/server/players/PlayerList.java.patch} puts
	 * {@code firePlayerLoggedIn(player)} at the very END of {@code placeNewPlayer},
	 * after the {@code ServerGamePacketListenerImpl} is installed and after the
	 * login/difficulty/abilities/recipes/tags packets have already been written to it.
	 * So the channel is open — the same guarantee fabric-api's
	 * {@code ServerPlayConnectionEvents.JOIN} gives.
	 */
	public static void playerJoin(final Consumer<ServerPlayer> listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * Fires once per server tick, at the END of it.
	 *
	 * <p>{@code TickEvent.ServerTickEvent} carries a {@code public final Phase phase}
	 * and a {@code MinecraftServer getServer()}; the {@code Phase.END} filter is what
	 * makes this {@code ServerTickEvents.END_SERVER_TICK} rather than
	 * {@code START_SERVER_TICK}. Without it all fourteen tickers would run twice per
	 * tick — which for the cooldown, stance and bleed clocks is a doubled rate, not a
	 * doubled log line.
	 */
	public static void endServerTick(final Consumer<MinecraftServer> listener) {
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				listener.accept(event.getServer());
			}
		});
	}

	/**
	 * A boolean veto on a death: {@code false} means <b>THE ENTITY SURVIVES AT ITS
	 * CURRENT HEALTH</b>, not that the damage was voided.
	 *
	 * <p>That sentence is the whole reason this method has a javadoc.
	 * {@code AgilityCombat}'s Last Shadow is a cheat-death: it heals to 1, cleanses,
	 * stamps an immunity window and then returns {@code false}. A helper that mapped
	 * {@code false} onto "cancel the damage" would hand out an immortality bug rather
	 * than a lost proc.
	 *
	 * <p>{@code patches/net/minecraft/world/entity/LivingEntity.java.patch} shows
	 * {@code if (ForgeHooks.onLivingDeath(this, source)) return;} as the FIRST statement
	 * of {@code die(DamageSource)} — ahead of vanilla's own
	 * {@code !isRemoved() && !dead} guard — and {@code onLivingDeath} returns
	 * {@code EVENT_BUS.post(new LivingDeathEvent(...))}, i.e. true when cancelled. So a
	 * cancelled event skips the whole of {@code die} and the entity keeps whatever
	 * health the listener left it with. That is exactly
	 * {@code ServerLivingEntityEvents.ALLOW_DEATH}, whose fabric-api implementation
	 * cancels the same method.
	 *
	 * <p>Server-side only, because {@code hurt(DamageSource,F)Z} runs on both logical
	 * sides on this Minecraft version and can therefore reach {@code die} on a client
	 * level. fabric-api's event is in {@code ServerLivingEntityEvents} and never sees
	 * that call; this filter is what makes the two the same event.
	 *
	 * <p><b>THE THIRD PARAMETER IS NOT AVAILABLE ON THIS LOADER AND IS PASSED AS ZERO.</b>
	 * fabric-api's event carries the killing damage amount; {@code LivingDeathEvent}
	 * carries the source and nothing else, and there is no honest way to recover the
	 * number at the head of {@code die}. Both shared listeners ignore it — {@code
	 * AgilityCombat}'s Last Shadow and {@code Archetypes}' channel teardown each name
	 * the parameter and never read it — so nothing changes behaviour. Zero is passed
	 * rather than {@code getHealth()} or the last-hurt amount precisely because a
	 * plausible-looking wrong number is the failure R-20 exists to catch: the next
	 * listener that DOES read it must find an obviously absent value, not a lie.
	 */
	public static void allowDeath(final AllowDeathListener listener) {
		MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
			if (event.getEntity().level().isClientSide()) {
				return;
			}

			if (!listener.allowDeath(event.getEntity(), event.getSource(), 0.0F)) {
				event.setCanceled(true);
			}
		});
	}

	/**
	 * Fires after a death has become final, server-side, with the entity and the
	 * {@code DamageSource}. Supports several listeners and keeps them in registration
	 * order — {@code AgilityCombat} registers two on purpose.
	 *
	 * <p><b>THIS IS A RE-ROOTING, AND HERE IS EXACTLY WHAT MOVED.</b> LexForge 1.20.1
	 * has no post-death event: {@code LivingDeathEvent} is the only one, and the patch
	 * quoted under {@link #allowDeath} posts it at the HEAD of {@code die} rather than
	 * the TAIL, which is where fabric-api's {@code AFTER_DEATH} injects. Two devices
	 * close the gap:
	 *
	 * <ul>
	 * <li>{@link EventPriority#LOWEST}, so every other listener — including this mod's
	 *     own {@link #allowDeath} arm at the default priority — has already voted.</li>
	 * <li>The default {@code receiveCancelled = false}, so a cancelled death never
	 *     reaches these listeners at all. That is the "post must not fire when the death
	 *     was cancelled" half of the contract, and it is enforced by the bus rather than
	 *     by a flag of ours.</li>
	 * </ul>
	 *
	 * <p>What remains different: this fires before drops, before XP and while
	 * {@code dead} is still false. None of the three shared listeners reads any of
	 * those — {@code DeathMark.onDeath} clears a flag off the victim,
	 * {@code AgilityCombat}'s pair and {@code SlayerCombat}'s one all react to the
	 * KILLER — so the observable behaviour is the same. Anything added later that reads
	 * the victim's drops or its {@code dead} flag would not be, and that is why this is
	 * written down.
	 *
	 * <p>Server-side filter for the same reason as {@link #allowDeath}.
	 */
	public static void afterDeath(final AfterDeathListener listener) {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
			if (event.getEntity().level().isClientSide()) {
				return;
			}

			listener.afterDeath(event.getEntity(), event.getSource());
		});
	}

	/**
	 * Fires once per entity added to a SERVER level, with the entity and that level.
	 *
	 * <p>{@code EntityJoinLevelEvent} is posted from
	 * {@code patches/net/minecraft/world/level/Level.java.patch}'s
	 * {@code addFreshEntity}/{@code ServerLevel.addEntity} path for every entity
	 * entering any level, client levels included, so the {@code isClientSide} filter is
	 * what makes it {@code ServerEntityEvents.ENTITY_LOAD}.
	 *
	 * <p><b>The {@code tickCount > 0} filter stays in the shared body</b> and is not
	 * quietly replaced with {@code !event.loadedFromDisk()}. The two are not the same
	 * question — a dimension-hopped arrow was not loaded from disk either — and a helper
	 * that fired only for genuinely new entities would be a DIFFERENT event, which would
	 * silently stop the caller's own filter from meaning anything (R-20).
	 */
	public static void entityLoad(final EntityLoadListener listener) {
		MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				listener.entityLoad(event.getEntity(), level);
			}
		});
	}

	/**
	 * The server is going down and still owns its schedule.
	 *
	 * <p>{@code ServerStoppingEvent} and {@link #serverStopped} are genuinely different
	 * events and the two call sites want different ones:
	 * {@code OracleStrikes.PENDING.clear()} needs STOPPING, while the bleed list needs
	 * STOPPED. Both are posted from {@code ServerLifecycleHooks} — stopping before
	 * {@code MinecraftServer.stopServer} runs, stopped after it has returned.
	 */
	public static void serverStopping(final Consumer<MinecraftServer> listener) {
		MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
				listener.accept(event.getServer()));
	}

	/** The server is down. See {@link #serverStopping} for why both exist. */
	public static void serverStopped(final Consumer<MinecraftServer> listener) {
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
				listener.accept(event.getServer()));
	}

	/**
	 * A DENY predicate on a right-click with an item: {@code true} cancels the
	 * interaction for that hand and nothing else.
	 *
	 * <p>The sense is inverted from the Fabric arm on purpose and the seam's own comment
	 * says so: {@code UseItemCallback} returns a result, while
	 * {@code PlayerInteractEvent.RightClickItem} is {@code @Cancelable} and has no
	 * result to return. {@code setCanceled(true)} is what {@code InteractionResult.FAIL}
	 * means there — the item's {@code use} is skipped and the other hand is untouched.
	 *
	 * <p>Not side-filtered, exactly as the Fabric arm is not: the greatsword's two-hand
	 * lock has to hold on the client too, or the client predicts a use the server
	 * refuses.
	 */
	public static void denyUseItem(final DenyUseListener listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
			if (listener.deny(event.getEntity(), event.getHand())) {
				event.setCanceled(true);
			}
		});
	}

	/** The block half of {@link #denyUseItem}, same contract. */
	public static void denyUseBlock(final DenyUseListener listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			if (listener.deny(event.getEntity(), event.getHand())) {
				event.setCanceled(true);
			}
		});
	}

	/**
	 * Registers this mod's brewing mixes, once, during the loader's own window.
	 *
	 * <p>The recipe TABLE is not here — it is {@code ManaPotions.brew} and
	 * {@code AmnesiaPotions.brew}, one copy for the whole loader axis, handed over
	 * through {@link BrewingSink}. That is the point of {@code BrewingSink} existing at
	 * all: a fifth and sixth transcription of the same six mixes is exactly the silent
	 * content drift R-20 is about.
	 *
	 * <p><b>Why a hand-written {@link IBrewingRecipe} and not
	 * {@code BrewingRecipeRegistry.addRecipe(Ingredient, Ingredient, ItemStack)}.</b>
	 * MEASURED in {@code Ingredient.java}: {@code Ingredient.test} compares
	 * {@code stack.is(item)} and ignores NBT, and on 1.20.1 a potion's identity lives
	 * entirely in NBT. An {@code Ingredient.of(awkwardPotionStack)} therefore matches
	 * EVERY potion, so "awkward + lapis" would turn any potion in the stand into Mana
	 * Restore. {@link Mix} tests {@code PotionUtils.getPotion} instead, which is the
	 * same comparison vanilla's own {@code PotionBrewing.Mix} makes.
	 *
	 * <p>The output keeps the input's CONTAINER item, so the splash and lingering forms
	 * come free from vanilla's gunpowder and dragon's-breath mixes — the same thing
	 * {@code registerPotionRecipe} gives on every Fabric node.
	 *
	 * <p>Deferred to {@code FMLCommonSetupEvent.enqueueWork} because
	 * {@code BrewingRecipeRegistry.recipes} is a plain {@code ArrayList} written from
	 * mod code and read from the game thread, and common setup runs mods in parallel.
	 * {@code enqueueWork} is what puts every mod's additions on one thread. It is also
	 * comfortably before any brewing stand can tick.
	 */
	public static void brewingRecipes(final Consumer<BrewingSink> table) {
		ArchetypesForge.modEventBus().addListener((FMLCommonSetupEvent event) ->
				event.enqueueWork(() -> table.accept(
						(from, ingredient, to) -> BrewingRecipeRegistry.addRecipe(
								new Mix(from.value(), ingredient, to.value())))));
	}

	/**
	 * Adds entries to one vanilla creative tab.
	 *
	 * <p>{@code BuildCreativeModeTabContentsEvent implements CreativeModeTab.Output},
	 * which is the whole reason the shared {@code output.accept(...)} lines stay outside
	 * the conditional on this node exactly as they do on Fabric. It also
	 * {@code implements IModBusEvent}, so it goes on the MOD bus, and
	 * {@code getTabKey()} is the filter that replaces fabric-api's per-tab event object.
	 */
	public static void creativeTabOutput(final ResourceKey<CreativeModeTab> tab,
			final Consumer<CreativeModeTab.Output> filler) {
		ArchetypesForge.modEventBus().addListener((BuildCreativeModeTabContentsEvent event) -> {
			if (event.getTabKey() == tab) {
				filler.accept(event);
			}
		});
	}

	/**
	 * A builder for this mod's OWN creative tab, with no {@code (Row, column)}
	 * arguments.
	 *
	 * <p>Both fabric-api helpers exist because vanilla's only builder factory takes a
	 * row and a column, which are meaningless for a tab that is not one of vanilla's.
	 * The shared call site's comment says both loaders are BELIEVED to patch a no-arg
	 * {@code CreativeModeTab.builder()} in for the same reason; on this one that is now
	 * MEASURED rather than believed —
	 * {@code patches/net/minecraft/world/item/CreativeModeTab.java.patch} adds
	 * {@code public static CreativeModeTab.Builder builder()} returning
	 * {@code new Builder(Row.TOP, 0)} and deprecates the two-argument original. So this
	 * really is a one-line helper, and every line of the title/icon/{@code displayItems}
	 * chain after it stays shared.
	 */
	public static CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	/**
	 * Hands over the three things {@code CommandRegistrationCallback} passes — the
	 * dispatcher, the registry access and the registration environment — in that order,
	 * so the shared lambda infers them and needs no change.
	 *
	 * <p>{@code RegisterCommandsEvent} exposes exactly those three
	 * ({@code getDispatcher()}, {@code getBuildContext()}, {@code getCommandSelection()})
	 * and is posted from {@code Commands}' constructor, i.e. once per server before any
	 * command is parsed. Game bus.
	 */
	public static void registerCommands(final CommandRegistrar listener) {
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
				listener.register(event.getDispatcher(), event.getBuildContext(),
						event.getCommandSelection()));
	}

	/**
	 * One potion mix, matched the way vanilla matches its own: by the potion in the
	 * input's NBT rather than by the input item.
	 *
	 * <p>{@code isInput} deliberately does not test which potion ITEM is held, so a
	 * splash or lingering awkward potion is a valid base and {@code getOutput} hands
	 * back the same container. That reproduces vanilla's
	 * {@code PotionBrewing.getOutput}, which builds
	 * {@code PotionUtils.setPotion(new ItemStack(input.getItem()), mix.to)}.
	 */
	private record Mix(Potion from, Item ingredient, Potion to) implements IBrewingRecipe {
		@Override
		public boolean isInput(final ItemStack input) {
			return PotionUtils.getPotion(input) == this.from;
		}

		@Override
		public boolean isIngredient(final ItemStack stack) {
			return stack.is(this.ingredient);
		}

		@Override
		public ItemStack getOutput(final ItemStack input, final ItemStack stack) {
			if (!isInput(input) || !isIngredient(stack)) {
				return ItemStack.EMPTY;
			}

			return PotionUtils.setPotion(new ItemStack(input.getItem()), this.to);
		}
	}

	/** {@code ServerLivingEntityEvents.ALLOW_DEATH}'s shape: false means the entity lives. */
	@FunctionalInterface
	public interface AllowDeathListener {
		boolean allowDeath(LivingEntity entity, DamageSource source, float amount);
	}

	/** {@code ServerLivingEntityEvents.AFTER_DEATH}'s shape. */
	@FunctionalInterface
	public interface AfterDeathListener {
		void afterDeath(LivingEntity entity, DamageSource source);
	}

	/** {@code ServerEntityEvents.ENTITY_LOAD}'s shape. */
	@FunctionalInterface
	public interface EntityLoadListener {
		void entityLoad(Entity entity, ServerLevel level);
	}

	/**
	 * The two parameters both interact callbacks need, with the DENY sense the two
	 * cancellable Forge events force. {@code Level} and the hit result are dropped
	 * because neither shared lambda reads them.
	 */
	@FunctionalInterface
	public interface DenyUseListener {
		boolean deny(Player player, InteractionHand hand);
	}

	/** {@code CommandRegistrationCallback}'s three parameters, in that order. */
	@FunctionalInterface
	public interface CommandRegistrar {
		void register(CommandDispatcher<CommandSourceStack> dispatcher,
				CommandBuildContext registries, Commands.CommandSelection environment);
	}
}
