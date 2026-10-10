#!/usr/bin/env bash
# Compile and execute the real read-only entrance survey against tiny OFFLINE Bukkit stubs.
# This catches Java syntax/type regressions, but is NOT a real Paper server integration test.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/org/bukkit/block" "$TMP/pl/s16/dungeons"
cat > "$TMP/org/bukkit/Material.java" <<'JAVA'
package org.bukkit;
public enum Material {GRASS_BLOCK,STONE,WATER,LAVA,AIR}
JAVA
cat > "$TMP/org/bukkit/block/Block.java" <<'JAVA'
package org.bukkit.block;
import org.bukkit.Material;
public class Block {
    private final Material type;
    public Block(Material type){this.type=type;}
    public Material getType(){return type;}
    public boolean isLiquid(){return type==Material.WATER||type==Material.LAVA;}
    public boolean isPassable(){return type==Material.AIR||isLiquid();}
}
JAVA
cat > "$TMP/org/bukkit/World.java" <<'JAVA'
package org.bukkit;
import org.bukkit.block.Block;
public class World {
    public int min=-64, max=320, base=90, liquidX=Integer.MIN_VALUE, missingCX=Integer.MIN_VALUE, roofX=Integer.MIN_VALUE;
    public boolean isChunkLoaded(int x,int z){return x!=missingCX;}
    public int getHighestBlockYAt(int x,int z){return base+(x>=50?5:0);}
    public Block getBlockAt(int x,int y,int z){
        if(x==roofX&&y==getHighestBlockYAt(x,z)+2)return new Block(Material.STONE);
        if(x==liquidX&&y==getHighestBlockYAt(x,z)+1)return new Block(Material.WATER);
        return new Block(y<=getHighestBlockYAt(x,z)?Material.GRASS_BLOCK:Material.AIR);
    }
    public int getMinHeight(){return min;}
    public int getMaxHeight(){return max;}
}
JAVA
cat > "$TMP/pl/s16/dungeons/LiveEntranceSurveyStubTest.java" <<'JAVA'
package pl.s16.dungeons;
import org.bukkit.World;
import java.util.List;
public class LiveEntranceSurveyStubTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var world=new World();
        var two=List.of(new DungeonMultiEntrancePlan.Candidate(0,0,0),
            new DungeonMultiEntrancePlan.Candidate(1,100,100));
        var ok=LiveEntranceSurvey.inspect(world,two,66,3500);
        check(ok.accepted() && ok.entries().size()==2,"two measured entrances accepted");
        check(ok.entries().get(0).y()==90 && ok.entries().get(1).y()==95,"different real heights preserved");
        world.liquidX=100;
        check(!LiveEntranceSurvey.inspect(world,two,66,3500).accepted(),"fluid refused");
        world.liquidX=Integer.MIN_VALUE;world.missingCX=6;
        check(!LiveEntranceSurvey.inspect(world,two,66,3500).accepted(),"unloaded chunks refused");
        world.missingCX=Integer.MIN_VALUE;world.roofX=100;
        check(!LiveEntranceSurvey.inspect(world,two,66,3500).accepted(),"roof refused");
        check(LiveEntranceSurvey.inspect(world,List.of(two.getFirst()),66,3500).accepted(),
            "single valid entrance accepted");
        System.out.println("PASS live entrance survey OFFLINE stubs: 2 sites, liquid, missing chunk, roof");
    }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
    "$TMP/org/bukkit/Material.java" "$TMP/org/bukkit/block/Block.java" "$TMP/org/bukkit/World.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/DungeonLayoutPlanner.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/DungeonEntrancePreview.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/DungeonMultiEntrancePlan.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/EntranceSurfaceRules.java" \
    "$ROOT/src/main/java/pl/s16/dungeons/LiveEntranceSurvey.java" \
    "$TMP/pl/s16/dungeons/LiveEntranceSurveyStubTest.java"
java -cp "$TMP" pl.s16.dungeons.LiveEntranceSurveyStubTest
