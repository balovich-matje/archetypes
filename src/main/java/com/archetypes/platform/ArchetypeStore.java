package com.archetypes.platform;

import java.util.List;

import com.archetypes.state.StateKey;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * Seam 1 of three (design {@code docs/MULTIVERSION.md} §3.1) — every piece of
 * state this mod hangs on an entity.
 *
 * <p><b>Deliberately NOT the shape Skill Proficiencies' {@code SkillStore} has.</b>
 * That seam names one method per value, which is right for its five. Archetypes
 * has 74 keys, 47 of them synced, read and written from ~260 call sites across 38
 * files; one method per value there is 150 interface methods and 450
 * implementations — a transcription error waiting to happen, three times over.
 * So the values are described as DATA ({@link StateKey}, in shared code with no
 * loader import in it) and this interface is the six operations on that data.
 *
 * <p>Why it is a seam at all: the Fabric attachment API changes shape below
 * 1.20.5 (fabric-api 0.92.11 has a persistent builder and <b>no sync at all</b> —
 * no {@code syncWith}, no {@code AttachmentSyncPredicate}), NeoForge has its own
 * attachment registry, and Forge 1.20.1 has capabilities with no codec, no
 * copy-on-death and no sync. Behind this interface the 260 call sites cast to
 * nothing and name nothing.
 *
 * <p><b>The balance logic does not move.</b> Only the storage does; every read and
 * write stays exactly where it was, shared by every node.
 */
public interface ArchetypeStore {
	// Three-arm BLOCK chain, per Skill Proficiencies' conventions §4: the maintained
	// template's one-line inline `elif` cannot chain, so each arm repeats the whole
	// field declaration. Each node's build script excludes the two implementations it
	// does not use from the source set; the naming rule the globs depend on is that a
	// one-loader file is named after its loader.
	//
	// STAGE 6 — the two loader arms are live. What each implementation owes the six
	// operations below is the interface's own javadoc and does not change per loader;
	// what DOES change is how much of it the platform gives for free. NeoForge has
	// attachments with real sync from 21.1.200 (the node script asserts the floor), so
	// `resyncAll`/`syncOnStartTracking` may be genuine no-ops there exactly as they are on
	// Fabric >=1.20.5. Forge 1.20.1 has capabilities: no codec, no copy-on-death and no
	// sync of ANY kind, so both replay methods are load-bearing there and
	// `platform/LegacyStateSync` — already written for the one Fabric node in the same
	// position — is the shape to reuse rather than re-derive.
	//? if fabric {
	ArchetypeStore INSTANCE = new FabricArchetypeStore();
	//?} elif neoforge {
	/*ArchetypeStore INSTANCE = new NeoForgeArchetypeStore();
	*///?} elif forge {
	/*ArchetypeStore INSTANCE = new ForgeArchetypeStore();
	*///?}

	/**
	 * Hands the platform the whole key table, once, from common init — from the exact
	 * point the Fabric-only tree's {@code ModAttachments.initialize()} occupied. Registration is
	 * eager on every platform because a world may load immediately afterwards.
	 */
	void register(List<StateKey<?>> keys);

	/** The value, or {@code null} when this entity has never had one set. */
	<T> @Nullable T get(Entity target, StateKey<T> key);

	<T> void set(Entity target, StateKey<T> key, T value);

	/**
	 * Drops the value and hands back what was there, or {@code null}.
	 *
	 * <p>Returning the old value rather than {@code void} (which is what design
	 * §3.1 sketched) is not cosmetic: {@code REFLECT_AIM} is read-and-cleared in one
	 * step inside the arrow hit handler, and every platform's remove already returns
	 * it.
	 */
	<T> @Nullable T remove(Entity target, StateKey<T> key);

	boolean has(Entity target, StateKey<?> key);

	/**
	 * Pushes every {@link StateKey.Sync#TARGET_ONLY} value this player owns to their
	 * own client.
	 *
	 * <p><b>A no-op wherever the platform syncs attachments itself</b>, which is
	 * every node registered today. It exists for the nodes that cannot: fabric-api
	 * 0.92.11 has no attachment sync and Forge 1.20.1 has none either, and on those
	 * this is the only way a client ever learns the state it draws. Called on join.
	 */
	void resyncAll(ServerPlayer player);

	/**
	 * Pushes every {@link StateKey.Sync#ALL_TRACKING} value on {@code tracked} to a
	 * viewer who has just started tracking it.
	 *
	 * <p>Sixteen keys are broadcast so that OTHER players' renderers can read them
	 * (Bulwark's aura, Ghost Armor, the Bladestorm and Decimate poses, the mark, the
	 * Deadeye trail, the night form). On a node with native broadcast sync the
	 * platform replays them itself and this is a no-op; on the three that have no
	 * such thing it is the replay, and it has no template anywhere — design R-B1.
	 * Declared here so the shared code that will drive it in Stage 5 has one name to
	 * call on every node.
	 */
	void syncOnStartTracking(Entity tracked, ServerPlayer viewer);
}
