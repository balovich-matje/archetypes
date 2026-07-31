package com.archetypes.client;

// THIS WHOLE FILE IS BELOW-1.20.5-ONLY, and it is the half of R-B1 that Stage 5 shipped
// without.
//
// WHAT WAS MISSING, and how it presented. `platform/LegacyStateSync` is the whole
// attachment-sync fallback for the one node whose fabric-api cannot sync: it encodes on
// change, routes by scope, replays on start-tracking and replays on join, and its own
// javadoc calls `apply` "the client half's entry point". Nothing called it. Every one of
// those four paths was writing packets onto `archetypes:state_sync` and NO CLIENT WAS
// LISTENING — `grep -rn LegacyStateSync src/client` returned nothing at all.
//
// The failure mode is why it survived a build, a mixin audit and a dedicated-server smoke:
// there is no error anywhere. The server sends a custom payload on a channel the client
// never registered; vanilla drops an unregistered clientbound channel silently. Server-side
// state stays perfect, so every server-authoritative behaviour keeps working, and the whole
// visible half — the HUD, the tree screen, every renderer flag other players read — is
// simply blank on that node. A dedicated-server smoke cannot see this by construction, and a
// two-client session (the design's prescribed R-B1 proof) is exactly what would have.
//
// WHY IT IS A SEPARATE FILE rather than a block inside `ClientHandDown`, which is where the
// seam's own comment points. Two reasons, one mechanical and one about ownership:
//
//   * `ClientHandDown` is already a whole-file `//? if <26.1` block, so its enabled arms are
//     written at the ESCALATED marker (`/^ … ^/`). A `<1.20.5` arm inside it would be a
//     third level, and the escalation is documented for one step only. A file whose entire
//     body is one `//?` block needs no nesting at all.
//   * The hand-down exists to give `src/main` two calls it cannot NAME. This is not that:
//     resolving an entity id needs `Minecraft`, which is the one thing the seam must never
//     see, so the receiver belongs on this side of the line rather than behind it.
//
// Class doc kept as LINE comments on purpose: a `*/` inside a disabled `//?` branch would
// close Stonecutter's own comment early (Stage-2 finding).
//? if <1.20.5 {
/*import com.archetypes.platform.LegacyStateSync;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;

public final class LegacyStateSyncClient {
	private LegacyStateSyncClient() {
	}

	public static void install() {
		ClientPlayNetworking.registerGlobalReceiver(LegacyStateSync.CHANNEL,
				(client, handler, buf, responseSender) -> {
					// The raw 0.92.11 receiver runs on the NETTY thread and its buffer is
					// released the moment this method returns, so the bytes are copied
					// before anything defers — the same rule `ClientHandDown`'s own raw
					// receiver states, and the reason it is restated here is that this
					// sink defers by two hops rather than one.
					FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.copiedBuffer(buf));
					int entityId = copy.readVarInt();

					client.execute(() -> LegacyStateSync.apply(
							client.level == null ? null : client.level.getEntity(entityId),
							copy));
				});
	}
}
*///?}
