package minepiece.noah.crew;

import minepiece.noah.data.Faction;

/**
 * Logo d'équipage : grille de 12 x 12 pixels, chaque pixel est un numéro de couleur de la palette (0 = transparent).
 * Partagé entre le serveur (validation) et le client (dessin, éditeur).
 */
public final class CrewLogo {
	public static final int SIZE = 12;
	public static final int CELLS = SIZE * SIZE;

	/** 16 couleurs (ARGB). La 0 est transparente. */
	public static final int[] PALETTE = {
			0x00000000, 0xFF141414, 0xFFF4F4F4, 0xFF9A9A9A,
			0xFFD62828, 0xFF7A1010, 0xFFF28C28, 0xFFF6D743,
			0xFF8A5A2B, 0xFF3FAE4A, 0xFF1E6B32, 0xFF4FC3F7,
			0xFF2457C5, 0xFF14285E, 0xFF8E44AD, 0xFFF48FB1
	};

	private CrewLogo() {}

	/** true si le tableau est un logo valide (bonne taille, couleurs connues). */
	public static boolean valid(byte[] logo) {
		if (logo == null || logo.length != CELLS) {
			return false;
		}
		for (byte b : logo) {
			if (b < 0 || b >= PALETTE.length) {
				return false;
			}
		}
		return true;
	}

	public static byte[] blank() {
		return new byte[CELLS];
	}

	// ------------------------------------------------------------------ modèles de départ

	private static final String KEYS = ".KWgRrOYBGDcbNPp";

	private static final String[] PIRATE = {
			"............",
			"...WWWWWW...",
			"..WWWWWWWW..",
			"..WKKWWKKW..",
			"..WKKWWKKW..",
			"..WWWKKWWW..",
			"...WWWWWW...",
			"W...WKWKW..W",
			".WW..WWW.WW.",
			"...WW..WW...",
			".WW..WW..WW.",
			"W..........W"};

	private static final String[] MARINE = {
			"............",
			".....bb.....",
			"....b..b....",
			".....bb.....",
			"..bbbbbbbb..",
			".....bb.....",
			".....bb.....",
			"b....bb....b",
			"bb...bb...bb",
			".bb..bb..bb.",
			"..bbbbbbbb..",
			"....bbbb...."};

	private static final String[] REVOLUTIONARY = {
			"............",
			".....RR.....",
			".....RR.....",
			"....RRRR....",
			"RRRRRRRRRRRR",
			".RRRRRRRRRR.",
			"..RRRRRRRR..",
			"...RRRRRR...",
			"...RRRRRR...",
			"..RRR..RRR..",
			"..RR....RR..",
			".R........R."};

	private static final String[] FLAG = {
			"............",
			".K..........",
			".KKKKKKKKK..",
			".KKWWKKWWK..",
			".KKKKWWKKK..",
			".KKWWKKWWK..",
			".KKKKKKKKK..",
			".K..........",
			".K..........",
			".K..........",
			".K..........",
			"BBB........."};

	private static final String[] STAR = {
			"............",
			".....YY.....",
			".....YY.....",
			"....YYYY....",
			"YYYYYYYYYYYY",
			".YYYYYYYYYY.",
			"..YYYYYYYY..",
			"...YYYYYY...",
			"...YYYYYY...",
			"..YYY..YYY..",
			"..YY....YY..",
			".Y........Y."};

	/** Modèle de départ n° i (le n° 0 dépend de la faction). */
	public static byte[] preset(Faction faction, int i) {
		String[][] all = {
				faction == Faction.MARINE ? MARINE : faction == Faction.REVOLUTIONARY ? REVOLUTIONARY : PIRATE,
				PIRATE, MARINE, REVOLUTIONARY, FLAG, STAR};
		String[] rows = all[Math.floorMod(i, all.length)];
		byte[] out = new byte[CELLS];
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int k = KEYS.indexOf(rows[y].charAt(x));
				out[y * SIZE + x] = (byte) Math.max(0, k);
			}
		}
		return out;
	}

	public static int presetCount() {
		return 6;
	}

	// ------------------------------------------------------------------ texte (sauvegarde)

	public static String toHex(byte[] logo) {
		StringBuilder sb = new StringBuilder(CELLS);
		for (byte b : logo) {
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	public static byte[] fromHex(String s) {
		byte[] out = new byte[CELLS];
		if (s == null) {
			return out;
		}
		for (int i = 0; i < Math.min(CELLS, s.length()); i++) {
			int v = Character.digit(s.charAt(i), 16);
			out[i] = (byte) Math.max(0, v);
		}
		return out;
	}
}
