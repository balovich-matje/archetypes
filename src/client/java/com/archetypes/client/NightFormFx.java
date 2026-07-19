package com.archetypes.client;

import com.archetypes.NightForm;
import com.archetypes.Tuning;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The Dark Ritual's channel, seen and heard: dark motes drawn up out of the
 * ground into a tightening column, over a heartbeat that quickens as the ten
 * seconds run out.
 *
 * <p>Client-side and stateless. {@code NIGHT_CHANNEL_END} syncs to every
 * client, so each one runs this pass over every player it can see and arrives
 * at the same picture — no packet, and onlookers get the same show as the
 * caster. Everything ramps on {@link NightForm#channelProgress}, which is the
 * one number the mechanics pass exposes for exactly this.
 *
 * <p>The transformation itself is NOT here: it is a state change, and its
 * burst and cue stay with the state change in {@code NightForm.complete}, where
 * they reach everyone in earshot whether or not the caster is being rendered.
 */
public final class NightFormFx {
	/** The column's radius at the ritual's first tick and at its last. It
	 * closes in on the caster as the ten seconds run out. */
	private static final double RING_START = 1.7;
	private static final double RING_END = 0.35;
	/** Motes per tick at the start and at the end — the density ramp the
	 * author's sketch asks for. */
	private static final int MOTES_START = 2;
	private static final int MOTES_END = 9;
	/** The heartbeat's period in ticks, first beat to last: it roughly triples
	 * in rate over the channel. */
	private static final int BEAT_SLOW = 21;
	private static final int BEAT_FAST = 6;

	private NightFormFx() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null || client.isPaused()) {
				return;
			}

			for (AbstractClientPlayer player : client.level.players()) {
				if (NightForm.isChannelling(player)) {
					channelTick(client.level, player, NightForm.channelProgress(player));
				}
			}
		});
	}

	private static void channelTick(final ClientLevel level, final AbstractClientPlayer player,
			final float progress) {
		RandomSource random = player.getRandom();
		double radius = Mth.lerp(progress, RING_START, RING_END);
		int motes = Math.round(Mth.lerp(progress, MOTES_START, MOTES_END));

		// The column: motes born at ground level on a closing ring and pulled
		// upward, faster the nearer the ritual is to taking.
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double jitter = radius * (0.7 + 0.3 * random.nextDouble());
			double x = player.getX() + Math.cos(angle) * jitter;
			double z = player.getZ() + Math.sin(angle) * jitter;
			level.addParticle(ParticleTypes.SOUL, x, player.getY() + 0.05, z,
					0.0, 0.04 + 0.16 * progress, 0.0);
		}

		// A low skirt of smoke that thickens with the column, so the effect
		// still reads at a distance where single motes do not.
		if (random.nextFloat() < 0.35F + 0.5F * progress) {
			level.addParticle(ParticleTypes.LARGE_SMOKE,
					player.getX() + (random.nextDouble() - 0.5) * radius,
					player.getY() + 0.1,
					player.getZ() + (random.nextDouble() - 0.5) * radius,
					0.0, 0.02, 0.0);
		}

		// The last three seconds: the ritual reaches the caster's own body.
		if (progress > 0.7F) {
			level.addParticle(ParticleTypes.SCULK_SOUL,
					player.getX() + (random.nextDouble() - 0.5) * 0.6,
					player.getY() + random.nextDouble() * player.getBbHeight(),
					player.getZ() + (random.nextDouble() - 0.5) * 0.6,
					0.0, 0.05, 0.0);
		}

		// The drone: a heart beating harder and faster the closer the form
		// comes. Beat ticks are derived from the channel's own elapsed count
		// rather than kept in a map, so nothing here holds state that a relog
		// or a render-distance blink could desynchronise.
		int elapsed = Tuning.DARK_RITUAL_CHANNEL_TICKS - NightForm.channelRemainingTicks(player);
		int period = Math.round(Mth.lerp(progress, BEAT_SLOW, BEAT_FAST));

		if (period > 0 && elapsed % period == 0) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(),
					SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
					0.4F + 0.6F * progress, 0.55F + 0.35F * progress, false);
		}
	}
}
