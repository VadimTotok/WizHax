package dev.wisuazt.wizhax.event;

import net.minecraft.client.particle.Particle;

public record EventParticle(Particle particle, double velocityX, double velocityY, double velocityZ) {
}
