package dev.jakob.islandgen.layout;

/**
 * Ergebnis einer Säulen-Abfrage: welche Insel liegt an (x, z) und von wo bis wo ist sie fest.
 * Wiederverwendbares, veränderliches Objekt (nicht zwischen Threads teilen).
 */
public final class Column {
	public static final int FLUID_NONE = 0, FLUID_WATER = 1, FLUID_LAVA = 2;

	public Island island;
	/** Unterster fester Block. */
	public int bottom;
	/** Oberster fester Block. */
	public int solidTop;
	/** Oberste Flüssigkeit (nur wenn fluid != NONE), Flüssigkeit liegt in solidTop+1 .. fluidTop. */
	public int fluidTop;
	public int fluid;
	/** Normierte Entfernung zur Inselmitte (0 = Mitte, 1 = Rand). */
	public double t;
	/** Gelände-Oberkante ohne Teich/Lagune (für Material-Tiefen). */
	public int landTop;

	public int highest() {
		return fluid != FLUID_NONE ? fluidTop : solidTop;
	}

	public void clear() {
		island = null;
		fluid = FLUID_NONE;
	}
}
