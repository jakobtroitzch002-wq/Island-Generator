package dev.jakob.islandgen.layout;

import java.util.SplittableRandom;

/**
 * Eine einzelne schwebende Insel. Die Form wird vollständig aus Seed + Parametern berechnet:
 * oben eine (leicht hügelige) Oberfläche, unten ein zerklüfteter, nach unten zulaufender Körper
 * mit hängenden "Zapfen" (Spikes), wie bei klassischen Floating Islands.
 */
public final class Island {
	public enum Kind { MAIN, SIDE, SATELLITE, STARTER, STRONGHOLD, TRIAL }

	public final Kind kind;
	/** Index innerhalb ihrer Art in der Gruppe (Hauptinsel 0/1, Nebeninsel 0..6). */
	public final int index;
	public final double cx, cz, radius;
	public final int topY;
	public final double thickness;
	public final String biome;
	/** Höhlen-Biom im Inneren (nur manche Hauptinseln), sonst null. */
	public final String underground;
	public final Theme theme;
	public final boolean nether;
	public final double hillAmp;
	public final double maxReach;

	// Lagune (Ozean-Inseln)
	public final boolean lagoon;
	public final int waterLevel;
	private final int lagoonFloor;
	private final double lagoonFrac;

	private final Noise noise;
	private final long seed;
	private final double ox, oz;
	private final double[] harmAmp = new double[4], harmPhase = new double[4];
	private final double[] spX, spZ, spR, spD;
	private final double[] poolX, poolZ, poolR;
	private final int[] poolDepth;
	private final int poolFluid;
	private final boolean pillar;
	private final int minBottom;

	private Island(Builder b) {
		this.kind = b.kind;
		this.index = b.index;
		this.cx = b.cx;
		this.cz = b.cz;
		this.radius = b.radius;
		this.topY = b.topY;
		this.thickness = b.thickness;
		this.biome = b.biome;
		this.underground = b.underground;
		this.theme = b.theme;
		this.nether = b.nether;
		this.hillAmp = b.hillAmp;
		this.noise = b.noise;
		this.seed = b.seed;
		this.lagoon = b.lagoon;
		this.waterLevel = b.waterLevel;
		this.lagoonFloor = b.lagoonFloor;
		this.lagoonFrac = b.lagoonFrac;
		this.pillar = b.kind == Kind.STRONGHOLD || b.kind == Kind.TRIAL;
		this.minBottom = b.minBottom;
		this.maxReach = radius * 1.32 + 1;

		SplittableRandom r = new SplittableRandom(seed);
		ox = r.nextDouble() * 4096;
		oz = r.nextDouble() * 4096;
		double rough = kind == Kind.SATELLITE ? 0.16 : 0.11;
		for (int k = 0; k < 4; k++) {
			harmAmp[k] = rough * r.nextDouble() / (1 + k * 0.6);
			harmPhase[k] = r.nextDouble() * Math.PI * 2;
		}

		int spikes = switch (kind) {
			case MAIN, STRONGHOLD, TRIAL -> 4 + r.nextInt(5);
			case SIDE -> 1 + r.nextInt(3);
			case STARTER -> 3;
			case SATELLITE -> r.nextInt(2);
		};
		spX = new double[spikes];
		spZ = new double[spikes];
		spR = new double[spikes];
		spD = new double[spikes];
		for (int i = 0; i < spikes; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius * 0.6;
			spX[i] = cx + Math.cos(a) * d;
			spZ[i] = cz + Math.sin(a) * d;
			spR[i] = radius * (0.12 + r.nextDouble() * 0.16);
			spD[i] = thickness * (0.25 + r.nextDouble() * 0.45);
		}

		int pools = 0;
		int fluid = Column.FLUID_NONE;
		if (b.poolsAllowed && theme != null && !lagoon) {
			switch (theme.pools) {
				case WATER -> { fluid = Column.FLUID_WATER; pools = kind == Kind.MAIN ? 1 + r.nextInt(2) : r.nextInt(2); }
				case WATER_MANY -> { fluid = Column.FLUID_WATER; pools = kind == Kind.MAIN ? 3 + r.nextInt(3) : 1 + r.nextInt(2); }
				case LAVA -> { fluid = Column.FLUID_LAVA; pools = kind == Kind.MAIN ? 1 + r.nextInt(2) : (r.nextInt(3) == 0 ? 1 : 0); }
				case LAVA_MANY -> { fluid = Column.FLUID_LAVA; pools = kind == Kind.MAIN ? 3 + r.nextInt(3) : 1 + r.nextInt(2); }
				default -> { }
			}
			if (kind == Kind.SATELLITE) pools = 0;
		}
		poolFluid = fluid;
		poolX = new double[pools];
		poolZ = new double[pools];
		poolR = new double[pools];
		poolDepth = new int[pools];
		for (int i = 0; i < pools; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius * 0.5;
			poolX[i] = cx + Math.cos(a) * d;
			poolZ[i] = cz + Math.sin(a) * d;
			poolR[i] = Math.min(radius * 0.3, 3 + r.nextDouble() * 4);
			poolDepth[i] = 2 + r.nextInt(2);
		}
	}

