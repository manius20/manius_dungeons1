package pl.s16.dungeons;

import java.util.*;

public final class BossRewardSelfTest {
    public static void main(String[] args) {
        Random random=new Random(25);
        var all=BossRewardRoller.roll(random,3,1.0,List.of("diamond"));
        if(all.size()!=3||all.stream().anyMatch(r->!r.mace()))throw new AssertionError("3 Mace");
        var no=BossRewardRoller.roll(random,3,0,List.of("diamond"));
        if(no.size()!=3||no.stream().anyMatch(r->r.mace()||!"diamond".equals(r.normalItem())))throw new AssertionError("3 zwykłe nagrody");
        if(!BossRewardRoller.roll(random,3,0,List.of()).isEmpty())throw new AssertionError("Pusta pula musi być pusta");
        // Bezpieczny test statystyczny: 300k slotów, 0.01% => ok. 30 sukcesów.
        int mace=0;
        for(int i=0;i<100000;i++)
            for(var reward:BossRewardRoller.roll(random,3,0.0001,List.of("gem")))
                if(reward.mace())mace++;
        if(mace<10||mace>55)throw new AssertionError("0,01% per slot: "+mace);
        System.out.println("PASS: 3 niezależne losowania nagród, Mace 0,01% per slot: "+mace+"/300000");
    }
}
