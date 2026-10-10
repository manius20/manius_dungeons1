package pl.s16.dungeons;

import java.util.*;

/** Czysta logika szansy Mace: osobny rzut 0,01% w każdym z trzech slotów nagrody. */
public final class BossRewardRoller {
    private BossRewardRoller() {}
    public record Reward<T>(boolean mace,T normalItem) {}
    public static <T> List<Reward<T>> roll(Random random,int rolls,double maceProbability,List<T> candidates) {
        Objects.requireNonNull(random);Objects.requireNonNull(candidates);
        if (rolls<0||rolls>27||!Double.isFinite(maceProbability)||maceProbability<0||maceProbability>1)
            throw new IllegalArgumentException("Nieprawidłowa szansa lub liczba losowań");
        List<Reward<T>> results=new ArrayList<>();
        for(int i=0;i<rolls;i++) {
            if(random.nextDouble()<maceProbability)results.add(new Reward<>(true,null));
            else if(!candidates.isEmpty())results.add(new Reward<>(false,candidates.get(random.nextInt(candidates.size()))));
        }
        return List.copyOf(results);
    }
}
