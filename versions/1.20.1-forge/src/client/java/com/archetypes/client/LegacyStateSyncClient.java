package com.archetypes.client;

import com.archetypes.platform.ForgeStateSync;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/**
 * THIS NODE'S {@code LegacyStateSyncClient}, as a PER-NODE OVERRIDE of the shared file —
 * the client half of the attached-state sync, which on this loader is the ONLY way any
 * client ever learns the 47 synced keys.
 *
 * <p><b>Why an override.</b> The shared file is a whole-file {@code fabric && <1.20.5} unit
 * and compiles to nothing here, while its call site in {@code ArchetypesClient} is gated on
 * the VERSION alone ({@code if <1.20.5}), which is true on this node. The name is the call
 * site's, not a claim that anything here is fabric-flavoured.
 *
 * <p><b>This is now the node's ONLY per-node Java override.</b> Stage 6b shipped a second
 * one, {@code ClientHandDown}, for the same mismatch; the Stage-6 integration merge deleted
 * it, because Stage 6a fixed that one at the SHARED file instead — the call site in
 * {@code ArchetypesClient} and {@code SpecialitiesBridge}'s installer both became
 * {@code fabric && <26.1}, so on this node the hand-down is not called at all and
 * {@code SpecialitiesBridge.Linked.hudShift} takes the direct
 * {@code SpecialitiesClient.hudShift()} call. Confirmed by build rather than assumed:
 * {@code :1.20.1-forge:compileJava} resolves that call, because Architectury Loom puts the
 * whole Skill Proficiencies Forge jar on {@code src/main}'s compile classpath — there is no
 * split-environment jar on this loader, which is exactly the fact Stage 6a's rescoping
 * turned on. The same rescoping could NOT fix this file: this node genuinely needs a client
 * receiver, it just cannot be the fabric-api one.
 *
 * <p><b>Why the entity is resolved on this side of the seam.</b> Reading
 * {@code Minecraft.getInstance().level.getEntity(id)} is the one thing
 * {@code com.archetypes.platform} must never do (conventions §5g), so the server half hands
 * the frame over as raw bytes and this class turns an id into an entity. That is exactly the
 * division the Fabric node in the same position makes.
 *
 * <p><b>WHAT GOES WRONG IF THIS IS MISSING, and why no automated gate can see it</b> —
 * measured on that Fabric node, which shipped a stage without it: there is no error
 * anywhere. The server sends on a channel the client never listens to, every
 * server-authoritative behaviour keeps working, and the HUD, the tree screen and every
 * renderer flag other players read are simply blank. A dedicated-server smoke cannot see it
 * by construction; a two-client session is what does.
 *
 * <p>The buffer handed to the sink is built over the mod's own byte array (see
 * {@code ForgeStateSync.handle}), so deferring the read of it onto the client thread is
 * safe — which is the whole reason the hop below can happen after the entity lookup rather
 * than before it.
 */
public final class LegacyStateSyncClient {
	private LegacyStateSyncClient() {
	}

	public static void install() {
		ForgeStateSync.clientSink(buf -> {
			// The frozen format's first field. Read here, on the network thread, because the
			// rest of the frame is read inside `apply` and both have to see the same cursor.
			int entityId = buf.readVarInt();
			Minecraft client = Minecraft.getInstance();

			client.execute(() -> ForgeStateSync.apply(
					client.level == null ? null : client.level.getEntity(entityId), buf));
		});
	}
}
