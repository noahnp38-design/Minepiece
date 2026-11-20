package minepiece.noah.ability.giraffe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Etat du Ushi Ushi no Mi d'un joueur, synchronisé à TOUS les clients (pour voir la transformation des autres joueurs).
 * Le client ne reçoit que des instants (temps de jeu) : l'avancement des animations se calcule localement, donc il est
 * identique pour tout le monde sans paquet à chaque tick.
 *
 * @param form      forme active (voir GiraffeAnim.HUMAN / HYBRID / GIRAFFE)
 * @param prev      forme quittée (sert à la transition)
 * @param formStart temps de jeu où la transition vers "form" a commencé
 * @param anim      attaque en cours (GiraffeAnim.ANIM_*), 0 = aucune
 * @param animStart temps de jeu où l'animation d'attaque a commencé
 */
public record GiraffeState(int form, int prev, long formStart, int anim, long animStart) {
	public static final GiraffeState HUMAN = new GiraffeState(GiraffeAnim.HUMAN, GiraffeAnim.HUMAN, -10_000L, 0, -10_000L);

	public static final Codec<GiraffeState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("form").forGetter(GiraffeState::form),
			Codec.INT.fieldOf("prev").forGetter(GiraffeState::prev),
			Codec.LONG.fieldOf("form_start").forGetter(GiraffeState::formStart),
			Codec.INT.fieldOf("anim").forGetter(GiraffeState::anim),
			Codec.LONG.fieldOf("anim_start").forGetter(GiraffeState::animStart)
	).apply(i, GiraffeState::new));
	public static final StreamCodec<ByteBuf, GiraffeState> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public GiraffeState withAnim(int newAnim, long now) {
		return new GiraffeState(form, prev, formStart, newAnim, now);
	}

	public GiraffeState withForm(int newForm, long now) {
		return new GiraffeState(newForm, form, now, anim, animStart);
	}

	public GiraffeState noAnim() {
		return new GiraffeState(form, prev, formStart, 0, animStart);
	}
}
