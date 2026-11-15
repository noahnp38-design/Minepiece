package minepiece.noah.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

final class EnumCodecs {
	private EnumCodecs() {}

	static <E extends Enum<E>> Codec<E> of(Class<E> type) {
		return Codec.STRING.comapFlatMap(name -> {
			try {
				return DataResult.success(Enum.valueOf(type, name));
			} catch (IllegalArgumentException e) {
				return DataResult.error(() -> "Valeur inconnue : " + name);
			}
		}, Enum::name);
	}
}
