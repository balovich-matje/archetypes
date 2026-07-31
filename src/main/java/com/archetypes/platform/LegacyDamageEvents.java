package com.archetypes.platform;

// STAGE 5 — `ServerLivingEntityEvents.AFTER_DAMAGE` is a `>=1.20.5` fabric-api row and
// fabric-api 0.92.11 does not have it. The whole compilation unit is below-1.20.5 only
// (conventions §4's whole-file form), so this class exists in exactly one jar — which is
// the point: on every other node the real event exists and a second definition of when
// "after damage" is would be a competing one.
//
// WHY A REGISTRY AND NOT A CALL. The consumer — SlayerCombat's Hamstring / Rend / Blade
// Dance batch — is a lambda in shared code, and design §3.4's rule is that only the
// REGISTRATION line forks while the lambda body stays ONE implementation. This gives that
// line something to fork onto with the same five-parameter shape, which is also the shape
// NeoForgeEvents/ForgeEvents will need in Stage 6.
//
// THE EVENT'S THREE SEMANTICS ARE REPRODUCED AT THE FIRE SITE, not here — see
// mixin/LivingEntityMixin.archetypes$afterDamage, which follows Skill Proficiencies'
// measured recipe (TAIL of `hurt`, the pre-armour amount, the not-dead guard, and the
// player-override coverage its first attempt got wrong). R-20 is exactly this: a re-rooted
// event has to reproduce the event's CONTRACT, not merely fire somewhere plausible.
//? if <1.20.5 {
/*import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/^*
 * {@code ServerLivingEntityEvents.AFTER_DAMAGE}, reproduced for the one node whose
 * fabric-api does not have it.
 ^/
public final class LegacyDamageEvents {
	/^* Fabric's {@code ServerLivingEntityEvents.AfterDamage}, parameter for parameter. ^/
	public interface AfterDamage {
		void afterDamage(LivingEntity entity, DamageSource source, float baseDamage,
				float damageTaken, boolean blocked);
	}

	private static final List<AfterDamage> LISTENERS = new ArrayList<>();

	private LegacyDamageEvents() {
	}

	/^* Called from common init, exactly where the real event's {@code register} is called. ^/
	public static void register(final AfterDamage listener) {
		LISTENERS.add(listener);
	}

	/^* Called from the mixin that owns the injection point. ^/
	public static void fireAfterDamage(final LivingEntity entity, final DamageSource source,
			final float baseDamage, final float damageTaken, final boolean blocked) {
		for (int i = 0; i < LISTENERS.size(); i++) {
			LISTENERS.get(i).afterDamage(entity, source, baseDamage, damageTaken, blocked);
		}
	}
}
*///?}
