package pl.s16.dungeons;

import java.util.*;

/** Uruchamiany samodzielnie; nie potrzebuje serwera Minecraft. */
public final class PlannerSelfTest {
    public static void main(String[] args){
        for(int size=15;size<=100;size++){
            DungeonLayoutPlanner.Layout layout=DungeonLayoutPlanner.generate(size,10,1000L+size,0.05,0.25);
            if(layout.rooms().size()!=size)throw new AssertionError("liczba pokoi");
            if(layout.entrances().isEmpty()||layout.entrances().size()>10)throw new AssertionError("wejscia");
            Set<Integer> reached=new HashSet<>();reached.add(layout.entrances().get(0));
            boolean changed;
            do{
                changed=false;
                for(var e:layout.corridors()){
                    if(reached.contains(e.from())&&!reached.contains(e.to()))changed|=reached.add(e.to());
                    if(reached.contains(e.to())&&!reached.contains(e.from()))changed|=reached.add(e.from());
                }
            }while(changed);
            if(reached.size()!=size)throw new AssertionError("niedostepne pokoje: "+size);
            for(var room:layout.rooms()) {
                if(room.x()-room.radius()<0||room.z()-room.radius()<0||room.x()+room.radius()>layout.width()||room.z()+room.radius()>layout.length())
                    throw new AssertionError("pokoj poza wymiarami: "+size);
            }
        }
        System.out.println("OK: 86 ukladow, kazdy pokoj osiagalny i wewnatrz obszaru.");
    }
}
