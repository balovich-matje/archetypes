package com.archetypes;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

public final class ModAttachments {
	/**
	 * The player's chosen archetype, or absent if they haven't picked yet.
	 * Server-authoritative; persisted with the player and synced to the owning
	 * client only.
	 */
	public static final AttachmentType<String> ARCHETYPE = AttachmentRegistry.create(
			Archetypes.id("archetype"),
			builder -> builder
					.persistent(Codec.STRING)
					.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/**
	 * Total experience the archetype has banked, in vanilla XP points. Mirrors the
	 * player's own XP gain rather than spending it — see "archetype XP is vanilla
	 * XP" in notes/design.md — so archetype progress never competes with
	 * enchanting.
	 */
	public static final AttachmentType<Integer> ARCHETYPE_XP = AttachmentRegistry.create(
			Archetypes.id("archetype_xp"),
			builder -> builder
					.persistent(Codec.INT)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/** Points already committed to nodes. Earned minus this is what's spendable. */
	public static final AttachmentType<Integer> SPENT_POINTS = AttachmentRegistry.create(
			Archetypes.id("spent_points"),
			builder -> builder
					.persistent(Codec.INT)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	private ModAttachments() {
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}

	/** The player's archetype, or null if unpicked. */
	public static @Nullable Archetype get(final Player player) {
		String id = ((AttachmentTarget) player).getAttached(ARCHETYPE);
		return id == null ? null : Archetype.byId(id).orElse(null);
	}

	public static void set(final Player player, final Archetype archetype) {
		((AttachmentTarget) player).setAttached(ARCHETYPE, archetype.id());
	}

	/** Back to unpicked, so the picker opens again. Creative-only, for testing. */
	public static void clear(final Player player) {
		((AttachmentTarget) player).removeAttached(ARCHETYPE);
	}
}