	/** Liegt die Säule (x, z) in dieser Insel? Wenn ja, wird {@code out} befüllt. */
	public boolean sample(int x, int z, Column out) {
		double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
		double d2 = dx * dx + dz * dz;
		if (d2 > maxReach * maxReach) return false;
		double d = Math.sqrt(d2);
		double theta = Math.atan2(dz, dx);
		double shape = 1;
		for (int k = 0; k < 4; k++) shape += harmAmp[k] * Math.sin((k + 2) * theta + harmPhase[k]);
		shape += 0.07 * noise.noise2((x + ox) * 0.07, (z + oz) * 0.07);
		double edge = radius * shape;
		double t = d / edge;
		if (t >= 1) return false;

		// --- Oberfläche
		double hill = noise.fbm2((x + ox) / 48.0, (z + oz) / 48.0, 3);
		double h;
		if (hillAmp > 8) {
			// Berge: spitze, hohe Gipfel zur Mitte hin
			h = hillAmp * Math.max(0, hill * 1.5 + 0.35) * (1 - t * t);
		} else {
			h = hill * hillAmp * (1 - t * t * t * t);
		}
		int top = topY + (int) Math.round(h);
		if (t > 0.82) top -= (int) ((t - 0.82) / 0.18 * 3.2);

		// --- Unterseite: zulaufender, zerklüfteter Körper
		double depth;
		if (pillar) {
			depth = thickness * (1 - Math.pow(t, 4));
		} else {
			depth = thickness * Math.pow(1 - t, 0.9);
		}
		double jag = noise.noise2((x + ox) * 0.11, (z + oz) * 0.11 + 300);
		depth *= 0.8 + 0.3 * jag;
		// sanfte, grossflächige Variation statt Einzelsäulen-Rauschen (das gab Rillen an der Unterseite)
		depth += thickness * 0.1 * noise.noise2((x + ox) * 0.045, (z + oz) * 0.045 - 500) * (1 - t);
		// kleine, unregelmässige Beulen: brechen die gleichmässigen Ringe des Kegels auf (keine Rillen)
		depth += (2.2 * noise.noise2((x + ox) * 0.21, (z + oz) * 0.21 + 900)
				+ 1.3 * noise.noise2((x + ox) * 0.47, (z + oz) * 0.47 - 900)) * Math.min(1, (1 - t) * 4);
		for (int i = 0; i < spX.length; i++) {
			double sx = x + 0.5 - spX[i], sz = z + 0.5 - spZ[i];
			double ds = Math.sqrt(sx * sx + sz * sz);
			if (ds < spR[i]) depth += spD[i] * Math.pow(1 - ds / spR[i], 1.5);
		}
		depth = Math.max(depth, 3 + (1 - t) * 6);
		int bottom = top - (int) depth;

		int solidTop = top;
		int fluid = Column.FLUID_NONE;
		int fluidTop = 0;

		// --- Lagune
		if (lagoon) {
			double lagR = edge * lagoonFrac;
			// Unter und um die Lagune bleibt der Boden dick genug (Ozeanmonument reicht bis Y 39 hinunter).
			if (d < lagR * 1.25) bottom = Math.min(bottom, lagoonFloor - 7 - (int) (4 * (1 - d / (lagR * 1.25))));
			if (d < lagR) {
				double q = d / lagR;
				int floor = lagoonFloor + (int) ((waterLevel + 1 - lagoonFloor) * Math.pow(q, 4));
				if (floor < waterLevel) {
					solidTop = Math.min(top, floor);
					fluid = Column.FLUID_WATER;
					fluidTop = waterLevel;
				}
			}
		}

		// --- Teiche / Lavaseen
		for (int i = 0; i < poolX.length && fluid == Column.FLUID_NONE; i++) {
			double px = x + 0.5 - poolX[i], pz = z + 0.5 - poolZ[i];
			double pr = poolR[i] * (1 + 0.25 * noise.noise2((x + ox) * 0.3, (z + oz) * 0.3 + i * 17));
			double pd2 = px * px + pz * pz;
			if (pd2 < pr * pr) {
				int dep = Math.max(1, (int) Math.round(poolDepth[i] * (1 - pd2 / (pr * pr)) + 0.4));
				solidTop = top - dep;
				fluid = poolFluid;
				fluidTop = top;
			}
		}

		bottom = Math.min(bottom, solidTop - 3);
		bottom = Math.max(bottom, minBottom);

		out.island = this;
		out.t = t;
		out.landTop = top;
		out.solidTop = solidTop;
		out.bottom = bottom;
		out.fluid = fluid;
		out.fluidTop = fluidTop;
		return true;
	}

