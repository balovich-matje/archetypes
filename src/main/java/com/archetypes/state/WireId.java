package com.archetypes.state;

/**
 * The eleven channels this mod talks over, named once — the frozen half of the
 * network contract.
 *
 * <p><b>The path strings and the field order behind them are frozen.</b> A client
 * and a server built from different nodes of the same Minecraft version have to
 * stay compatible, so an id may be added but never renamed or reordered, and the
 * bytes a channel carries may never change shape. (The one id that does not match
 * its Java name is {@link #RUSH}, whose path has always been {@code shield_rush}.)
 *
 * <p>Portable by construction: no Minecraft or loader type appears here, which is
 * what lets shared code name a channel on every node while the payload classes,
 * the codecs and the registration all stay behind {@code platform/Net}.
 */
public enum WireId {
	/** Server to client: one of your passives fired. Carries (subTreeId, family). */
	PASSIVE_PROC("passive_proc", Direction.CLIENTBOUND),
	/** Server to client: a landed parry waived your swing cooldown. Carries (ticker). */
	PARRY_SWING("parry_swing", Direction.CLIENTBOUND),

	/** Client to server: an archetype was confirmed on the picker. Carries (archetypeId). */
	PICK_ARCHETYPE("pick_archetype", Direction.SERVERBOUND),
	/** Client to server: creative-only reset. No fields. */
	RESET_ARCHETYPE("reset_archetype", Direction.SERVERBOUND),
	/** Client to server: spend a point. Carries (subTreeId, node). */
	BUY_NODE("buy_node", Direction.SERVERBOUND),
	/** Client to server: an ability key edge. Carries (slot). */
	ACTIVE_ABILITY("active_ability", Direction.SERVERBOUND),
	/** Client to server: the flamethrower channel is still held. No fields. */
	SPELL_CHANNEL("spell_channel", Direction.SERVERBOUND),
	/** Client to server: a charged combat swing began. No fields. */
	MELEE_SWING("melee_swing", Direction.SERVERBOUND),
	/** Client to server: shield rush. No fields. */
	RUSH("shield_rush", Direction.SERVERBOUND),
	/** Client to server: sprint while drawing a bow. No fields. */
	DISENGAGE("disengage", Direction.SERVERBOUND),
	/** Client to server: Ghost Form's sneak-dash. No fields. */
	NIGHT_DASH("night_dash", Direction.SERVERBOUND);

	public enum Direction {
		CLIENTBOUND, SERVERBOUND
	}

	private final String path;
	private final Direction direction;

	WireId(final String path, final Direction direction) {
		this.path = path;
		this.direction = direction;
	}

	public String path() {
		return this.path;
	}

	public Direction direction() {
		return this.direction;
	}
}
