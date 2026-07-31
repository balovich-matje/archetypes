package com.archetypes.client;

// THIS WHOLE FILE IS BELOW-26.1-ONLY. It is the client half of the two hand-downs the
// remapping nodes need, and it exists for a build fact rather than an API change: from
// 1.21.11 down the node runs fabric-loom-remap, which honours a mod jar's
// `Fabric-Loom-Split-Environment` header and gives `src/main` the COMMON half only. The
// full measurement — three `dependencies --configuration compileClasspath` runs — is in
// platform/ClientNetHooks. Two things in `src/main` were reaching across that line:
//
//   1. FabricNet's nested `ClientSide`, for `ClientPlayNetworking.send` and
//      `registerGlobalReceiver`. Its own javadoc predicted this exact break.
//   2. SpecialitiesBridge's `hudShift`, which reads Skill Proficiencies'
//      `SpecialitiesClient.hudShift()` — that jar is split-environment too.
//
// Both are installed here, from client init, before anything can call them. On the two
// 26.x nodes this compilation unit holds nothing but its package statement and produces no
// `.class`, which is what keeps those jars byte-identical.
//
// Class doc kept as LINE comments on purpose: a `*/` inside a disabled `//?` branch would
// close Stonecutter's own comment early (Stage-2 finding).
//? if <26.1 {
/*import java.util.function.Consumer;

import com.archetypes.compat.SpecialitiesBridge;
import com.archetypes.platform.ClientNetHooks;
import com.archetypes.platform.Platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientHandDown {
	private ClientHandDown() {
	}

	public static void install() {
		ClientNetHooks.install(new ClientNetHooks.Calls() {
			//? if >=1.20.5 {
			@Override
			public void send(final CustomPacketPayload payload) {
				ClientPlayNetworking.send(payload);
			}

			// Does NOT touch `context.client()`, for the same reason FabricNet's 26.x arm
			// does not: the sink schedules itself onto the client thread, so nothing here
			// has to name Minecraft and the hook's signature stays common-typed.
			@Override
			public <P extends CustomPacketPayload> void receive(
					final CustomPacketPayload.Type<P> type, final Consumer<P> sink) {
				ClientPlayNetworking.registerGlobalReceiver(type,
						(payload, context) -> sink.accept(payload));
			}
			//?} else {
			/^@Override
			public void send(final net.minecraft.resources.Identifier channel,
					final net.minecraft.network.FriendlyByteBuf buf) {
				ClientPlayNetworking.send(channel, buf);
			}

			// The raw receiver runs on the netty thread and its buffer dies when this
			// method returns, so the bytes are copied before the sink is handed them —
			// the sink is what schedules the hop onto the client thread, and it must
			// still be reading OUR buffer when it gets there. `client` is in scope here
			// and deliberately unused: naming Minecraft is this class's privilege, not
			// the seam's.
			@Override
			public void receive(final net.minecraft.resources.Identifier channel,
					final Consumer<net.minecraft.network.FriendlyByteBuf> sink) {
				ClientPlayNetworking.registerGlobalReceiver(channel,
						(client, handler, buf, responseSender) -> sink.accept(
								new net.minecraft.network.FriendlyByteBuf(
										io.netty.buffer.Unpooled.copiedBuffer(buf))));
			}
			^///?}
		});

		// Guarded, and the holder below is why: naming a Skill Proficiencies class at all
		// has to stay behind the loaded check, exactly as SpecialitiesBridge#Linked does it
		// on the common side. Without the mod the supplier stays at its `() -> 0` default,
		// which is the same answer SpecialitiesBridge#hudShift already gives.
		if (Platform.INSTANCE.isModLoaded("specialities")) {
			SpecialitiesBridge.installClientHudShift(Linked::hudShift);
		}
	}

	// Everything that names a Skill Proficiencies class, loaded lazily and only behind the
	// check above.
	private static final class Linked {
		private static int hudShift() {
			return com.specialities.client.SpecialitiesClient.hudShift();
		}
	}
}
*///?}
