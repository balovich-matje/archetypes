package com.archetypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Spear Phalanx (the node is still {@code Family.GROUND_SLAM}): two spearmen
 * instead of a shockwave.
 *
 * <h2>What changed and why</h2>
 * The capstone used to turn the bash into a ring — same hit, larger circle. It
 * read as "the bash, but more", which is a poor thing for a capstone to be
 * when the node opposite it is Bulwark and the tree's other half is already
 * about widening the bash. So the ring is gone. The bash now plants two clones
 * of the caster at their shoulders and all three thrust forward together: a
 * formation, not an explosion, and it points the ability the same way the
 * caster is pointing instead of at everything around them.
 *
 * <h2>The hit</h2>
 * One hit per victim, of the bash's damage <em>plus</em> a spear thrust, and it
 * goes through {@link MeleeSwing} so the rest of the mod sees a real swing —
 * the same reasoning as {@code SlayerActives.resolve}: a capstone's blow that
 * skipped the funnel would be the one attack in the tree that armour handled
 * differently, and Specialities' multipliers ride the same funnel.
 *
 * <p>The stab is scaled off {@code ATTACK_DAMAGE}, which is the item, Strength
 * and the tree together — the same number the Slayer's own capstone reads.
 *
 * <h2>No spear, no phalanx</h2>
 * The node is gated on actually carrying a spear, and the bash falls back to
 * its ordinary front-cone shove when the caster is not. That is deliberately
 * not a dead capstone: it is a loadout the node asks for, on the same column
 * as Spearwall, and the ability the player keeps when they ignore it is the
 * one they already had.
 *
 * <h2>The clones are decoration and nothing else</h2>
 * Gameplay resolves in full at cast, server-side, the way the rest of
 * {@code ShieldBash} does. What follows is {@link PhantomSpearman} — a player
 * assembled out of clientbound packets, with no entity behind it on the server
 * at all. Keeping it that way is what makes it safe: a clone that vanished
 * early, or that a client never received, cannot cost anybody a hit.
 *
 * <p>The bookkeeping here is deliberately made of value types — entity ids,
 * profile UUIDs, viewer UUIDs, a team name. Nothing in {@link #LIVE} holds a
 * reference to a player or a level, so a formation outliving the disconnect of
 * everyone who could see it leaks nothing and its despawn simply finds nobody
 * to tell.
 *
 * <p>The profile ids and the team name are derived from the CASTER rather than
 * minted per cast, which caps what a viewer's client accumulates — see
 * {@link PhantomSpearman}. The price of stable ids is that two formations for
 * one caster must never stand at once; {@code formUp} sweeps first rather than
 * leaning on the bash cooldown to be longer than the formation's life.
 */
public final class SpearPhalanx {
	/**
	 * A cast in flight: the two spearmen, the client-only team that hides their
	 * name plates, the clients that were told about them, and the two gametimes
	 * that shape their short life.
	 *
	 * <p>{@code stabbed} is a latch, not an equality check against
	 * {@code stabAt}: a lagged server tick can skip the exact gametime, and the
	 * thrust must fire on the next tick rather than never.
	 */
	private static final class Formation {
		final List<PhantomSpearman> spearmen;
		final String team;
		final UUID caster;
		final List<UUID> viewers;
		final long stabAt;
		final long removeAt;
		boolean stabbed;

		Formation(final List<PhantomSpearman> spearmen, final String team, final UUID caster,
				final List<UUID> viewers, final long stabAt, final long removeAt) {
			this.spearmen = spearmen;
			this.team = team;
			this.caster = caster;
			this.viewers = viewers;
			this.stabAt = stabAt;
			this.removeAt = removeAt;
		}
	}

	private static final List<Formation> LIVE = new ArrayList<>();

	private SpearPhalanx() {
	}

	/** The spear this player would fight with, or null if they carry none. */
	public static ItemStack spear(final ServerPlayer player) {
		ItemStack main = player.getMainHandItem();

		if (main.is(ItemTags.SPEARS)) {
			return main;
		}

		ItemStack off = player.getOffhandItem();
		return off.is(ItemTags.SPEARS) ? off : null;
	}

	/**
	 * The formation's blow. Called from {@code ShieldBash} in place of the
	 * normal target loop, so a victim takes this or the bash, never both.
	 *
	 * @param bashDamage the bash's own damage, already shaped by Shield Slam
	 */
	public static void execute(final ServerPlayer player, final ServerLevel level,
			final ItemStack spear, final float bashDamage, final int wide) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		double range = Tuning.phalanxRange(wide);

		float stab = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.PHALANX_STAB_MULTIPLIER);
		float damage = bashDamage + stab;

		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().expandTowards(flat.scale(range)).inflate(1.2, 0.5, 1.2),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& inFront(player, entity, flat));

		// One swing, opened once around every victim: three spears landing
		// together are one blow, and opening a swing per victim would let a
		// per-swing passive fire once per body.
		var previousSwing = MeleeSwing.begin(player);

		try {
			for (LivingEntity victim : victims) {
				victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
			}
		} finally {
			MeleeSwing.end(previousSwing);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);

		stabTrail(level, player, flat, range);
		formUp(level, player, spear, flat);
	}

	/**
	 * The crit cloud along the line the spears cover, thickest where the
	 * flanking pair actually reach rather than evenly down the middle — the
	 * particles ARE the hitbox as far as a player reading the ability goes, so
	 * they are drawn from the same range the victim query used.
	 */
	private static void stabTrail(final ServerLevel level, final ServerPlayer player,
			final Vec3 flat, final double range) {
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		double y = player.getY() + player.getBbHeight() * 0.55;

		for (int lane = -1; lane <= 1; lane += 2) {
			Vec3 origin = player.position().add(right.scale(lane * Tuning.PHALANX_FLANK_OFFSET));

			for (int i = 1; i <= Tuning.PHALANX_TRAIL_POINTS; i++) {
				double along = range * i / Tuning.PHALANX_TRAIL_POINTS;
				level.sendParticles(ParticleTypes.CRIT,
						origin.x + flat.x * along, y, origin.z + flat.z * along,
						2, 0.08, 0.08, 0.08, 0.0);
			}
		}
	}

	/**
	 * Conjures the pair and tells everyone who can see the caster.
	 *
	 * <p>The audience is the caster's own tracker set plus the caster: a client
	 * that is not tracking the caster is not rendering the spot the clones
	 * stand in either, and would be holding two profiles and two entities for a
	 * formation it never sees.
	 */
	private static void formUp(final ServerLevel level, final ServerPlayer player,
			final ItemStack spear, final Vec3 flat) {
		// One caster, one pair of identities, one team — see PhantomSpearman's
		// javadoc for why they are derived rather than minted per cast.
		String team = PhantomSpearman.teamFor(player.getUUID());

		// Which makes overlapping formations the one thing that must not
		// happen: two live formations for one caster would share profile ids,
		// so the first one's despawn would pull the profiles the second one's
		// entities were built from and leave two clones the client can never be
		// told to remove. It cannot occur at shipped numbers — the bash's
		// cooldown floor is BASH_SWING_TICKS + 20% of BASH_ABILITY_TICKS = 40
		// ticks against PHALANX_LIFE_TICKS = 12 — but the identities are only
		// safe while that arithmetic holds, and it lives in another file. So
		// the overlap is closed here instead of assumed: a second cast sweeps
		// the first formation properly before the same ids are re-used.
		dismiss(level.getServer(), player.getUUID());

		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		List<PhantomSpearman> spearmen = new ArrayList<>(2);

		for (int lane = -1; lane <= 1; lane += 2) {
			Vec3 at = player.position().add(right.scale(lane * Tuning.PHALANX_FLANK_OFFSET));
			// The caster's yaw, but NOT their pitch: the clone's pitch is what
			// aims its spear (Tuning.phalanxSpearPitch), and a caster who cast
			// while looking at the sky would otherwise plant two spearmen
			// saluting it.
			spearmen.add(PhantomSpearman.conjure(level, player, lane < 0 ? 'L' : 'R', at,
					player.getYRot(), Tuning.phalanxSpearPitch()));
		}

		List<Packet<?>> spawn = new ArrayList<>();
		spawn.add(PhantomSpearman.raiseColours(team,
				spearmen.stream().map(PhantomSpearman::name).toList()));

		for (PhantomSpearman spearman : spearmen) {
			spearman.appendSpawn(spawn, player, spear);
		}

		List<ServerPlayer> audience = new ArrayList<>(PlayerLookup.tracking(player));

		if (!audience.contains(player)) {
			audience.add(player);
		}

		for (ServerPlayer viewer : audience) {
			for (Packet<?> packet : spawn) {
				viewer.connection.send(packet);
			}
		}

		// One clock for spawn and sweep: the overworld's, which every dimension
		// shares, so a formation cast in the Nether is not compared against a
		// gametime read somewhere else.
		long now = level.getServer().overworld().getGameTime();
		LIVE.add(new Formation(spearmen, team, player.getUUID(),
				audience.stream().map(ServerPlayer::getUUID).toList(),
				now + Tuning.PHALANX_WINDUP_TICKS, now + Tuning.PHALANX_LIFE_TICKS));
	}

	/**
	 * Drops any formation that does not belong to this server.
	 *
	 * <p>{@link #LIVE} is static and an integrated server is torn down and
	 * rebuilt inside one JVM every time a single-player world is left, so
	 * without this a cast made in the last ten ticks of a world would be ticked
	 * against the next one. The clones themselves need no cleanup on the way
	 * out: they live in clients that are being disconnected, and a disconnected
	 * client keeps nothing.
	 */
	public static void forget() {
		LIVE.clear();
	}

	/** Drives the thrust and sweeps the dead. Called once a server tick. */
	public static void tick(final MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}

		long now = server.overworld().getGameTime();
		var iterator = LIVE.iterator();

		while (iterator.hasNext()) {
			Formation formation = iterator.next();

			if (!formation.stabbed && now >= formation.stabAt) {
				formation.stabbed = true;
				ServerPlayer caster = server.getPlayerList().getPlayer(formation.caster);

				// The swing packet is addressed by hand but still has to be
				// BUILT around some entity; without the caster there is nobody
				// to build it from, and a formation whose caster logged out mid
				// cast simply stands still until it is swept.
				if (caster != null) {
					List<Packet<?>> thrust = new ArrayList<>(formation.spearmen.size());

					for (PhantomSpearman spearman : formation.spearmen) {
						thrust.add(spearman.stab(caster));
					}

					broadcast(server, formation, thrust);
				}
			}

			if (now >= formation.removeAt) {
				List<Packet<?>> despawn = new ArrayList<>(3);
				PhantomSpearman.appendDespawn(despawn, formation.team, formation.spearmen);
				broadcast(server, formation, despawn);
				iterator.remove();
			}
		}
	}

	/**
	 * Sweeps this caster's live formation early, if they somehow have one, by
	 * doing exactly what the timer would have done — despawn packets to the
	 * clients that were told, then drop it. Called at the head of a new cast;
	 * at shipped cooldowns it finds nothing.
	 */
	private static void dismiss(final MinecraftServer server, final UUID caster) {
		var iterator = LIVE.iterator();

		while (iterator.hasNext()) {
			Formation formation = iterator.next();

			if (!formation.caster.equals(caster)) {
				continue;
			}

			List<Packet<?>> despawn = new ArrayList<>(3);
			PhantomSpearman.appendDespawn(despawn, formation.team, formation.spearmen);
			broadcast(server, formation, despawn);
			iterator.remove();
		}
	}

	/**
	 * To exactly the clients that were told about this formation, and only
	 * those still connected. Viewers are held as UUIDs and resolved here, so a
	 * player who quit during the cast is skipped rather than kept alive by the
	 * list — and their client, which is gone, needs no despawn anyway.
	 */
	private static void broadcast(final MinecraftServer server, final Formation formation,
			final List<Packet<?>> packets) {
		for (UUID viewer : formation.viewers) {
			ServerPlayer player = server.getPlayerList().getPlayer(viewer);

			if (player == null) {
				continue;
			}

			for (Packet<?> packet : packets) {
				player.connection.send(packet);
			}
		}
	}

	/** In front: the same >60-degree cone the bash and Decimate both use. */
	private static boolean inFront(final ServerPlayer player, final LivingEntity target,
			final Vec3 flat) {
		Vec3 toTarget = target.position().subtract(player.position());
		Vec3 level = new Vec3(toTarget.x, 0.0, toTarget.z);

		if (level.lengthSqr() < 1.0E-4) {
			return true;
		}

		return flat.dot(level.normalize()) > 0.5;
	}
}