	public boolean hasCaves() {
		return kind == Kind.MAIN || kind == Kind.STRONGHOLD || kind == Kind.TRIAL;
	}

	public long seed() {
		return seed;
	}

	@Override
	public String toString() {
		return kind + "#" + index + "[" + (int) cx + "," + (int) cz + " r=" + (int) radius + " top=" + topY + " " + biome + "]";
	}

	// ------------------------------------------------------------------ Builder

	public static final class Builder {
		Kind kind;
		int index;
		double cx, cz, radius;
		int topY;
		double thickness;
		String biome;
		String underground;
		Theme theme;
		boolean nether;
		double hillAmp;
		Noise noise;
		long seed;
		boolean lagoon;
		int waterLevel;
		int lagoonFloor;
		double lagoonFrac = 0.6;
		boolean poolsAllowed = true;
		int minBottom = 2;

		public Builder(Kind kind, int index, Noise noise, long seed) {
			this.kind = kind;
			this.index = index;
			this.noise = noise;
			this.seed = seed;
		}

		public Builder at(double cx, double cz, double radius) { this.cx = cx; this.cz = cz; this.radius = radius; return this; }
		public Builder height(int topY, double thickness) { this.topY = topY; this.thickness = thickness; return this; }
		public Builder biome(String biome, Theme theme) { this.biome = biome; this.theme = theme; return this; }
		public Builder underground(String b) { this.underground = b; return this; }
		public Builder nether(boolean n) { this.nether = n; return this; }
		public Builder hills(double amp) { this.hillAmp = amp; return this; }
		public Builder lagoon(int waterLevel, int floor, double frac) { this.lagoon = true; this.waterLevel = waterLevel; this.lagoonFloor = floor; this.lagoonFrac = frac; return this; }
		public Builder noPools() { this.poolsAllowed = false; return this; }
		public Builder minBottom(int y) { this.minBottom = y; return this; }

		public Island build() {
			return new Island(this);
		}
	}
}
