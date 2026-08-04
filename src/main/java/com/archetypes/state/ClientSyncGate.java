package com.archetypes.state;

import java.lang.ref.WeakReference;

import net.minecraft.world.entity.Entity;

/**
 * "Has the server actually told this client about its own tree yet?" — one boolean, read by the
 * client before it is allowed to offer the ARCHETYPE PICKER.
 *
 * <p><b>Why this exists.</b> A client whose attached state is empty cannot tell "I have never
 * picked an archetype" apart from "I have one and nobody has sent it to me". Those two states
 * render identically — {@code ModState.get} answers null for both — and the second one puts the
 * player in front of a picker for a choice they already made. The server-side guard in
 * {@code Archetypes}' {@code PICK_ARCHETYPE} handler is what makes that harmless; this is the
 * belt that stops it being offered at all.
 *
 * <p><b>The gate is armed at RUNTIME, by the sync backend, not by a {@code //?} predicate.</b>
 * That is deliberate and it is the whole design of this class. Five of the seven nodes have a
 * platform that carries attached state across a {@code ClientboundRespawnPacket} by itself, so an
 * empty state on those really does mean "never picked" and a gate would be pure risk:
 *
 * <ul>
 * <li>the five Fabric nodes ship fabric-api's own client-side attachment transfer
 *     ({@code ClientPlayNetworkHandlerMixin} in 0.92.11 / 0.116.14,
 *     {@code ClientPacketListenerMixin} in 1.8.48 / 2.2.x) — verified by javap, and present even
 *     on the 0.92.11 pin that has no SERVER-side sync at all, which is why the legacy Fabric node
 *     is not in the same position as the Forge one despite both being below 1.20.5;</li>
 * <li>NeoForge patches {@code AttachmentSync.syncInitialPlayerAttachments} into
 *     {@code ServerPlayer.changeDimension}, {@code PlayerList.respawn} and
 *     {@code PlayerList.placeNewPlayer}.</li>
 * </ul>
 *
 * <p>{@code 1.20.1-forge} has neither, which is why its manual sync backend
 * ({@code platform/ForgeStateSync}) is the one and only caller of {@link #requireExplicitSync()}.
 * Doing it this way means no new predicate, no {@code //?} block, and — the property that matters
 * — a node whose sync is automatic keeps a gate that is open BY CONSTRUCTION rather than one held
 * open by a flag that could fail to be set.
 *
 * <p><b>Scope: client only, in effect.</b> Nothing on a server ever reads {@link #isSynced}. The
 * class lives in {@code src/main} rather than the client source set because the manual backend
 * that marks it is common-side and the client source set is not on its compile classpath — the
 * same division that already sends the decoded frame across {@code ForgeStateSync}'s
 * {@code clientSink} seam.
 *
 * <p>No {@code @Nullable} annotation anywhere in this file, on purpose: the annotation forks at
 * 1.21.1 (jspecify above, jetbrains below — conventions §5e-bis) and a nine-line utility is not
 * worth an import block on every node. Both entry points state their null handling in prose and
 * handle it.
 */
public final class ClientSyncGate {
	/**
	 * False while the platform carries attached state across a client player swap by itself, in
	 * which case {@link #isSynced} is unconditionally true and this class costs one static read.
	 */
	private static volatile boolean explicit;

	/**
	 * The player instance the last full replay was applied to. A weak reference because a
	 * {@code LocalPlayer} is discarded on every respawn packet and this must not be the thing
	 * keeping one alive; identity comparison because the whole point is to tell the current
	 * client player apart from the one it replaced.
	 */
	private static volatile WeakReference<Entity> synced = new WeakReference<>(null);

	private ClientSyncGate() {
	}

	/**
	 * Called once, from a sync backend that has no automatic transfer — today only
	 * {@code ForgeStateSync}. After this, {@link #isSynced} answers true only for a player a full
	 * replay has actually landed on.
	 */
	public static void requireExplicitSync() {
		explicit = true;
	}

	/**
	 * A full replay has been applied to this entity. Called from the client end of the manual
	 * sync channel when it sees the end-of-replay marker. A null target (the entity left between
	 * the send and the hop onto the client thread) marks nothing.
	 */
	public static void markSynced(final Entity target) {
		if (target != null) {
			synced = new WeakReference<>(target);
		}
	}

	/**
	 * Whether this client's copy of {@code player}'s attached state can be trusted to be complete
	 * — i.e. whether an ABSENT value means "the server has none" rather than "the server has not
	 * said yet". A null player is never synced.
	 */
	public static boolean isSynced(final Entity player) {
		return !explicit || (player != null && synced.get() == player);
	}
}
