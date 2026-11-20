package minepiece.noah.client.haki;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Particules du Haki (textures dédiées, pas de couleurs vanilla) :
 * - BOLT / ARC / CLASH : éclairs noirs et rouges, très brefs, qui changent de forme et d'orientation à chaque tick ;
 * - DISCHARGE : impulsion qui s'agrandit d'un coup ; AURA : petits arcs autour des membres ;
 * - PRESSURE : volute sombre translucide qui tourne et s'étale (distorsion de l'air) ;
 * - DARK : énergie sombre qui dérive (activation de l'Armement) ;
 * - GHOST : silhouette fantôme qui s'efface (traînée d'esquive, Vision du futur).
 */
public class HakiParticle extends SingleQuadParticle {
	enum Kind { BOLT, ARC, DISCHARGE, AURA, CLASH, PRESSURE, DARK, GHOST }

	private final Kind kind;
	private final SpriteSet sprites;
	private final float baseSize;
	private final float spin;

	protected HakiParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.kind = kind;
		this.sprites = sprites;
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		float r = this.random.nextFloat();
		this.spin = (this.random.nextFloat() - 0.5F) * 0.2F;
		switch (kind) {
			case BOLT -> { this.lifetime = 3 + this.random.nextInt(3); this.baseSize = 0.35F + r * 0.45F; this.friction = 0.6F; }
			case ARC -> { this.lifetime = 2 + this.random.nextInt(3); this.baseSize = 0.25F + r * 0.35F; this.friction = 0.6F; }
			case CLASH -> { this.lifetime = 3 + this.random.nextInt(3); this.baseSize = 1.4F + r * 1.6F; this.friction = 0.5F; }
			case DISCHARGE -> { this.lifetime = 6; this.baseSize = 0.6F + r * 0.5F; this.friction = 0.5F; }
			case AURA -> { this.lifetime = 2 + this.random.nextInt(3); this.baseSize = 0.12F + r * 0.14F; this.friction = 0.8F; }
			case PRESSURE -> { this.lifetime = 14 + this.random.nextInt(8); this.baseSize = 0.8F + r * 0.6F; this.friction = 0.92F; }
			case DARK -> { this.lifetime = 12 + this.random.nextInt(10); this.baseSize = 0.08F + r * 0.08F; this.friction = 0.9F; }
			default -> { this.lifetime = 16; this.baseSize = 1.0F; this.friction = 0.0F; this.xd = 0; this.yd = 0; this.zd = 0; }
		}
		this.quadSize = this.baseSize;
		this.roll = kind == Kind.GHOST ? 0.0F : this.random.nextFloat() * 6.2832F;
		this.oRoll = this.roll;
		this.setColor(1.0F, 1.0F, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			return;
		}
		this.setSpriteFromAge(this.sprites);
		float life = this.age / (float) this.lifetime;
		this.oRoll = this.roll;
		switch (this.kind) {
			case BOLT, ARC, CLASH, AURA -> {
				// grésille : nouvelle orientation et nouvelle taille à chaque tick
				this.roll = this.random.nextFloat() * 6.2832F;
				this.oRoll = this.roll;
				this.quadSize = this.baseSize * (0.7F + this.random.nextFloat() * 0.6F);
				this.alpha = this.random.nextFloat() < 0.15F ? 0.4F : 1.0F;
			}
			case DISCHARGE -> {
				this.quadSize = this.baseSize * (1.0F + life * 2.5F);
				this.alpha = 1.0F - life;
			}
			case PRESSURE -> {
				this.roll += this.spin;
				this.quadSize = this.baseSize * (1.0F + life * 1.8F);
				this.alpha = (1.0F - life) * 0.8F;
			}
			case DARK -> this.alpha = 1.0F - life * life;
			default -> this.alpha = (1.0F - life) * 0.9F;
		}
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return this.kind == Kind.DARK || this.kind == Kind.PRESSURE ? super.getLightCoords(partialTick) : 0xF000F0;
	}

	private abstract static class Base implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;
		private final Kind kind;

		Base(SpriteSet sprites, Kind kind) {
			this.sprites = sprites;
			this.kind = kind;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
				double xd, double yd, double zd, RandomSource random) {
			return new HakiParticle(level, x, y, z, xd, yd, zd, this.sprites, this.kind);
		}
	}

	public static final class Bolt extends Base {
		public Bolt(SpriteSet s) {
			super(s, Kind.BOLT);
		}
	}

	public static final class Arc extends Base {
		public Arc(SpriteSet s) {
			super(s, Kind.ARC);
		}
	}

	public static final class Discharge extends Base {
		public Discharge(SpriteSet s) {
			super(s, Kind.DISCHARGE);
		}
	}

	public static final class Aura extends Base {
		public Aura(SpriteSet s) {
			super(s, Kind.AURA);
		}
	}

	public static final class Clash extends Base {
		public Clash(SpriteSet s) {
			super(s, Kind.CLASH);
		}
	}

	public static final class Pressure extends Base {
		public Pressure(SpriteSet s) {
			super(s, Kind.PRESSURE);
		}
	}

	public static final class Dark extends Base {
		public Dark(SpriteSet s) {
			super(s, Kind.DARK);
		}
	}

	public static final class Ghost extends Base {
		public Ghost(SpriteSet s) {
			super(s, Kind.GHOST);
		}
	}
}
