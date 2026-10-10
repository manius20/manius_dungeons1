#!/usr/bin/env bash
# Compile and exercise real read-only subsurface scanner against simple API stubs.
# This is NOT an integration test with an actual Paper server.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/org/bukkit/block" "$TMP/pl/s16/dungeons"
cat > "$TMP/org/bukkit/Material.java" <<'JAVA'
package org.bukkit;
public enum Material {
    STONE,DEEPSLATE,TUFF,ANDESITE,DIORITE,GRANITE,DIRT,COARSE_DIRT,ROOTED_DIRT,
    GRAVEL,COBBLESTONE,CALCITE,DRIPSTONE_BLOCK,BLACKSTONE,BASALT,AIR,WATER,LAVA,CHEST;
    public boolean isAir(){return this==AIR;}
}
JAVA
cat > "$TMP/org/bukkit/block/Block.java" <<'JAVA'
package org.bukkit.block;
import org.bukkit.Material;
public class Block {
    private final Material type;
    public Block(Material type){this.type=type;}
    public Material getType(){return type;}
    public boolean isLiquid(){return type==Material.WATER||type==Material.LAVA;}
}
JAVA
cat > "$TMP/org/bukkit/World.java" <<'JAVA'
package org.bukkit;
import org.bukkit.block.Block;
public class World {
    public boolean missing=false;
    public Material interior=Material.STONE;
    public boolean isChunkLoaded(int x,int z){return !missing;}
    public Block getBlockAt(int x,int y,int z){return new Block(interior);}
    public int getMinHeight(){return -64;}
    public int getMaxHeight(){return 320;}
}
JAVA
cat > "$TMP/pl/s16/dungeons/SubsurfaceSurveyStubTest.java" <<'JAVA'
package pl.s16.dungeons;
import org.bukkit.World;
import org.bukkit.Material;
public class SubsurfaceSurveyStubTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        var layout=DungeonLayoutPlanner.generate(15,1,1234,0.05,0.25);
        var blueprint=DungeonBlueprint.fromLayout(layout);
        var w=new World();
        check(LiveSubsurfaceSurvey.inspect(w,blueprint,0,40,0,1024,0.4).accepted(),"natural rock accepted");
        w.interior=Material.WATER;
        check(!LiveSubsurfaceSurvey.inspect(w,blueprint,0,40,0,1024,0.4).accepted(),"fluid refused");
        w.interior=Material.CHEST;
        check(!LiveSubsurfaceSurvey.inspect(w,blueprint,0,40,0,1024,0.4).accepted(),"crafted blocks refused");
        w.interior=Material.AIR;
        check(!LiveSubsurfaceSurvey.inspect(w,blueprint,0,40,0,1024,0.4).accepted(),"large cave refused");
        w.interior=Material.STONE;
        w.missing=true;
        check(!LiveSubsurfaceSurvey.inspect(w,blueprint,0,40,0,1024,0.4).accepted(),"missing chunks refused");
        w.missing=false;
        check(!LiveSubsurfaceSurvey.inspect(w,blueprint,0,319,0,1024,0.4).accepted(),"height exceeded refused");
        System.out.println("PASS subsurface offline API stubs (6 controls)");
    }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
    "$TMP/org/bukkit/Material.java" "$TMP/org/bukkit/block/Block.java" "$TMP/org/bukkit/World.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/DungeonLayoutPlanner.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/DungeonBlueprint.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/SubsurfaceSamplePolicy.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/SubsurfaceSampleIndices.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/LiveSubsurfaceSurvey.java" \
    "$TMP/pl/s16/dungeons/SubsurfaceSurveyStubTest.java"
java -cp "$TMP" pl.s16.dungeons.SubsurfaceSurveyStubTest
