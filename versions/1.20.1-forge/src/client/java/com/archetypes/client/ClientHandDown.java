package com.archetypes.client;

import com.archetypes.compat.SpecialitiesBridge;
import com.archetypes.platform.Platform;

/**
 * THIS NODE'S {@code ClientHandDown}, as a PER-NODE OVERRIDE of the shared file.
 *
 * <p><b>Why an override and not a shared {@code //?} arm.</b> The shared
 * {@code src/client/java/com/archetypes/client/ClientHandDown.java} is a whole-file
 * {@code fabric && <26.1} unit, so on this node it compiles to nothing at all — but its
 * CALL SITE in {@code ArchetypesClient} is gated on the VERSION alone
 * ({@code if <26.1}), which is true here. Left as it stands, this node's generated client
 * init calls a class that does not exist. Providing the class from
 * {@code versions/1.20.1-forge/src/} fixes that inside this lane's own ownership instead of
 * re-forking a shared file every other node would have to be re-gated against.
 *
 * <p>Stonecutter takes a per-node source file in place of the shared one of the same path —
 * MEASURED on this node: with this file present, {@code stonecutterGenerateClient} emits no
 * {@code ClientHandDown.java} into {@code build/generated/stonecutter/}, while
 * {@code LegacyStateSyncClient.java} (which had no override at the time) was still emitted.
 * So there is exactly one definition on the compile classpath.
 *
 * <p><b>Of the shared file's two jobs, this node has one.</b>
 *
 * <ul>
 * <li>The fabric-api NET HAND-DOWN is not needed and is deliberately absent. It exists
 *     because fabric-loom-remap honours {@code Fabric-Loom-Split-Environment} and hands
 *     {@code src/main} the common half of fabric-api only, so {@code FabricNet} cannot name
 *     {@code ClientPlayNetworking}. This node has no fabric-api and no split dev jar, and
 *     {@code platform/ForgeNet} reaches its client receivers through the seam's own
 *     {@code clientReceivers} sink map — nothing in {@code src/main} names a client type
 *     there either, by construction rather than by workaround.</li>
 * <li>The {@code SpecialitiesBridge} HUD-SHIFT hand-down IS needed, unchanged and for an
 *     unchanged reason: {@code SpecialitiesClient.hudShift()} lives in Skill Proficiencies'
 *     client half and {@code compat/SpecialitiesBridge} is in {@code src/main}. Without this
 *     call the bridge keeps its {@code () -> 0} default and this node's HUD rows would sit
 *     seven pixels lower than Skill Proficiencies' raised elements — a silent
 *     collision-contract break, not an error.</li>
 * </ul>
 *
 * <p>The {@code Linked} holder is the shared file's own device, kept for the same reason:
 * naming a Skill Proficiencies class at all has to stay behind the loaded check, so the
 * class is never resolved when the mod is absent.
 */
public final class ClientHandDown {
	private ClientHandDown() {
	}

	public static void install() {
		if (Platform.INSTANCE.isModLoaded("specialities")) {
			SpecialitiesBridge.installClientHudShift(Linked::hudShift);
		}
	}

	/** Everything that names a Skill Proficiencies class, resolved only behind the check. */
	private static final class Linked {
		private Linked() {
		}

		private static int hudShift() {
			return com.specialities.client.SpecialitiesClient.hudShift();
		}
	}
}
