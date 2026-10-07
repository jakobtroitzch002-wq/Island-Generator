package dev.jakob.islandgen.layout;

import java.util.List;

/** Eine Inselgruppe in einer Rasterzelle. */
public final class Group {
	public final int cellX, cellZ;
	public final Theme theme;
	public final double centerX, centerZ;
	public final List<Island> islands;
	/** Umkreis (Mitte + Radius) um alle Inseln, für schnelles Verwerfen. */
	public final double reach;
	public final int minY, maxY;

	public Group(int cellX, int cellZ, Theme theme, double centerX, double centerZ, List<Island> islands) {
		this.cellX = cellX;
		this.cellZ = cellZ;
		this.theme = theme;
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.islands = List.copyOf(islands);
		double r = 0;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (Island i : islands) {
			r = Math.max(r, Math.hypot(i.cx - centerX, i.cz - centerZ) + i.maxReach);
			lo = Math.min(lo, (int) (i.topY - i.thickness * 1.7) - 2);
			hi = Math.max(hi, i.topY + (int) Math.ceil(i.hillAmp * 2.2) + 4);
		}
		this.reach = r;
		this.minY = lo;
		this.maxY = hi;
	}

	/** Findet die Insel, die die Säule (x, z) enthält. */
	public boolean sample(int x, int z, Column out) {
		for (int k = 0; k < islands.size(); k++) {
			if (islands.get(k).sample(x, z, out)) return true;
		}
		out.clear();
		return false;
	}

	/** Schneidet die Gruppe das Rechteck [x0,x1]x[z0,z1]? */
	public boolean intersects(int x0, int z0, int x1, int z1) {
		double nx = Math.max(x0, Math.min(centerX, x1)), nz = Math.max(z0, Math.min(centerZ, z1));
		double dx = nx - centerX, dz = nz - centerZ;
		return dx * dx + dz * dz <= reach * reach;
	}

	public Island find(Island.Kind kind, int index) {
		for (Island i : islands) if (i.kind == kind && i.index == index) return i;
		return null;
	}

	public boolean isEmpty() {
		return islands.isEmpty();
	}
}
