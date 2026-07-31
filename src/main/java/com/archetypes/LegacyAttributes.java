package com.archetypes;

// STAGE 5 — the id-keyed AttributeModifier is a `>=1.21` frozen row. Below it a modifier is
// keyed by a UUID and carries a display NAME, and `AttributeInstance` has no
// `hasModifier(id)` at all (its `hasModifier` takes a whole modifier; `getModifier(UUID)`
// is the lookup). Eight files keep one transient modifier in step with a rank through the
// same four calls, so the four calls live here once rather than eight times.
//
// THE UUID IS DERIVED FROM THE MODERN ID, NOT INVENTED (design R-B8, Skill Proficiencies'
// AthleticsTicker note): `UUID.nameUUIDFromBytes(id.toString().getBytes(UTF_8))`, i.e. the
// version-3 name UUID of "archetypes:whatever". Reproducible from the id it replaces in one
// line of Java or of any other language, which is what makes it reviewable — and stable,
// which is what matters: a per-session id on a PERMANENT modifier stacks forever, and even a
// transient one would leave the previous session's copy behind on a shared attribute.
//
// The whole compilation unit is below-1.21 only (conventions §4's whole-file form), so this
// class is in exactly one jar and the four other nodes cannot move a byte because of it.
//? if <1.21 {
/*import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/^*
 * The pre-1.21 attribute-modifier vocabulary, behind the modern one's spelling.
 ^/
public final class LegacyAttributes {
	private static final Map<Identifier, UUID> IDS = new HashMap<>();

	private LegacyAttributes() {
	}

	/^* The stable UUID for a modern modifier id. ^/
	public static UUID uuid(final Identifier id) {
		return IDS.computeIfAbsent(id,
				key -> UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)));
	}

	/^* {@code AttributeInstance.hasModifier(Identifier)}. ^/
	public static boolean has(final AttributeInstance attribute, final Identifier id) {
		return attribute.getModifier(uuid(id)) != null;
	}

	/^* {@code AttributeInstance.getModifier(Identifier)}. ^/
	public static @Nullable AttributeModifier get(final AttributeInstance attribute, final Identifier id) {
		return attribute.getModifier(uuid(id));
	}

	/^* {@code AttributeInstance.removeModifier(Identifier)}. ^/
	public static void remove(final AttributeInstance attribute, final Identifier id) {
		attribute.removeModifier(uuid(id));
	}

	/^*
	 * {@code new AttributeModifier(Identifier, double, Operation)}. The display name is the
	 * id's own string — vanilla only ever shows it in the modifier's {@code toString}, and
	 * it is what makes a stray modifier in a save file traceable back to the node.
	 ^/
	public static AttributeModifier modifier(final Identifier id, final double value,
			final AttributeModifier.Operation operation) {
		return new AttributeModifier(uuid(id), id.toString(), value, operation);
	}
}
*///?}
