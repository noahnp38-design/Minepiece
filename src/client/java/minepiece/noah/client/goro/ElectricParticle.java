package minepiece.noah.client.goro;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Particules électriques du Goro Goro no Mi (foudre bleue) : toujours pleinement lumineuses, elles grésillent
 * (scintillement aléatoire, images de la texture qui changent, rotation brusque des arcs).
 * - Spark  : étincelle cyan rapide, freinée, qui s'éteint en scintillant ;
 * - Blue   : électricité bleue plus douce et plus lente ;
 * - Arc    : petit arc en zigzag, très bref, tourne d'un coup à chaque tick ;
 * - Branch : grand éclair ramifié, flash de 2 à 4 ticks ;
 * - Charge : part de position + vitesse et est aspirée (accélération) vers la position (charge de l'énergie).
 */
public class ElectricParticle extends SingleQuadParticle {
	private enum Kind { SPARK, BLUE, ARC, BRANCH, CHARGE }

	private final Kind kind;
	private final SpriteSet sprites;
	private final float baseSize;
	private final double tx;
	private final double ty;
	private final double tz;
	private final double sx;
	private final double sy;
	private final double sz;

	protected ElectricParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
			SpriteSet sprites, Kind kind) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.kind = kind;
		this.sprites = sprites;
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.tx = x;
		this.ty = y;
		this.tz = z;
		this.sx = x + xd;
		this.sy = y + yd;
		this.sz = z + zd;
		float r = this.random.nextFloat();
		switch (kind) {
			case SPARK -> {
				this.xd = xd; this.yd = yd; this.zd = zd;
				this.friction = 0.86F;
				this.lifetime = 6 + this.random.nextInt(8);
				this.baseSize = 0.06F + r * 0.08F;
				this.setColor(0.75F + r * 0.25F, 0.95F, 1.0F);
			}
			case BLUE -> {
				this.xd = xd; this.yd = yd; this.zd = zd;
				this.friction = 0.9F;
				this.lifetime = 10 + this.random.nextInt(10);
				this.baseSize = 0.08F + r * 0.1F;
				this.setColor(0.55F + r * 0.25F, 0.78F + r * 0.15F, 1.0F);
			}
			case ARC -> {
				this.xd = xd * 0.3; this.yd = yd * 0.3; this.zd = zd * 0.3;
				this.friction = 0.7F;
				this.lifetime = 3 + this.random.nextInt(4);
				this.baseSize = 0.18F + r * 0.22F;
				this.setColor(1.0F, 1.0F, 1.0F);
			}
			case BRANCH -> {
				this.xd = 0; this.yd = 0; this.zd = 0;
				this.friction = 1.0F;
				this.lifetime = 2 + this.random.nextInt(3);
				this.baseSize = 0.45F + r * 0.7F;
				this.setColor(1.0F, 1.0F, 1.0F);
			}
			default -> {   // CHARGE
				this.xd = 0; this.yd = 0; this.zd = 0;
				this.friction = 1.0F;
				this.lifetime = 9 + this.random.nextInt(6);
				this.baseSize = 0.06F + r * 0.07F;
				this.setColor(0.8F + r * 0.2F, 0.95F, 1.0F);
				this.setPos(this.sx, this.sy, this.sz);
				this.xo = this.sx; this.yo = this.sy; this.zo = this.sz;
			}
		}
		this.quadSize = this.baseSize;
		this.roll = this.random.nextFloat() * 6.2832F;
		this.oRoll = this.roll;
	}

	@Override
	public void tick() {
		if (this.kind == Kind.CHARGE) {
			this.xo = this.x; this.yo = this.y; this.zo = this.z;
			if (this.age++ >= this.lifetime) {
				this.remove();
				return;
			}
			// aspirée vers la cible en accélérant (ease-in)
			double t = this.age / (double) this.lifetime;
			double k = t * t;
			this.setPos(this.sx + (this.tx - this.sx) * k, this.sy + (this.ty - this.sy) * k, this.sz + (this.tz - this.sz) * k);
		} else {
			super.tick();
			if (this.removed) {
				return;
			}
		}
		this.setSpriteFromAge(this.sprites);
		float life = this.age / (float) this.lifetime;
		// grésillement : taille et opacité qui sautent, arcs qui changent brusquement d'orientation
		float flicker = 0.55F + this.random.nextFloat() * 0.45F;
		this.alpha = Math.max(0.0F, Math.min(1.0F, flicker * (this.kind == Kind.CHARGE ? Math.min(1.0F, life * 3.0F) : 1.0F - life * 0.6F)));
		this.quadSize = this.baseSize * (this.kind == Kind.CHARGE ? 1.0F - life * 0.5F : 0.8F + this.random.nextFloat() * 0.4F);
		this.oRoll = this.roll;
		if (this.kind == Kind.ARC || this.kind == Kind.BRANCH) {
			this.roll = this.random.nextFloat() * 6.2832F;
			this.oRoll = this.roll;
		}
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return 0xF000F0;
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
			return new ElectricParticle(level, x, y, z, xd, yd, zd, this.sprites, this.kind);
		}
	}

	public static final class Spark extends Base {
		public Spark(SpriteSet sprites) {
			super(sprites, Kind.SPARK);
		}
	}

	public static final class Blue extends Base {
		public Blue(SpriteSet sprites) {
			super(sprites, Kind.BLUE);
		}
	}

	public static final class Arc extends Base {
		public Arc(SpriteSet sprites) {
			super(sprites, Kind.ARC);
		}
	}

	public static final class Branch extends Base {
		public Branch(SpriteSet sprites) {
			super(sprites, Kind.BRANCH);
		}
	}

	public static final class Charge extends Base {
		public Charge(SpriteSet sprites) {
			super(sprites, Kind.CHARGE);
		}
	}
}
