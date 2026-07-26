package com.archetypes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.archetypes.mixin.AnimatePacketAccessor;
import com.archetypes.mixin.AvatarAccessor;
import com.archetypes.mixin.PlayerInfoUpdatePacketAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/**
 * A player that exists only on clients: Spear Phalanx's flanking spearmen.
 *
 * <h2>Why a puppet and not an entity</h2>
 * There is no server-side way to make a second player. {@code EntityTypes.PLAYER}
 * cannot be {@code create}d — the registry entry has no factory, players are
 * built by the login path — and the previous pass at this capstone worked
 * around that with two {@code ItemDisplay}s holding a spear each, which is what
 * the in-game report called "invisible clones": there were never any clones,
 * only the weapons.
 *
 * <p>So the clone is assembled the way NPC servers have always assembled one,
 * out of packets the client already knows how to obey. Nothing is spawned. The
 * server holds two integers and two UUIDs; every client in range holds a real
 * {@code RemotePlayer} it built itself.
 *
 * <h2>The sequence, and why it is in this order</h2>
 * <ol>
 * <li><b>Team.</b> {@code ClientboundSetPlayerTeamPacket} carrying a team that
 *     exists on no scoreboard anywhere — it is written from a throwaway
 *     {@link Scoreboard} and only ever travels over the wire. It does two jobs
 *     that would otherwise each need their own hack:
 *     {@code Visibility.NEVER} kills the name plate
 *     ({@code LivingEntityRenderer.shouldShowName} asks the entity's team
 *     first, and {@code Player.shouldShowName()} is a hard {@code true}, so a
 *     team is the only thing that silences it), and
 *     {@code CollisionRule.NEVER} stops the caster's own client shoving itself
 *     sideways off two player-shaped bodies standing at its shoulders.</li>
 * <li><b>Player info.</b> {@code ClientPacketListener.createEntityFromPacket}
 *     refuses a {@code PLAYER} add-entity whose UUID is not already in
 *     {@code playerInfoMap}, and builds the {@code RemotePlayer} from the
 *     profile it finds there. That profile carries the skin: the caster's own
 *     signed {@code textures} property, copied verbatim onto a fresh UUID. The
 *     signature covers the payload, not the profile it is attached to
 *     (authlib's {@code unpackTextures} validates the signature and decodes;
 *     it does not compare ids), which is why the clone wears the caster's
 *     skin rather than a random default. Only {@code ADD_PLAYER} is sent —
 *     {@code UPDATE_LISTED} is what puts an entry in the tab list, so an entry
 *     that never carries it is never listed and there is no ghost name to
 *     remove afterwards.</li>
 * <li><b>Add entity</b>, then the skin-layer byte, then equipment. The entity
 *     id comes from {@link ServerLevel#getNextEntityId()}, so it can never
 *     collide with something the server later spawns.</li>
 * </ol>
 *
 * <h2>What the clone does not have</h2>
 * No server-side body, so no hitbox, no collision, no AI, no save: the whole
 * class of bugs the previous {@code ItemDisplay} pass needed an
 * {@code EntityMixin} veto for cannot happen, because there is nothing in the
 * world to write to a region file. Gameplay has already resolved in full by the
 * time any of this is sent — a clone a client never received, or one swept
 * early, cannot cost anybody a hit.
 */
final class PhantomSpearman {
	/** Armour copied off the caster, so the clone reads as the same soldier. */
	private static final EquipmentSlot[] ARMOUR = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	private final int entityId;
	private final GameProfile profile;
	private final Vec3 at;
	private final float yRot;
	private final float xRot;

	private PhantomSpearman(final int entityId, final GameProfile profile, final Vec3 at,
			final float yRot, final float xRot) {
		this.entityId = entityId;
		this.profile = profile;
		this.at = at;
		this.yRot = yRot;
		this.xRot = xRot;
	}

	/**
	 * A clone of {@code caster}, standing at {@code at}.
	 *
	 * @param name the profile name. Never shown — the team hides the plate —
	 *             but it is the string the team's membership is keyed on, so it
	 *             has to be unique per cast or one caster's team would steal
	 *             another's entry. Capped at 16 characters by the wire codec
	 *             ({@code ByteBufCodecs.PLAYER_NAME}).
	 */
	static PhantomSpearman conjure(final ServerLevel level, final ServerPlayer caster,
			final String name, final Vec3 at, final float yRot, final float xRot) {
		GameProfile profile = new GameProfile(UUID.randomUUID(), name,
				new PropertyMap(caster.getGameProfile().properties()));

		return new PhantomSpearman(level.getNextEntityId(), profile, at, yRot, xRot);
	}

