package pl.s16.dungeons;

import java.util.HashSet;

public final class DungeonEntrancePreviewSelfTest {
    public static void main(String[] args) {
        int trials=0;
        for(int rooms=15;rooms<=100;rooms++) {
            for(int entrances=1;entrances<=10;entrances++) {
                var layout=DungeonLayoutPlanner.generate(rooms,entrances,17L+rooms*31L+entrances,0.05,0.25);
                var proposals=DungeonEntrancePreview.forLayout(layout);
                check(proposals.size()==entrances,"entrance count "+rooms+"/"+entrances);
                check(proposals.get(0).roomIndex()==0,"entrance 0 must match physical shaft");
                var positions=new HashSet<String>();
                for(var proposed:proposals) {
                    var room=layout.rooms().get(proposed.roomIndex());
                    check(proposed.relativeX()==room.x() && proposed.relativeZ()==room.z(),"room centers");
                    check(positions.add(proposed.relativeX()+":"+proposed.relativeZ()),"duplicated entrance");
                }
                trials++;
            }
        }
        try {
            DungeonLayoutPlanner.generate(15,11,1L,0.0,0.0);
            throw new AssertionError("11 entrances must fail");
        }catch(IllegalArgumentException expected) { /* safe */ }
        System.out.println("PASS DungeonEntrancePreviewSelfTest ("+trials+" candidate plans, local coordinates only)");
    }
    private static void check(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }
}
