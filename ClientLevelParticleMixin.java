package dev.fishingcatchtimer.mixin;

import dev.fishingcatchtimer.CatchTimerController;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleMixin {
    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD")
    )
    private void fishingcatchtimer$observeFishingTrail(
            ParticleOptions particle,
            double x, double y, double z,
            double offsetX, double offsetY, double offsetZ,
            CallbackInfo callbackInfo
    ) {
        fishingcatchtimer$observe((ClientLevel) (Object) this, particle, x, y, z, offsetX, offsetY, offsetZ);
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V",
            at = @At("HEAD")
    )
    private void fishingcatchtimer$observeFishingTrailWithLimiter(
            ParticleOptions particle,
            boolean overrideLimiter,
            boolean alwaysShow,
            double x, double y, double z,
            double offsetX, double offsetY, double offsetZ,
            CallbackInfo callbackInfo
    ) {
        fishingcatchtimer$observe((ClientLevel) (Object) this, particle, x, y, z, offsetX, offsetY, offsetZ);
    }

    private static void fishingcatchtimer$observe(ClientLevel level, ParticleOptions particle,
                                                   double x, double y, double z,
                                                   double offsetX, double offsetY, double offsetZ) {
        if (particle.getType() == ParticleTypes.FISHING) {
            CatchTimerController.onFishingParticle(level, x, y, z, offsetX, offsetY, offsetZ);
        }
    }
}
