# Island Generator

Fabric-Mod für **Minecraft 26.3**: eine Void-Welt, in der vereinzelte Inselgruppen schweben.

## Was die Mod macht

- **Inselgruppen** mit 250–800 Blöcken Leere dazwischen. Jede Gruppe besteht aus 1–2 Hauptinseln,
  1–7 Nebeninseln (5–30 Blöcke daneben) und 2–5 kleinen Satelliten.
- **Höhe:** Overworld-Inseln auf Y 100–150 (Ozean-Inseln mit Lagune tiefer, auf Meereshöhe), Nether-Inseln auf Y 30–100.
- **15 Overworld-Themen:** Plains, Desert (Lavaseen), Savanna, Snowy, Taiga, Jungle, Swamp, Mangrove, Dark Forest/Pale Garden,
  Badlands, Cherry Grove, Mushroom Fields, Ice Spikes, Ozean (Lagune mit Ozeanmonument, Korallen, Strände) und Berge (Jagged Peaks).
- **Höhlenbiome** im Inneren mancher Hauptinseln: Lush Caves, Dripstone Caves, Deep Dark (mit Ancient City), Sulfur Caves.
- **Strukturen:** jede Haupt- und Nebeninsel bekommt mindestens eine passende Vanilla-Struktur
  (Dörfer, Tempel, Hexenhütte, Iglu, Aussenposten, Waldanwesen, Ozeanmonument, Trail Ruins, Camps, Ruined Portals ...).
  Trial Chambers stecken in vielen Hauptinseln.
- **Stronghold-Insel:** ein riesiger Felsbrocken etwa 1300 Blöcke vom Spawn, mit der Festung und dem Endportal im Inneren.
  Enderaugen und `/locate` funktionieren.
- **Startinsel:** Radius 20 Blöcke beim Spawn, alleine, mit Baum und kleiner Wasserquelle.
- **Viele Blöcke:** Erde, Stein, Granit/Diorit/Andesit, Tuff, Calcit, Deepslate, Kies, Lehm, Schlamm, Terracotta, Eis,
  Amethyst-Geoden und alle Erze (Diamanten sehr selten, nur tief in Hauptinseln).
- **Nether:** gleiche Inselidee ohne Bedrock-Decke und -Boden, mit Festungen, Bastionen, Fossilien und Glowstone unter den Inseln.
- **End:** unverändert.

## Installation (Server)

1. Fabric-Server für 26.3 installieren, dazu **Fabric API** in den `mods`-Ordner.
2. `island-generator-x.y.z.jar` in den `mods`-Ordner legen.
3. Server mit einer **neuen** Welt starten (alten Welt-Ordner vorher löschen oder umbenennen).

Spieler brauchen die Mod nicht, normales Minecraft 26.3 reicht.

## Download der fertigen Mod

Unter **Actions** → letzter erfolgreicher Lauf → Artefakt `island-generator-mod`.

## Für Entwickler

Bei jedem Push baut GitHub Actions die Mod und startet danach einen Test-Server, der einige Inselgruppen generiert.
Das Artefakt `selftest` enthält Karten, Seitenansichten und einen Bericht (Strukturen, Blockanzahl, Generierungszeit).
Der Test-Server akzeptiert dabei automatisch die Minecraft-EULA.
