package com.archetypes.platform;

// THIS WHOLE FILE IS BELOW-26.1-ONLY, and the boundary is a BUILD-MECHANICS one rather than
// an API one — the first of its kind in this port, so it is written down at length.
//
// MEASURED, on this repo, at Stage 3:
//     ./gradlew :26.2-fabric:dependencies    --configuration compileClasspath
//         net.fabricmc.fabric-api:fabric-networking-api-v1:6.3.3+72073ef09e
//     ./gradlew :26.1-fabric:dependencies    --configuration compileClasspath
//         net.fabricmc.fabric-api:fabric-networking-api-v1:6.3.1+554860db4c
//     ./gradlew :1.21.11-fabric:dependencies --configuration compileClasspath
//         remapped.…:fabric-networking-api-v1-4abd26ae-COMMON:5.1.6+6b6d71a53e
//     ./gradlew :1.21.11-fabric:dependencies --configuration clientCompileClasspath
//         remapped.…:fabric-networking-api-v1-4abd26ae-CLIENT  + …-common
//
// Every one of those module jars declares `Fabric-Loom-Split-Environment: true`. What
// changes at 26.1 is the LOOM, not the jar: `dev.kikugie.loom-back-compat` runs plain
// fabric-loom on the unobfuscated 26.x nodes, which hands `src/main` the whole mod jar, and
// fabric-loom-REMAP from 1.21.11 down, which honours the split and hands `src/main` the
// common half only. So from this node down, `src/main` cannot see
// `net.fabricmc.fabric.api.client.…` AT ALL.
//
// That is Skill Proficiencies' conventions §5g stated as a build fact rather than a rule:
// "a seam in src/main CANNOT reach client-only API". This repo had been getting away with
// the violation because 26.x's loom does not split — FabricNet's own javadoc predicted
// exactly this and named this fix.
//
// The hand-down is the shape §3.2's `Net#clientReceivers` already uses, for the same
// reason: the client owns the call, common code owns the wiring. Two methods, both
// spelled in types `src/main` can name (`CustomPacketPayload` and its `Type` are common).
//
// It stays a below-26.1 file rather than becoming the one implementation everywhere so the
// two 26.x nodes' jars do not move a byte — the port's standing regression gate. On those
// nodes FabricNet's nested `ClientSide` still makes the fabric-api call directly and this
// compilation unit holds nothing but its package statement, producing no `.class`.
//
// Class doc kept as LINE comments on purpose: a `*/` inside a disabled `//?` branch would
// close Stonecutter's own comment early (Stage-2 finding).
//? if <26.1 {
/*import java.util.function.Consumer;

//? if >=1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/^import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
^///?}

public final class ClientNetHooks {
	// The two fabric-api calls that only exist on a client, named in common types.
	//
	// STAGE 5 widened the pair rather than adding a second hook: below 1.20.5 the whole
	// payload stack is absent and fabric-api 0.92.11 speaks the RAW channel API, so what
	// crosses this line is an id and a buffer. Both shapes are still spelled in types
	// `src/main` can name, which is the only thing this interface exists to guarantee.
	public interface Calls {
		//? if >=1.20.5 {
		void send(CustomPacketPayload payload);

		<P extends CustomPacketPayload> void receive(CustomPacketPayload.Type<P> type,
				Consumer<P> sink);
		//?} else {
		/^void send(Identifier channel, FriendlyByteBuf buf);

		void receive(Identifier channel, Consumer<FriendlyByteBuf> sink);
		^///?}
	}

	// A dedicated server never installs anything and never calls either method — the two
	// entry points are FabricNet#sendToServer and FabricNet#clientReceivers, and neither is
	// reachable from a server. This stand-in is declared BEFORE the field that reads it:
	// javac rejects the other order with "illegal forward reference".
	private static final Calls ABSENT = new Calls() {
		//? if >=1.20.5 {
		@Override
		public void send(final CustomPacketPayload payload) {
			throw new IllegalStateException("archetypes: client net hooks not installed");
		}

		@Override
		public <P extends CustomPacketPayload> void receive(final CustomPacketPayload.Type<P> type,
				final Consumer<P> sink) {
			throw new IllegalStateException("archetypes: client net hooks not installed");
		}
		//?} else {
		/^@Override
		public void send(final Identifier channel, final FriendlyByteBuf buf) {
			throw new IllegalStateException("archetypes: client net hooks not installed");
		}

		@Override
		public void receive(final Identifier channel, final Consumer<FriendlyByteBuf> sink) {
			throw new IllegalStateException("archetypes: client net hooks not installed");
		}
		^///?}
	};

	private static volatile Calls calls = ABSENT;

	private ClientNetHooks() {
	}

	// Called once from client init, BEFORE Net#clientReceivers.
	public static void install(final Calls installed) {
		calls = installed;
	}

	static Calls calls() {
		return calls;
	}
}
*///?}
