package com.archetypes.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;

/**
 * The NeoForge half of the event seam: one static registration helper per fabric-api event
 * the shared tree registers, each taking the very same lambda the Fabric arm hands to
 * fabric-api. <b>Not one line of balance logic lives here</b> — that is
 * {@code SlayerCombat}, {@code AgilityCombat}, the twenty tickers, {@code ModItems},
 * {@code ManaPotions} and {@code ArchetypeCommands}, unforked (Skill Proficiencies'
 * conventions §5a applied to events instead of mixins).
 *
 * <p>This class exists because {@code com.archetypes.platform} is the only package allowed
 * to name loader API (conventions §5g). It is {@code public} because its callers are all
 * over {@code com.archetypes}; the Fabric and Forge node scripts exclude it by the anchored
 * {@code NeoForge*} glob (§5e-ter).
 *
 * <p><b>Every method states which contract it owes the caller, and — where the loader's
 * event is not the same event — exactly how far the substitute is from it, with the artifact
 * line that proves the claim.</b> That accounting is design R-20's first bullet: a re-rooted
 * event must reproduce the event's own contract, not merely fire somewhere plausible. Skill
 * Proficiencies' four behavioural regressions all sat in one re-rooted event that every
 * build-shaped gate had passed.
 *
 * <p>Bus assignment, read off the class declarations rather than assumed:
 * {@code BuildCreativeModeTabContentsEvent} is the only one here that
 * {@code implements IModBusEvent}, so it goes on the mod bus;
 * <b>{@code RegisterBrewingRecipesEvent} is NOT a mod-bus event on this loader</b> — it is
 * posted from {@code PotionBrewing.bootstrap} with
 * {@code NeoForge.EVENT_BUS.post(...)} ({@code world/item/alchemy/PotionBrewing.java:163}),
 * i.e. on the game bus, once per {@code RegistryAccess} build. Getting that backwards is a
 * silently absent recipe set, not a compile error.
 *
 * <p>Registration happens from inside the {@code RegisterEvent} window — see
 * {@link ArchetypesNeoForge} for why the whole shared init runs there, and why adding a
 * mod-bus listener for a DIFFERENT event class while the mod bus is posting
 * {@code RegisterEvent} is safe (the {@code ListenerList} being iterated is not the one
 * mutated).
 */
public final class NeoForgeEvents {
	/**
	 * Cached so the {@code afterDamage} hot path does not clone the enum's values array on
	 * every hit. Summing over ALL constants rather than the four the patched
	 * {@code actuallyHurt} happens to set today is deliberate: an unset reduction reads 0.0F
	 * ({@code DamageContainer.getReduction} is {@code reductions.getOrDefault(type, 0f)}), so
	 * the sum stays correct if NeoForge ever starts recording {@code INNATE_RESISTANCE} — the
	 * one constant with no writer in 21.1.243.
	 */
	private static final DamageContainer.Reduction[] REDUCTIONS = DamageContainer.Reduction.values();

	private NeoForgeEvents() {
	}

	// ------------------------------------------------------------------ damage

	/**
	 * {@code ServerLivingEntityEvents.AfterDamage}, parameter for parameter — the same
	 * five-argument shape {@code platform/LegacyDamageEvents} declares for the one Fabric
	 * node whose fabric-api does not have the event.
	 */
	@FunctionalInterface
	public interface AfterDamage {
		void afterDamage(LivingEntity entity, DamageSource source, float baseDamage,
				float damageTaken, boolean blocked);
	}

