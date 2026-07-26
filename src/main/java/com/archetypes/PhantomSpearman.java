package com.archetypes;

import java.nio.charset.StandardCharsets;
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
 *
 * <h2>Why the identities are derived and not random</h2>
 * A clone's profile id and profile name are a pure function of the caster's
 * UUID and which shoulder it stands at, so a given player's left spearman is
 * the same profile on the tenth cast as on the first. That is not tidiness, it
 * is the fix for a leak that only a viewer's client could see.
 *
 * <p>{@code ClientPacketListener.handleAddEntity} ends by putting every add of
 * a {@code Player} into {@code seenPlayers} (the profile is already in
 * {@code playerInfoMap} by then, which is the whole reason step 2 exists), and
 * <b>nothing takes it back out</b>:
 * {@code handlePlayerInfoRemove} clears {@code playerInfoMap} and
 * {@code listedPlayers} and calls {@code PlayerSocialManager.removePlayer}, but
 * never touches {@code seenPlayers}; the only other writes to that map are
 * {@code handleConfigurationStart} (a server switch) and the
 * {@code getSeenPlayers()} the Social Interactions list and the pause screen
 * read. So a despawn cannot undo the entry — the map is append-only for the
 * life of a connection, by design, because "players you have seen this session"
 * is exactly what a report screen wants.
 *
 * <p>With random ids that meant every cast added two more strangers wearing the
 * caster's skin to every nearby player's Social Interactions list, forever.
 * Derived ids cap it: a caster contributes <b>two</b> entries per viewer per
 * connection no matter how many times they cast. Those two do remain until the
 * viewer disconnects, and no server-side packet exists that can remove them —
 * that residual is the price of the puppet and is accepted. Naming them after
 * the caster ({@code <caster>_L} / {@code <caster>_R}) is the other half of
 * accepting it: what is left in the list reads as the player who cast it
 * instead of as an unexplained stranger.
 */
final class PhantomSpearman {
	/** Armour copied off the caster, so the clone reads as the same soldier. */
	private static final EquipmentSlot[] ARMOUR = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	/**
	 * Namespace for the derived profile ids. It is only ever hashed, never
	 * shown; it is spelled out so the derivation cannot collide with another
	 * mod's name-based UUIDs by accident.
	 */
	private static final String ID_NAMESPACE = "archetypes:phalanx:";

	/**
	 * How much of the caster's name a clone's name may carry.
	 *
	 * <p>Profile names are wire-capped at 16 characters
	 * ({@code ByteBufCodecs.PLAYER_NAME} is {@code stringUtf8(16)}, verified in
	 * the jar) and the suffix costs two, so thirteen is the whole budget.
	 * Vanilla names run to 16, so a name CAN be truncated into another's — two
	 * casters sharing their first thirteen characters would key their clones on
	 * the same team membership strings. The profile ids stay distinct (they are
	 * derived from the full caster UUID), so the formations never merge; the
	 * worst case is that one caster's despawn drops the other's clones out of a
	 * team for the last few ticks of a 12-tick life and a name plate flickers.
	 * Accepted over hashing the name into unreadability, which would give back
	 * the "unexplained stranger" this naming exists to avoid.
	 */
	private static final int NAME_BUDGET = 13;

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
	 * <p>The entity id is fresh every cast ({@code getNextEntityId} is the only
	 * safe source and it never repeats), but the PROFILE — id and name both —
	 * is derived from the caster and the shoulder, so the client's
	 * append-only {@code seenPlayers} map gains at most two entries per caster
	 * per connection. See the class javadoc.
	 *
	 * @param side {@code 'L'} or {@code 'R'}: which shoulder, and the only
	 *             thing that separates a caster's two clones
	 */
	static PhantomSpearman conjure(final ServerLevel level, final ServerPlayer caster,
			final char side, final Vec3 at, final float yRot, final float xRot) {
		GameProfile profile = new GameProfile(profileId(caster.getUUID(), side),
				profileName(caster.getGameProfile().name(), side),
				new PropertyMap(caster.getGameProfile().properties()));

		return new PhantomSpearman(level.getNextEntityId(), profile, at, yRot, xRot);
	}

	/**
	 * The stable profile id for one shoulder of one caster's formation. Type-3
	 * (name-based) rather than random, so it is the same UUID on every cast and
	 * cannot be mistaken for an authenticated account id.
	 */
	private static UUID profileId(final UUID caster, final char side) {
		return UUID.nameUUIDFromBytes(
				(ID_NAMESPACE + caster + ":" + side).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * The caster's name, cut to {@link #NAME_BUDGET} and reduced to
	 * {@code [A-Za-z0-9_]}, plus {@code _L} or {@code _R}. Fifteen characters at
	 * most, so the 16-character wire cap is respected by construction rather
	 * than by trusting the caller's name to be a vanilla one — an offline or
	 * proxied login can hand us anything.
	 */
	private static String profileName(final String caster, final char side) {
		StringBuilder safe = new StringBuilder(NAME_BUDGET + 2);

		for (int i = 0; i < caster.length() && safe.length() < NAME_BUDGET; i++) {
			char c = caster.charAt(i);
			boolean plain = c == '_'
					|| (c >= '0' && c <= '9')
					|| (c >= 'A' && c <= 'Z')
					|| (c >= 'a' && c <= 'z');
			safe.append(plain ? c : '_');
		}

		// A name that sanitised away to nothing still has to be a name: the
		// string is the team's membership key and an empty one would key both
		// shoulders of every such caster together.
		if (safe.length() == 0) {
			safe.append('_');
		}

		return safe.append('_').append(side).toString();
	}

	/**
	 * The formation's team name, derived the same stable way. Team packets are
	 * add-or-modify ({@code createAddOrModifyPacket(colours, true)}), so
	 * re-sending the same name on the next cast simply re-states it. Not
	 * subject to the 16-character profile cap — the team name goes over the
	 * wire as a plain {@code writeUtf} — so it carries the whole caster UUID
	 * and cannot collide with anything.
	 */
	static String teamFor(final UUID caster) {
		return "archetypes_phalanx_" + caster;
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
