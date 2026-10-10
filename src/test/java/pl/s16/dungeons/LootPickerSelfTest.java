package pl.s16.dungeons;

import java.util.List;
import java.util.Random;

public final class LootPickerSelfTest {
    public static void main(String[] args) {
        List<LootPicker.Entry<String>> entries=List.of(
            new LootPicker.Entry<>("sure",100),new LootPicker.Entry<>("never",0),new LootPicker.Entry<>("half",50));
        Random r=new Random(123L);
        int half=0;
        for(int i=0;i<10000;i++) {
            List<String> result=LootPicker.roll(entries,r,3);
            if(!result.contains("sure")||result.contains("never"))throw new AssertionError("Chances 100/0");
            if(result.contains("half"))half++;
        }
        if(Math.abs(half-5000)>350)throw new AssertionError("Half chance distribution: "+half);
        Random w=new Random(44);int weak=0;
        for(int i=0;i<10000;i++)if(LootPicker.chooseWeak(w,0.65))weak++;
        if(Math.abs(weak-6500)>350)throw new AssertionError("Weak pool chance: "+weak);
        if(!LootPicker.roll(entries,new Random(1),0).isEmpty())throw new AssertionError("maxItems=0");
        for(int n=15;n<=100;n++) {
            var layout=DungeonLayoutPlanner.generate(n,1,10000L+n,0.05,0.25);
            var blueprint=DungeonBlueprint.fromLayout(layout);
            var spots=TestChestLayout.plan(layout,blueprint,n);
            if(spots.size()>2*(n-1))throw new AssertionError("Too many chests");
            for(var spot:spots)if(blueprint.cells().get(new DungeonBlueprint.Pos(spot.x(),spot.y(),spot.z()))!=DungeonBlueprint.Cell.AIR)
                throw new AssertionError("Chest not in carved air: "+spot);
        }
        System.out.println("LootPickerSelfTest OK (10000 trials, 86 dungeon sizes)");
    }
}
