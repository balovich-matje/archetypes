package com.archetypes.compat;

import com.specialities.api.SkillRegistrar;
import com.specialities.api.SkillsEntrypoint;

/**
 * Declared under {@code "specialities:skills"} in fabric.mod.json. Fabric
 * only instantiates it when Specialities itself pulls the entrypoint, so the
 * Specialities classes referenced here never load without the mod present.
 */
public final class SpellcastingEntrypoint implements SkillsEntrypoint {
	@Override
	public void registerSkills(final SkillRegistrar registrar) {
		registrar.register(SpellcastingSkill.INSTANCE);
	}
}
