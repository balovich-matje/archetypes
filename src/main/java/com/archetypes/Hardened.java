package com.archetypes;

import java.util.ArrayList;
import java.util.List;

import com.archetypes.platform.ArchetypeStore;

import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Hardened — the Colossus Crusher's right-hand column, two ranks: every blow
 * an <em>entity</em> lands on this player plates them a little more, +1 armour
 * with a mace and +2 with bare fists, for two seconds at rank 1 and four at
 * rank 2.
 *
 * <h2>Stacks that never refresh</h2>
 * The interesting half of the node, and the reason it is not a mob effect or a
 * single timed modifier: each hit adds a plate of its own with an expiry of its
 * own, and an older plate is never re-stamped by a newer one. Five hits in a
 * second are five plates falling off five ticks apart, not one plate held for
 * two seconds. So the state is a <em>list</em> of {@link Plate}s and the armour
 * granted is their sum — one attribute modifier whose value the ticker
 * re-asserts every tick as the list shrinks (see
 * {@code CrusherTicker#apply}, which rewrites a modifier whose amount drifted).
 *
 * <h2>What the trigger is, and is not</h2>
 * A LIVING source entity is the environmental-damage clause the sketch asks
 * for: fall, fire, lava, drowning, cactus, starvation and suffocation arrive
 * with no attacker at all, and the three that do name one — a falling anvil, a
 * gravel column, a dripstone tip — name a {@code FallingBlockEntity}, which is
 * the ceiling rather than an enemy. A mob's arrow or another player's blow
 * passes: the direct entity is the arrow, the source entity is the shooter.
 *
 * <h2>No cap of our own</h2>
 * Deliberately none. Vanilla's {@code Attributes.ARMOR} is a
 * {@code RangedAttribute} with a maximum of 30, so the sum is already ceilinged
 * by the same number that ceilings a full armour set — a second, invented cap
 * would only be a number to explain in the tooltip.
 *
 * <h2>Lifetime</h2>
 * The plates live on a transient, unsynced attachment, so they survive nothing:
 * a relog and a death both hand the player a fresh entity with no attachment,
 * and a respec is caught twice over — {@code ModState.forgetNodes} calls
 * {@link #clear} outright, and {@link #armour} drops the plates the first tick
 * the rank reads zero, which revokes the modifier in the same tick.
 */
public final class Hardened {
	/**
	 * One plate: the game tick it falls off, and the armour it is worth. A
	 * record rather than a running counter precisely because plates expire
	 * independently — there is no single deadline to keep.
	 */
	public record Plate(long expiresAt, int armour) {
	}

	/**
	 * Hardened's own key on ARMOR. Iron Skin already carries a modifier on that
	 * attribute ({@code CrusherTicker.IRON_SKIN_ARMOR_ID}) and a bare-fisted
	 * Colossus can own both: two modifiers on one attribute must not share an
	 * id, or the last writer wins and the other node silently stops existing.
	 *
	 * <p>Public because {@code CrusherTicker} re-asserts the sum under it and
	 * {@link #clear} revokes it — one constant, two writers, no second literal.
	 */
	public static final Identifier ARMOR_ID = Archetypes.id("hardened_armor");

	private Hardened() {
	}

	/**
	 * A hit landed on this player. Called from the damage funnel
	 * ({@code LivingEntityMixin#archetypes$hardened}) once the hit is known to
	 * have actually been taken.
	 *
	 * <p>The weapon in hand <em>at the moment of the hit</em> decides the
	 * amount, which is why this is read here and not at expiry: a plate earned
	 * bare-fisted stays worth 2 even if the mace comes out a tick later.
	 */
	public static void onHurt(final ServerPlayer player, final DamageSource source) {
		int rank = ColossusCrusherNodes.rank(player, ColossusCrusherNodes.Family.HARDENED);

		// Environmental damage should not count (user note). "Has an entity
		// behind it" is not enough of a test on its own: a falling anvil, a
		// gravel column and a dripstone tip all name the FallingBlockEntity as
		// their cause, and being hit by the ceiling is the definition of
		// environmental. A LIVING attacker is the line — which still admits
		// every projectile, since an arrow's source entity is its shooter.
		//
		// The aliveness test is the other half: this hook rides the RETURN of
		// hurtServer, and that return is reached AFTER die(), by which point a
		// killed player has already dropped their inventory — so a mace death
		// would read as bare hands and plate the corpse for 2.
		if (rank <= 0 || !(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity)
				|| !player.isAlive()) {
			return;
		}

		int armour = switch (WeaponClass.of(player)) {
			case MACE -> Tuning.HARDENED_MACE_ARMOR;
			case HANDS -> Tuning.HARDENED_UNARMED_ARMOR;
			// Anything else in hand is not the Colossus's fight: no plate.
			default -> 0;
		};

		if (armour <= 0) {
			return;
		}

		long now = player.level().getGameTime();
		List<Plate> plates = new ArrayList<>(live(player, now));
		plates.add(new Plate(now + (long) rank * Tuning.HARDENED_DURATION_TICKS_PER_RANK, armour));
		ArchetypeStore.INSTANCE.set(player, ModState.HARDENED_PLATES, List.copyOf(plates));

		// The node is otherwise silent — armour is a number nobody watches — so
		// the flash is how a player learns the plate was theirs.
		ProcIndicators.send(player, SubTree.COLOSSUS_CRUSHER,
				ColossusCrusherNodes.Family.HARDENED);
	}

	/**
	 * The armour every live plate is worth right now, expired ones dropped on
	 * the way past. Called once a tick by {@code CrusherTicker}, which is what
	 * makes this both the getter and the expiry clock: nothing else has to run
	 * on a schedule.
	 */
	public static double armour(final ServerPlayer player) {
		final Entity target = player;

		if (ColossusCrusherNodes.rank(player, ColossusCrusherNodes.Family.HARDENED) <= 0) {
			// Respec'd out mid-stack. Drop the plates here and the ticker's own
			// apply() takes the modifier off in the same tick.
			ArchetypeStore.INSTANCE.remove(target, ModState.HARDENED_PLATES);
			return 0.0;
		}

		int total = 0;

		for (Plate plate : live(player, player.level().getGameTime())) {
			total += plate.armour();
		}

		return total;
	}

	/** Every plate still standing, with the expired ones written out of the
	 * attachment as a side effect — the only pruning pass there is. */
	private static List<Plate> live(final ServerPlayer player, final long now) {
		final Entity target = player;
		List<Plate> stored = ArchetypeStore.INSTANCE.get(target, ModState.HARDENED_PLATES);

		if (stored == null || stored.isEmpty()) {
			return List.of();
		}

		List<Plate> live = new ArrayList<>(stored.size());

		for (Plate plate : stored) {
			if (plate.expiresAt() > now) {
				live.add(plate);
			}
		}

		if (live.size() != stored.size()) {
			if (live.isEmpty()) {
				ArchetypeStore.INSTANCE.remove(target, ModState.HARDENED_PLATES);
			} else {
				ArchetypeStore.INSTANCE.set(target, ModState.HARDENED_PLATES, List.copyOf(live));
			}
		}

		return live;
	}

	/**
	 * Drop every plate and the modifier they fed, immediately. Reached by
	 * {@code ModState.forgetNodes}: a respec must not leave armour
	 * standing on a player who no longer owns the node, and waiting for the
	 * ticker would leave it standing for the rest of the tick.
	 */
	public static void clear(final ServerPlayer player) {
		ArchetypeStore.INSTANCE.remove(player, ModState.HARDENED_PLATES);
		AttributeInstance armour = player.getAttribute(Attributes.ARMOR);

		if (armour != null) {
			armour.removeModifier(ARMOR_ID);
		}
	}
}
