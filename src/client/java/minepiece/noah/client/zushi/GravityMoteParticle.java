package minepiece.noah.client.zushi;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Petite particule de gravité du Zushi Zushi no Mi : minuscule, lumineuse (violet / mauve / blanc), elle descend
 * lentement vers le sol à vitesse constante (la pression gravitationnelle) en ondulant légèrement.
 * Elle disparaît immédiatement quand l'Écrasement gravitationnel qui l'a créée se termine (ClientGravityZones).
 */
public class GravityMoteParticle extends SingleQuadParticle {
	private static final float[][] COLORS = {
			{0.71F, 0.36F, 1.00F},   // violet
			{0.86F, 0.66F, 1.00F},   // mauve
			{0.97F, 0.93F, 1.00F},   // blanc lumineux
			{0.58F, 0.25F, 0.92F},   // violet profond
	};
	private final float maxAlpha;
	private final float sway;

	protected GravityMoteParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
			TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.gravity = 0.0F;
		this.friction = 1.0F;
		this.hasPhysics = false;
		this.lifetime = 28 + this.random.nextInt(24);
		this.quadSize = 0.035F + this.random.nextFloat() * 0.04F;
		float[] c = COLORS[this.random.nextInt(COLORS.length)];
		this.setColor(c[0], c[1], c[2]);
		this.maxAlpha = 0.55F + this.random.nextFloat() * 0.35F;
		this.alpha = 0.0F;
		this.sway = this.random.nextFloat() * 6.28F;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			return;
		}
		if (!ClientGravityZones.inside(this.x, this.z)) {
			this.remove();   // fin de la capacité : disparition immédiate
			return;
		}
		float t = this.age / (float) this.lifetime;
		this.alpha = t < 0.2F ? this.maxAlpha * t / 0.2F : t > 0.75F ? this.maxAlpha * (1.0F - t) / 0.25F : this.maxAlpha;
		// légère ondulation horizontale
		this.xd += Math.sin(this.age * 0.25F + this.sway) * 0.0015;
		this.zd += Math.cos(this.age * 0.25F + this.sway) * 0.0015;
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return 0xF000F0; // toujours lumineuse
	}

	public static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
				double xd, double yd, double zd, RandomSource random) {
			return new GravityMoteParticle(level, x, y, z, xd, yd, zd, this.sprites.get(random));
		}
	}
}
