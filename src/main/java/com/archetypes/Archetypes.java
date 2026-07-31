package com.archetypes;

import com.archetypes.platform.ArchetypeStore;
import com.archetypes.platform.Net;
import com.archetypes.state.WireId;

//? if fabric {
import net.fabricmc.api.ModInitializer;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// THE ENTRYPOINT SPLIT, and why it is a DECLARATION fork rather than an extracted `init()`.
//
// Only the class declaration and the `@Override` fork; the init body itself is shared by every
// loader, unchanged. `implements ModInitializer` and `@Override` are the two Fabric-only tokens
// in this file — `ModInitializer` does not exist on NeoForge or LexForge, and an `@Override`
// that overrides nothing is a compile error.
//
// On the loader axis `onInitialize()` is therefore a plain public instance method, and each
// loader's `@Mod` entrypoint (`platform/ArchetypesNeoForge`, `platform/ArchetypesForge` —
// excluded from every other node's source set by glob) calls `new Archetypes().onInitialize()`.
// The obvious alternative — extract the body into a shared `static void init()` and delegate —
// was NOT taken: it adds a method and rewrites `onInitialize` on five Fabric nodes that are
// required to stay instruction-identical while this axis lands. Renaming it afterwards is a
// follow-up, not something to do silently.
//
// THE INIT ORDER BELOW IS A CONTRACT, and there is only one copy of it, so every loader runs
// exactly this sequence: state -> items -> entities -> effects -> potions -> particles -> the
// twenty-odd tickers/combat registrars -> commands -> Net.registerAll -> the serverbound
// handlers. `ModState.initialize()` first is not stylistic — it hands the store its whole key
// table, and a world may load immediately after construction on both loaders.
//
// R-22 WIDENS HERE AND IS NOT SOLVED HERE. Skill Proficiencies' finding was `Item.<init>`
// asking the ITEM registry for an intrusive holder outside the registration window — a boot
// crash on both loaders. This mod additionally constructs `EntityType` (via
// `EntityType.Builder.build`), a `SimpleParticleType`, three `MobEffect` subclasses and four
// `Potion`s at class-init time. Which of those touches an intrusive holder is design's
// experiment E-R22-1 and belongs to each node's agent; the failure mode is `<clinit>`-time and
// therefore far from the call that was moved.
//? if fabric {
public class Archetypes implements ModInitializer {
//?} else {
/*public class Archetypes {
*///?}
	public static final String MOD_ID = "archetypes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	//? if fabric {
	@Override
	//?}
	public void onInitialize() {
		ModState.initialize();
		ModItems.initialize();
		ModEntities.initialize();
		ManaEffects.initialize();
		RadianceEffect.initialize();
		ManaPotions.initialize();
		AmnesiaPotions.initialize();
		ModParticles.initialize();
		ProtectorTicker.initialize();
		SlayerCombat.initialize();
		SlayerTicker.initialize();
		SlayerActives.initialize();
		CrusherTicker.initialize();
		AgilityCombat.initialize();
		AgilityTicker.initialize();
		ShadowTicker.initialize();
		SeekerTicker.initialize();
		SeekerCombat.initialize();
		BlizzardZones.initialize();
		OracleStrikes.initialize();
		OracleWizardTicker.initialize();
		NightFormTicker.initialize();
		RadianceAura.initialize();
		ColossusProtector.initialize();
		ColossusSlayer.initialize();
		// The op-gated dev test kit: /archetypes set|level|buy|dummy|trace.
		// Idle until somebody with permission level 2 asks for it.
		ArchetypeCommands.initialize();

		// All eleven channel types, both directions, in one call — a dedicated server
		// registers the clientbound pair too, which is why this cannot live client-side.
		Net.INSTANCE.registerAll();

		Net.INSTANCE.onServerbound(WireId.BUY_NODE, (player, buf) -> {
			SubTree tree = SubTree.byId(buf.readUtf());
			int node = buf.readVarInt();

			// Only spend into trees of the archetype you actually are.
			if (tree == null || ModState.get(player) != tree.archetype()) {
				return;
			}

			NodePurchases.buy(player, tree, node);
		});

		// The three ability keys are slots, one per sub-tree in screen order;
		// what a slot casts depends on the archetype. Strength trees keep
		// their internal weapon dispatch (the capstone pairs are exclusive).
		Net.INSTANCE.onServerbound(WireId.ACTIVE_ABILITY, (player, buf) -> {
					int slot = buf.readVarInt();
					Archetype archetype = ModState.get(player);

					if (archetype == null || slot < 0 || slot >= 7) {
						return;
					}

					// Slot 3 is the capstone key — Elementalist-only for now,
					// with room for more trees in later versions.
					if (slot == 3) {
						if (archetype == Archetype.INTELLECT) {
							SeekerSpells.castElementalistCapstone(player);
						}

						return;
					}

					// Slots 4-6 are the epic actives, and archetypes share them:
					// an epic tree takes slot 4 + N, where N is its base tree's
					// place in SubTree.of. Two trees on one slot never collide,
					// because they belong to different archetypes.
					if (slot == 4) {
						if (archetype == Archetype.INTELLECT) {
							OracleSpells.lightningStrike(player);
						} else if (archetype == Archetype.AGILITY) {
							Deadeye.activate(player);
						}

						return;
					}

					if (slot == 5) {
						if (archetype == Archetype.INTELLECT) {
							OracleSpells.magicArmaments(player);
						} else if (archetype == Archetype.AGILITY) {
							DeathMark.mark(player);
						} else if (archetype == Archetype.STRENGTH) {
							ColossusSlayer.parry(player);
						}

						return;
					}

					if (slot == 6) {
						if (archetype == Archetype.AGILITY) {
							NightForm.beginRitual(player);
						} else if (archetype == Archetype.STRENGTH) {
							TitansLeap.leap(player);
						}

						return;
					}

					switch (SubTree.of(archetype).get(slot)) {
						case PROTECTOR -> ShieldBash.execute(player);
						case SLAYER -> {
							if (ModItems.isGreatsword(player.getMainHandItem())) {
								SlayerActives.decimate(player);
							} else if (ModItems.isSword(player.getMainHandItem())) {
								SlayerActives.bladestorm(player);
							}
						}
						case CRUSHER -> {
							//? if >=1.21 {
							if (player.getMainHandItem().is(net.minecraft.world.item.Items.MACE)) {
								CrusherActives.quake(player);
							} else {
								CrusherActives.haymaker(player);
							}
							//?} else {
							/*CrusherActives.haymaker(player);
							*///?}
						}
						case MARKSMAN -> AgilityActives.trueShot(player);
						case ASSASSIN -> AgilityActives.shadowStep(player);
						case SHADOW -> AgilityActives.invisibility(player);
						case ELEMENTALIST -> SeekerSpells.castElementalist(player);
						case WIZARD -> SeekerSpells.castMissile(player);
						case PRIEST -> SeekerSpells.castHolyLight(player);
					}
		});

		Net.INSTANCE.onServerbound(WireId.SPELL_CHANNEL,
				(player, buf) -> SeekerSpells.channelFlame(player));

		Net.INSTANCE.onServerbound(WireId.RUSH, (player, buf) -> ShieldRush.execute(player));

		Net.INSTANCE.onServerbound(WireId.DISENGAGE, (player, buf) -> AgilityActives.acrobatics(player));

		Net.INSTANCE.onServerbound(WireId.NIGHT_DASH, (player, buf) -> NightForm.dash(player));

		// The greatsword is strictly two-handed: while it's in the main hand
		// the offhand is dead weight — no shields, no food, no blocks from it.
		// `UseItemCallback` returns fabric-api's mirror of `Item.use`'s return type, so it
		// follows the same 1.21.2 boundary: a bare `InteractionResult` above,
		// `InteractionResultHolder<ItemStack>` below. `UseBlockCallback` below is NOT
		// affected — it returned a plain `InteractionResult` on every version (measured on
		// fabric-api 0.116.14 and 0.155.2).
		// STAGE 6: `fabric &&` scopes the fabric-api boundary to the loader whose API HAS that
		// boundary; it is not a new predicate (conventions §5k). The loader arms hand the same
		// question to a helper that answers it the other way round — a DENY predicate, because
		// `PlayerInteractEvent.RightClickItem`/`RightClickBlock` are cancellable events and have
		// no result type to return. The helper's contract: `true` cancels the interaction for
		// that hand and nothing else, on both loaders and for both events.
		//? if fabric && >=1.21.2 {
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResult.FAIL
						: net.minecraft.world.InteractionResult.PASS);
		//?} elif fabric {
		/*net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand))
						: net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand)));
		*///?} elif neoforge {
		/*NeoForgeEvents.denyUseItem((player, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem()));
		*///?} elif forge {
		/*ForgeEvents.denyUseItem((player, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem()));
		*///?}
		//? if fabric {
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResult.FAIL
						: net.minecraft.world.InteractionResult.PASS);
		//?} elif neoforge {
		/*NeoForgeEvents.denyUseBlock((player, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem()));
		*///?} elif forge {
		/*ForgeEvents.denyUseBlock((player, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem()));
		*///?}

		// Custom swing poses were deprecated (see notes/design.md — Better
		// Combat compat), but the slab still announces itself: a deep whoosh
		// under every charged greatsword swing.
		Net.INSTANCE.onServerbound(WireId.MELEE_SWING, (player, buf) -> {
					if (WeaponClass.of(player) == WeaponClass.GREATSWORD) {
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
								net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
								net.minecraft.sounds.SoundSource.PLAYERS, 1.1F, 0.55F);
					}

					// A conjured-sword swing with no hostile aimed at is a Blink.
					if (ModItems.isMagicSword(player.getMainHandItem())) {
						MagicArmaments.blink(player);
					}
		});

		Net.INSTANCE.onServerbound(WireId.PICK_ARCHETYPE, (player, buf) -> {
			Archetype picked = Archetype.byId(buf.readUtf()).orElse(null);

			if (picked == null) {
				return;
			}

			// One pick for now: ignore attempts to re-pick until we decide
			// whether (and at what cost) an archetype can be changed.
			if (ModState.get(player) == null) {
				ModState.set(player, picked);
				LOGGER.info("{} chose the {} archetype", player.getName().getString(), picked.id());
			}
		});

		// Fresh advancement count each login (self-heals staleness), and the
		// bank-covers-spent guard that makes any future curve retune safe.
		//
		// STAGE 6 — THE ONE FORK IN THIS FILE THAT DUPLICATES A BODY, and it is deliberate.
		// The Fabric lambda takes a CONNECTION HANDLER and reads its `player` field eight
		// times; a loader helper hands a `ServerPlayer` straight in. There is no one lambda
		// shape that fits both, and binding `handler.player` to a local first would rewrite
		// `onInitialize` on five nodes required to stay instruction-identical while this axis
		// lands. So the loader arms take a method reference to `onPlayerJoin` below, which is
		// gated OFF on Fabric so no Fabric node carries an unreachable method.
		//
		// The duplication is a WIRING list, not balance logic, and it is the standing drift
		// risk in this file: anything added to the lambda below has to be added there too.
		// Collapsing the two into one shared method is the follow-up once the Fabric nodes are
		// re-gated. Skill Proficiencies made the identical trade for the identical reason.
		//
		// WHAT A LOADER HELPER OWES (R-20 — a re-rooted event must reproduce the CONTRACT, not
		// merely fire somewhere plausible): fire ONCE per login, on the server thread, at a
		// point where the connection can already receive packets — `resyncAll` sends one, and
		// on the loader that has no attachment sync at all it sends the client its whole world.
		//? if fabric {
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> {
					// A no-op on every node whose platform syncs attached state itself
					// (all of them today). It is the whole of the client's state on the
					// nodes that have no such thing — see ArchetypeStore#resyncAll.
					ArchetypeStore.INSTANCE.resyncAll(handler.player);
					SkillPoints.refreshAdvancementCount(handler.player);
					SkillPoints.ensureBankCoversSpent(handler.player);
					// A Magic Armaments channel that outlived its server hands the
					// wand back and clears the conjured weapon on the way in.
					MagicArmaments.restoreDirty(handler.player);
					// A ritual cannot survive a relog; the hour of night form
					// can and must (it is the node's whole price), so only the
					// channel is torn down here.
					NightForm.interrupt(handler.player);
					// Same rule for Deadeye: fifteen seconds is not worth
					// persisting, and a stance stamp restored without its
					// ticker would hand out free arrows until it lapsed.
					Deadeye.end(handler.player);
					// And the mark: a minute is not worth persisting, and an
					// entity id is not stable across a relog anyway — a restored
					// stamp would name whatever wears that id now.
					DeathMark.clear(handler.player);
					// And an in-flight Titan's Leap: the stamp is what waives
					// fall damage, and only a landing spends it — a leap
					// restored on a player standing safely on the ground would
					// waive every fall they ever took after it.
					TitansLeap.clear(handler.player);
				});
		//?} elif neoforge {
		/*NeoForgeEvents.playerJoin(Archetypes::onPlayerJoin);
		*///?} elif forge {
		/*ForgeEvents.playerJoin(Archetypes::onPlayerJoin);
		*///?}

		// A player dying mid-channel: end it before drops so the real wand
		// returns to the inventory (dropping or kept as any item would) while the
		// conjured weapon vanishes with the channel.
		//
		// Registration only; the lambda is shared. What a loader helper owes here is the ONE
		// R-20 trap in this event and design §3.4 names it: "cancel" means THE ENTITY SURVIVES
		// AT ITS CURRENT HEALTH, not that the damage was voided. The listener returns `true` to
		// let the death proceed, which is what this lambda always does — it is here for the
		// side effect, and a helper that inverted the sense would make every player immortal
		// rather than merely ending a channel late.
		//? if fabric {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register(
				(entity, source, amount) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.allowDeath((entity, source, amount) -> {
		*///?} elif forge {
		/*ForgeEvents.allowDeath((entity, source, amount) -> {
		*///?}
					if (entity instanceof net.minecraft.server.level.ServerPlayer player
							&& MagicArmaments.isActive(player)) {
						MagicArmaments.end(player);
					}

					return true;
				});

		Net.INSTANCE.onServerbound(WireId.RESET_ARCHETYPE, (player, buf) -> {
			// The client only shows this button in creative, but the client
			// is not to be trusted about game mode — check it here.
			if (!player.isCreative()) {
				return;
			}

			ModState.clear(player);
			LOGGER.info("{} reset their archetype", player.getName().getString());
		});

		LOGGER.info("Archetypes initialized");
	}

	// THE JOIN BODY THE LOADER HELPERS ARE HANDED AS A METHOD REFERENCE. It exists because the
	// Fabric arm above reads its player off a connection handler and this one is handed the
	// player, so there is no single lambda shape that fits both — see the long note at the
	// registration. Gated OFF on Fabric so no Fabric node carries an unreachable method: the
	// five of them are required to stay instruction-identical while this axis lands, and an
	// extra method is a class-shape difference the gate would (correctly) refuse.
	//
	// KEEP IT IN STEP WITH THE LAMBDA ABOVE. Same eight calls, same order, same reasons — each
	// one's "why" is written once, up there.
	//? if fabric {
	//?} else {
	/*public static void onPlayerJoin(final net.minecraft.server.level.ServerPlayer player) {
		// NOT a no-op on either loader the way it is on Fabric >=1.20.5. NeoForge's attachment
		// sync is not proven to push on login, and Forge capabilities have no sync at all — on
		// that node this call IS the client's entire view of its own tree, HUD and cooldowns.
		ArchetypeStore.INSTANCE.resyncAll(player);
		SkillPoints.refreshAdvancementCount(player);
		SkillPoints.ensureBankCoversSpent(player);
		MagicArmaments.restoreDirty(player);
		NightForm.interrupt(player);
		Deadeye.end(player);
		DeathMark.clear(player);
		TitansLeap.clear(player);
	}

	*///?}

	public static Identifier id(final String path) {
		/*? if >=1.21 {*/return Identifier.fromNamespaceAndPath(MOD_ID, path);
		/*?} else *///return new Identifier(MOD_ID, path);
	}
}
