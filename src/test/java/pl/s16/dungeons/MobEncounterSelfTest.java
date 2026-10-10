package pl.s16.dungeons;

import java.util.*;

public final class MobEncounterSelfTest {
    public static void main(String[] args) {
        for(int n=15;n<=100;n++) {
            var layout=DungeonLayoutPlanner.generate(n,1,1000+n,0,0);
            var mobs=MobEncounterPlan.generate(layout,1000+n,0.5,0.5);
            if(mobs.size()!=Math.round(n*0.5))throw new AssertionError("Rozkład pokoi: "+n);
            var rooms=new HashSet<Integer>();int boosted=0;
            for(var mob:mobs) {
                if(mob.roomIndex()==0||!rooms.add(mob.roomIndex()))throw new AssertionError("Zdublowane albo wejściowe pokoje");
                if(mob.boosted())boosted++;
            }
            if(boosted!=Math.round(mobs.size()*0.5))throw new AssertionError("Wzmocnione moby");
            if(!mobs.equals(MobEncounterPlan.generate(layout,1000+n,0.5,0.5)))throw new AssertionError("Niedeterministyczny plan");
        }
        for(int n=15;n<=100;n++) {
            var both=DungeonLayoutPlanner.generate(n,1,n,1,1);
            var mobs=MobEncounterPlan.generate(both,n,0.5,0.5);
            if(mobs.stream().filter(e->e.kind()==MobEncounterPlan.Kind.BOSS).count()!=1
              || mobs.stream().filter(e->e.kind()==MobEncounterPlan.Kind.MINIBOSS).count()!=1)
                throw new AssertionError("Boss/miniboss powinny pojawić się pojedynczo");
            if(mobs.stream().map(MobEncounterPlan.Encounter::roomIndex).distinct().count()!=mobs.size())
                throw new AssertionError("Nakładające się spotkania");
        }
        var random=new Random(12345L);int bosses=0,mini=0;
        for(int i=0;i<100000;i++) {
            var l=DungeonLayoutPlanner.generate(15,1,random.nextLong(),0.05,0.25);
            if(l.boss())bosses++;if(l.miniboss())mini++;
        }
        if(Math.abs(bosses/100000.0-0.05)>0.005||Math.abs(mini/100000.0-0.25)>0.009)
            throw new AssertionError("Niepoprawne szanse boss/miniboss "+bosses+" / "+mini);
        System.out.println("PASS: plan mobów 86 rozmiarów, 5%/25%, brak duplikatów i wejścia");
    }
}