	int entityId() {
		return this.entityId;
	}

	UUID profileId() {
		return this.profile.id();
	}

	String name() {
		return this.profile.name();
	}

	/** Everything a client needs to have a spearman standing there. */
	void appendSpawn(final List<Packet<?>> out, final ServerPlayer caster, final ItemStack spear) {
		ClientboundPlayerInfoUpdatePacket info = new ClientboundPlayerInfoUpdatePacket(
				EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), List.of());
		((PlayerInfoUpdatePacketAccessor) info).archetypes$setEntries(List.of(
				new ClientboundPlayerInfoUpdatePacket.Entry(this.profile.id(), this.profile,
						false, 0, GameType.SURVIVAL, null, false, 0, null)));
		out.add(info);

		out.add(new ClientboundAddEntityPacket(this.entityId, this.profile.id(),
				this.at.x, this.at.y, this.at.z, this.xRot, this.yRot,
				EntityTypes.PLAYER, 0, Vec3.ZERO, this.yRot));

		out.add(new ClientboundSetEntityDataPacket(this.entityId, List.of(
				SynchedEntityData.DataValue.create(AvatarAccessor.archetypes$modelCustomisation(),
						skinLayers(caster)))));

		List<Pair<EquipmentSlot, ItemStack>> gear = new ArrayList<>();
		gear.add(Pair.of(EquipmentSlot.MAINHAND, spear.copyWithCount(1)));
		gear.add(Pair.of(EquipmentSlot.OFFHAND, ItemStack.EMPTY));

		for (EquipmentSlot slot : ARMOUR) {
			gear.add(Pair.of(slot, caster.getItemBySlot(slot).copy()));
		}

		out.add(new ClientboundSetEquipmentPacket(this.entityId, gear));
	}

	/**
	 * The thrust. A main-hand swing is the whole animation: a spear carries
	 * {@code SWING_ANIMATION} of type {@code STAB}, and
	 * {@code HumanoidModel.setupAttackAnimation} routes that to
	 * {@code SpearAnimations.thirdPersonAttackHand} — vanilla's own stab, driven
	 * by the swing timer the client ticks for itself. There is nothing to
	 * interpolate by hand and nothing to undo when it is over.
	 */
	Packet<?> stab(final ServerPlayer caster) {
		ClientboundAnimatePacket swing =
				new ClientboundAnimatePacket(caster, ClientboundAnimatePacket.SWING_MAIN_HAND);
		((AnimatePacketAccessor) swing).archetypes$setId(this.entityId);

		return swing;
	}

	/**
	 * The client-only team the whole formation shares: no name plates, no
	 * collision. Built on a scoreboard nobody owns, so the server's real
	 * scoreboard never learns it exists and nothing survives a disconnect.
	 */
	static Packet<?> raiseColours(final String team, final List<String> members) {
		PlayerTeam colours = new PlayerTeam(new Scoreboard(), team);
		colours.setNameTagVisibility(Team.Visibility.NEVER);
		colours.setCollisionRule(Team.CollisionRule.NEVER);
		colours.getPlayers().addAll(members);

		return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(colours, true);
	}

	static Packet<?> strikeColours(final String team) {
		return ClientboundSetPlayerTeamPacket.createRemovePacket(
				new PlayerTeam(new Scoreboard(), team));
	}

	/** Both spearmen gone in one packet each, name plates and profiles with them. */
	static void appendDespawn(final List<Packet<?>> out, final String team,
			final List<PhantomSpearman> formation) {
		int[] ids = new int[formation.size()];
		List<UUID> profiles = new ArrayList<>(formation.size());

		for (int i = 0; i < formation.size(); i++) {
			ids[i] = formation.get(i).entityId();
			profiles.add(formation.get(i).profileId());
		}

		out.add(new ClientboundRemoveEntitiesPacket(ids));
		out.add(new ClientboundPlayerInfoRemovePacket(profiles));
		out.add(strikeColours(team));
	}

	/**
	 * The caster's second skin layer, as the tracked byte. Without it the clone
	 * renders base-layer only — no hat, no jacket, no sleeves — which on most
	 * skins reads as a bald stranger rather than as the player.
	 */
	private static byte skinLayers(final ServerPlayer caster) {
		int mask = 0;

		for (PlayerModelPart part : PlayerModelPart.values()) {
			if (caster.isModelPartShown(part)) {
				mask |= part.getMask();
			}
		}

		return (byte) mask;
	}
}