	/**
	 * The Slayer batch — Hamstring, Rend, Blade Dance. Stands in for
	 * {@code ServerLivingEntityEvents.AFTER_DAMAGE}, which NeoForge does not have.
	 *
	 * <p><b>THE THREE SEMANTICS THIS OWES, and Skill Proficiencies got each of them wrong
	 * once.</b> The reference is {@code fabric-entity-events-v1} 1.8.0 (the 1.21.1 pin),
	 * {@code mixin/entity/event/LivingEntityMixin.afterDamage}: an {@code @Inject} at
	 * {@code TAIL} of {@code hurt(DamageSource,float)Z} with
	 * {@code LocalCapture.CAPTURE_FAILHARD}, capturing {@code float dealt} and
	 * {@code boolean blocked}, guarded by {@code if (!isDead())}, invoking
	 * {@code afterDamage(entity, source, dealt, amount, blocked)} — where {@code amount} is
	 * the METHOD ARGUMENT slot, mutated in place by the shield / freezing / helmet math and
	 * therefore post-shield and <b>PRE-ARMOUR</b>.
	 *
	 * <ol>
	 * <li><b>Which value is reported.</b> {@code LivingDamageEvent.Post.getNewDamage()} is
	 *     the FINAL health loss, i.e. post-armour; using it raw is precisely Skill
	 *     Proficiencies' R-20 bug. The pre-armour figure is recovered exactly rather than
	 *     approximated: {@code DamageContainer.setReduction} is
	 *     {@code this.newDamage -= modifiedReduction}, and every reduction the patched
	 *     {@code actuallyHurt} records is readable from the event, so
	 *     <b>{@code preArmour = getNewDamage() + Σ getReduction(r)}</b>. Shield blocking is
	 *     NOT in the reduction map ({@code setBlockedDamage} subtracts it separately), which
	 *     is right, because Fabric's {@code amount} is post-shield too.
	 *     <p><b>Note which parameter gets which.</b> Fabric hands
	 *     {@code afterDamage(entity, source, DEALT, AMOUNT, blocked)} — its third argument is
	 *     the {@code hurt} return value's {@code dealt} local and its fourth is the mutated
	 *     argument slot. The consumer in {@code SlayerCombat} reads the FOURTH
	 *     ({@code taken}) and never the third, so the pre-armour figure goes there and
	 *     {@code baseDamage} carries the container's original amount.</li>
	 * <li><b>The death gate.</b> {@code onLivingDamagePost} is called at the end of
	 *     {@code actuallyHurt}, after {@code setHealth}, and {@code hurt} calls {@code die}
	 *     only afterwards — so {@code isDeadOrDying()} here is exactly what Fabric's
	 *     {@code !isDead()} at TAIL tests. Without it a killing blow pays out, which is R-20's
	 *     third finding.</li>
	 * <li><b>It must fire for PLAYERS.</b> It does: {@code LivingDamageEvent.Post} is posted
	 *     from {@code LivingEntity.actuallyHurt}, which {@code Player.actuallyHurt} calls
	 *     through {@code super}. That is the semantic this repo's own 1.20.1 substitute had
	 *     to be re-rooted for, and it costs nothing here.</li>
	 * </ol>
	 *
	 * <p>Two honest residues, neither a balance channel and both Skill Proficiencies':
	 * {@code blocked} is derived as {@code getBlockedDamage() > 0}, which is VANILLA's
	 * meaning of the local Fabric captures (the patch redefines its own {@code flag} as
	 * "fully blocked"); and a totem of undying restores health to 1 after
	 * {@code actuallyHurt} but before Fabric's TAIL, so Fabric would fire for a totem-saved
	 * blow and this does not.
	 *
	 * <p>The consumer's own two filters (positive damage, not blocked) are left to the shared
	 * lambda, which applies them as its first statement — passed through, not re-decided
	 * here, so all seven nodes make that call in one place.
	 */
	public static void afterDamage(final AfterDamage listener) {
		NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
			LivingEntity entity = event.getEntity();

			if (entity.level().isClientSide()) {
				return;
			}

			// AFTER_DAMAGE is not fired if the entity was killed by the damage.
			if (entity.isDeadOrDying()) {
				return;
			}

			boolean blocked = event.getBlockedDamage() > 0.0F;
			float preArmour = event.getNewDamage();

			for (final DamageContainer.Reduction reduction : REDUCTIONS) {
				preArmour += event.getReduction(reduction);
			}

			listener.afterDamage(entity, event.getSource(), event.getOriginalDamage(), preArmour, blocked);
		});
	}

	// ------------------------------------------------------------------ death

	/** {@code ServerLivingEntityEvents.AllowDeath} — false means THE ENTITY SURVIVES. */
	@FunctionalInterface
	public interface AllowDeath {
		boolean allowDeath(LivingEntity entity, DamageSource source, float amount);
	}

	/**
	 * Last Shadow's cheat-death and the Magic Armaments teardown —
	 * {@code ServerLivingEntityEvents.ALLOW_DEATH}.
	 *
	 * <p><b>THE R-20 TRAP DESIGN §3.4 NAMES IS THIS METHOD'S WHOLE JOB: "false" means the
	 * entity survives at its current health, NOT that the damage was voided.</b> A helper
	 * that mapped it onto cancelling the damage would hand out immortality rather than lose a
	 * proc — the Last Shadow lambda sets health to 1.0F and cleanses BEFORE it returns false,
	 * and depends on that meaning exactly.
	 *
	 * <p>The two sites, and they are closer than they look:
	 *
	 * <ul>
	 * <li><b>Fabric</b> is a {@code @Redirect} on {@code LivingEntity.isDead()} at
	 *     {@code ordinal = 1} inside {@code hurt}
	 *     ({@code fabric-entity-events-v1} 1.8.0, {@code LivingEntityMixin.beforeEntityKilled}):
	 *     a false return makes the check read false, so vanilla never calls {@code die} at
	 *     all.</li>
	 * <li><b>Here</b>: {@code LivingDeathEvent}, posted by
	 *     {@code CommonHooks.onLivingDeath} from the very first line of the patched
	 *     {@code LivingEntity.die} ({@code LivingEntity.java:1372}:
	 *     {@code if (CommonHooks.onLivingDeath(this, source)) return;}), i.e. before the
	 *     {@code !isRemoved() && !dead} guard and before kill credit, drops and the death
	 *     broadcast. Cancelling makes {@code die} return having done nothing.</li>
	 * </ul>
	 *
	 * <p>So both leave the entity alive at whatever health the handler set, with no death
	 * processed, and {@code hurt} does nothing after the {@code die} call either way. The
	 * difference is one no-op stack frame.
	 *
	 * <p><b>The one parameter that is NOT reproduced is {@code amount}, and it is passed as
	 * 0.0F.</b> {@code LivingDeathEvent} carries the source and nothing else — the damage
	 * figure is inside the {@code DamageContainer} that {@code hurt} has already popped by
	 * then. Neither of the two consumers in this mod reads it (checked: {@code Archetypes}'
	 * Magic Armaments teardown and {@code AgilityCombat}'s Last Shadow both ignore it), and a
	 * future consumer that wants it needs a different anchor rather than a guess written
	 * here. Stated loudly instead of quietly passing {@code getHealth()} or some other
	 * plausible-looking number.
	 */
	public static void allowDeath(final AllowDeath listener) {
		NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, event -> {
			if (!listener.allowDeath(event.getEntity(), event.getSource(), 0.0F)) {
				event.setCanceled(true);
			}
		});
	}

	/**
	 * {@code ServerLivingEntityEvents.AFTER_DEATH} — the kill rewards (the mark, Predator,
	 * Momentum, the night form's feed, the Slayer batch, the Seeker's missile kills).
	 *
	 * <p>Fabric's site: {@code @Inject(method = "onDeath", at = INVOKE
	 * Level.broadcastEntityEvent)} — i.e. INSIDE {@code die}, inside the
	 * {@code !isRemoved() && !dead} guard, after kill credit and after
	 * {@code dropAllDeathLoot}. NeoForge has no post-death event at all, so the same
	 * {@code LivingDeathEvent} carries this too, and the gap is closed the way Skill
	 * Proficiencies closed the same gap for {@code PlayerBlockBreakEvents.After}:
	 *
	 * <ul>
	 * <li><b>{@link EventPriority#LOWEST} with the default {@code receiveCanceled = false}</b>,
	 *     so this runs after every other listener and is skipped entirely if anything — including
	 *     {@link #allowDeath} above, which is Last Shadow — cancels the death. That is the
	 *     closest a pre event can get to "the death happened", and it is the ordering the
	 *     shared comment at {@code AgilityCombat}'s registration asks for.</li>
	 * <li><b>Once per death.</b> Fabric's placement inside the {@code !dead} guard makes it
	 *     once-only; here the guard has not run yet. It is once-only anyway on the only path
	 *     that reaches {@code die} in play: {@code LivingEntity.hurt} returns false at its
	 *     third statement when {@code isDeadOrDying()} ({@code LivingEntity.java:1372}'s
	 *     method, head), so a dead entity is never hurt into {@code die} a second time.</li>
	 * <li><b>Ordering vs drops.</b> Fabric fires after {@code dropAllDeathLoot}, this fires
	 *     before it. No consumer touches drops or experience — they read the source, the
	 *     victim and the killer's own state — so the two orders are indistinguishable. A
	 *     future consumer that wants to ADD a drop must not use this helper.</li>
	 * <li><b>More than one listener, in registration order</b>, which
	 *     {@code AgilityCombat} explicitly depends on: the bus keeps insertion order within a
	 *     priority, and all four registrations land at {@code LOWEST}.</li>
	 * </ul>
	 */
	public static void afterDeath(final BiConsumer<LivingEntity, DamageSource> listener) {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class,
				event -> listener.accept(event.getEntity(), event.getSource()));
	}

	// ------------------------------------------------------------------ entities

	/**
	 * {@code ServerEntityEvents.ENTITY_LOAD} — True Shot's arrow empower and the
	 * stray-conjured-item void.
	 *
	 * <p>Contract owed: fire once per entity ADDED TO A SERVER LEVEL, for chunk-loaded and
	 * dimension-hopped entities as well as fresh ones. The shared lambdas do their own
	 * {@code tickCount > 0} filtering and the comment at the call site says so, so a helper
	 * that fired only for genuinely new entities would be a DIFFERENT event and would make
	 * that filter silently stop meaning anything.
	 *
	 * <p>{@code EntityJoinLevelEvent} is that event and then some: it is posted from
	 * {@code Level.addFreshEntity}/the chunk entity path on BOTH logical sides, and its
	 * {@code getLevel()} is declared {@code Level}. So the {@code ServerLevel} narrowing is
	 * done here — a filter, which is what makes this a rename rather than a re-rooting, and
	 * exactly the guard the Fabric event gets from living in the server-only package.
	 *
	 * <p>It is also cancellable, and this listener never cancels: the event is consumed for
	 * its notification only, at default priority so a mod that DOES cancel a spawn is seen
	 * either way. Left at default rather than LOWEST deliberately — a cancelled spawn is rare
	 * and both lambdas are idempotent, whereas a LOWEST listener would silently stop
	 * empowering arrows for anyone running a spawn-filtering mod.
	 */
	public static void entityLoad(final BiConsumer<Entity, ServerLevel> listener) {
		NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, event -> {
			if (event.getLevel() instanceof ServerLevel level) {
				listener.accept(event.getEntity(), level);
			}
		});
	}

	// ------------------------------------------------------------------ player + server lifecycle

	/**
	 * {@code ServerPlayConnectionEvents.JOIN} — the advancement refresh, the bank guard and
	 * the five teardown calls in {@code Archetypes.onPlayerJoin}.
	 *
	 * <p>Contract owed: once per login, on the server thread, at a point where the connection
	 * can already receive packets — {@code resyncAll} sends one on the loaders that need it.
	 * Met, and provably: {@code patches/net/minecraft/server/players/PlayerList.java.patch}
	 * fires {@code EventHooks.firePlayerLoggedIn} at the very end of {@code placeNewPlayer},
	 * after every login packet and after {@code initInventoryMenu()} — and, on the line
	 * before it, after {@code AttachmentSync.syncInitialPlayerAttachments(player)}, which is
	 * the same fact that lets {@code NeoForgeArchetypeStore.resyncAll} be a no-op.
	 *
	 * <p>{@code PlayerEvent.getEntity()} is declared {@code Player}, so the
	 * {@code ServerPlayer} narrowing happens here. It is a filter, not a cast that can fail:
	 * the event is only ever posted from {@code PlayerList}.
	 */
	public static void playerJoin(final Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * {@code ServerTickEvents.END_SERVER_TICK} — <b>fourteen registrations, the whole ticker
	 * layer of this mod</b>. Contract owed: once per server tick, at the END of it.
	 * {@code ServerTickEvent.Post}'s own javadoc is "fired once per server tick, after the
	 * server performs work for the current tick… only fires on the logical server", which is
	 * that contract verbatim.
	 */
	public static void endServerTick(final Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
	}

	/**
	 * {@code ServerLifecycleEvents.SERVER_STOPPING} — {@code OracleStrikes}' pending-strike
	 * drain. Contract owed: the server is shutting down and the world is still there.
	 * {@code ServerStoppingEvent} is posted from {@code MinecraftServer.stopServer}'s head,
	 * before the levels are saved and closed, which is where Fabric's fires too.
	 */
	public static void serverStopping(final Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> listener.accept(event.getServer()));
	}

	/**
	 * {@code ServerLifecycleEvents.SERVER_STOPPED} — the night form's bleed list. Contract
	 * owed: everything is down; this is a static-state drain and nothing else. Both
	 * consumers ignore the server argument.
	 */
	public static void serverStopped(final Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> listener.accept(event.getServer()));
	}

	// ------------------------------------------------------------------ interaction

	/**
	 * A DENY predicate: {@code true} cancels the interaction for that hand, and nothing else.
	 *
	 * <p>The shape is deliberately not fabric-api's. {@code UseItemCallback} returns a
	 * RESULT, and which result type it returns is itself a 1.21.2 boundary
	 * ({@code InteractionResult} above, {@code InteractionResultHolder<ItemStack>} below);
	 * the loader events are cancellable and have no result to return. Asking the shared call
	 * site the question the other way round is what lets ONE lambda body serve every node —
	 * see the two registrations in {@code Archetypes.onInitialize}.
	 */
	@FunctionalInterface
	public interface DenyInteraction {
		boolean deny(Player player, InteractionHand hand);
	}

	/**
	 * The greatsword's two-handed lock, off-hand half — {@code UseItemCallback.EVENT}.
	 *
	 * <p>{@code PlayerInteractEvent.RightClickItem} is posted from
	 * {@code CommonHooks.onItemRightClick} inside {@code MultiPlayerGameMode.useItem} /
	 * {@code ServerPlayerGameMode.useItem}, per hand, which is the same per-hand call
	 * {@code UseItemCallback} is invoked from. The cancellation RESULT is set to
	 * {@code FAIL} rather than left at its {@code PASS} default so the two match exactly:
	 * Fabric's arm returns {@code InteractionResult.FAIL} (or {@code fail(stack)} below
	 * 1.21.2), and {@code PASS} would let vanilla fall through to further handling.
	 */
	public static void denyUseItem(final DenyInteraction predicate) {
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, event -> {
			if (predicate.deny(event.getEntity(), event.getHand())) {
				event.setCancellationResult(InteractionResult.FAIL);
				event.setCanceled(true);
			}
		});
	}

	/**
	 * The same lock's block half — {@code UseBlockCallback.EVENT}, which returns a plain
	 * {@code InteractionResult} on every version and therefore never needed the 1.21.2 fork
	 * its sibling did.
	 *
	 * <p>{@code PlayerInteractEvent.RightClickBlock} is posted from
	 * {@code CommonHooks.onRightClickBlock} at the head of the use-on-block path. Same
	 * {@code FAIL} cancellation result, for the same reason and with one extra consequence
	 * worth naming: the event's own javadoc says a {@code PASS} result proceeds to
	 * {@code RightClickItem}, so leaving the default here would have handed the click to the
	 * method above and relied on it to refuse a second time.
	 */
	public static void denyUseBlock(final DenyInteraction predicate) {
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
			if (predicate.deny(event.getEntity(), event.getHand())) {
				event.setCancellationResult(InteractionResult.FAIL);
				event.setCanceled(true);
			}
		});
	}

	// ------------------------------------------------------------------ registration events

	/**
	 * The six brewing mixes, through {@link BrewingSink} — the one new shared type the loader
	 * axis needed, so that the recipe TABLE is not transcribed a fourth and fifth time (read
	 * that interface's header).
	 *
	 * <p><b>GAME BUS, not the mod bus, and that is measured rather than assumed.</b>
	 * {@code RegisterBrewingRecipesEvent} does not implement {@code IModBusEvent}; it is
	 * posted by {@code NeoForge.EVENT_BUS.post(...)} from {@code PotionBrewing.bootstrap}
	 * ({@code world/item/alchemy/PotionBrewing.java:163}), which the server runs while
	 * building its {@code RegistryAccess} — so it fires once per world load and once per
	 * {@code /reload}, well after mod init. Putting it on the mod bus would compile and then
	 * quietly register nothing.
	 *
	 * <p>{@code Builder.addMix(Holder<Potion>, Item, Holder<Potion>)}
	 * ({@code PotionBrewing.java:260}) is exactly {@link BrewingSink}'s shape, which is why
	 * the sink is spelled in those three types: no adapter, and the same
	 * {@code Ingredient.of(item)} wrapping vanilla's own mixes get.
	 */
	public static void brewingRecipes(final Consumer<BrewingSink> mixes) {
		NeoForge.EVENT_BUS.addListener(RegisterBrewingRecipesEvent.class, event -> {
			var builder = event.getBuilder();
			mixes.accept(builder::addMix);
		});
	}

	/**
	 * The two vanilla creative tabs this mod adds to.
	 *
	 * <p>{@code BuildCreativeModeTabContentsEvent implements CreativeModeTab.Output} (its
	 * class declaration), which is what lets all twenty-three {@code output.accept(...)}
	 * lines stay outside the conditional in {@code ModItems} — the same trick the Fabric arms
	 * use with {@code FabricItemGroupEntries}. The event fires for EVERY tab, so the key
	 * filter is mandatory.
	 *
	 * <p>It is an {@code IModBusEvent}, so it goes on the mod bus. Its javadoc warns it "may
	 * be fired multiple times if the operator status of the local player or enabled feature
	 * flags changes" — harmless, because each build starts from a fresh entry set.
	 */
	public static void creativeTabOutput(final ResourceKey<CreativeModeTab> tab,
			final Consumer<CreativeModeTab.Output> listener) {
		ArchetypesNeoForge.modEventBus().addListener(BuildCreativeModeTabContentsEvent.class, event -> {
			if (event.getTabKey().equals(tab)) {
				listener.accept(event);
			}
		});
	}

	/**
	 * The mod's OWN creative tab — the builder call only; the title, the icon and the whole
	 * {@code displayItems} body (which is the tab's CONTENTS) stay shared.
	 *
	 * <p>fabric-api ships {@code FabricItemGroup.builder()} / {@code FabricCreativeModeTab.builder()}
	 * because vanilla's factory takes {@code (Row, int column)}, which are meaningless for a
	 * tab that is not one of vanilla's own. The shared call site guessed that both loaders
	 * patch a no-arg overload in for the same reason, and said so as a guess. <b>Measured
	 * here and the guess was right:</b> {@code CreativeModeTab.builder()} exists at
	 * {@code world/item/CreativeModeTab.java:79}, and the two-argument vanilla factory on the
	 * line below it is annotated deprecated with the comment "Forge: use builder()". So this
	 * helper is the one line the call site predicted.
	 */
	public static CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	/**
	 * {@code CommandRegistrationCallback.EVENT} — the op-and-creative-gated {@code /archetypes}
	 * test kit.
	 *
	 * <p>{@code RegisterCommandsEvent} is on the game bus and fires "whenever the
	 * {@code Commands} class is constructed", i.e. on server start and on every
	 * {@code /reload}, which is where the Fabric callback fires too. All three arguments are
	 * carried across in Fabric's order — {@code getDispatcher()}, {@code getBuildContext()},
	 * {@code getCommandSelection()} — even though the shared tree uses only the first, so
	 * that a future argument use needs no change here.
	 */
	@FunctionalInterface
	public interface CommandRegistration {
		void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registries,
				Commands.CommandSelection environment);
	}

	public static void registerCommands(final CommandRegistration listener) {
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event ->
				listener.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
	}
}
