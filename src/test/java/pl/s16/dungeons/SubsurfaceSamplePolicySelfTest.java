package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.List;

public final class SubsurfaceSamplePolicySelfTest {
    static void check(boolean result,String why){if(!result)throw new AssertionError(why);}
    public static void main(String[] args) {
        List<SubsurfaceSamplePolicy.Sample> good=new ArrayList<>();
        for(int i=0;i<80;i++)good.add(new SubsurfaceSamplePolicy.Sample("STONE",false,true,false));
        for(int i=0;i<20;i++)good.add(new SubsurfaceSamplePolicy.Sample("AIR",false,true,true));
        var ok=SubsurfaceSamplePolicy.evaluate(good,32,0.4);
        check(ok.acceptableSample()&&ok.caves()==20,"natural geology accepted");
        var fluid=new ArrayList<>(good);
        fluid.set(12,new SubsurfaceSamplePolicy.Sample("WATER",true,false,false));
        check(!SubsurfaceSamplePolicy.evaluate(fluid,32,.4).acceptableSample(),"water refused");
        var playerBlock=new ArrayList<>(good);
        playerBlock.set(10,new SubsurfaceSamplePolicy.Sample("CHEST",false,false,false));
        check(!SubsurfaceSamplePolicy.evaluate(playerBlock,32,.4).acceptableSample(),"player block refused");
        var cave=new ArrayList<>(good);
        for(int i=0;i<50;i++)cave.set(i,new SubsurfaceSamplePolicy.Sample("AIR",false,true,true));
        check(!SubsurfaceSamplePolicy.evaluate(cave,32,.4).acceptableSample(),"large cave refused");
        check(!SubsurfaceSamplePolicy.evaluate(good.subList(0,20),32,.4).acceptableSample(),"short sample refused");
        check(!SubsurfaceSamplePolicy.evaluate(null,32,.4).acceptableSample(),"null sample refused");
        boolean thrown=false;
        try{SubsurfaceSamplePolicy.evaluate(good,32,1.5);}catch(IllegalArgumentException correct){thrown=true;}
        check(thrown,"invalid threshold refused");
        System.out.println("PASS SubsurfaceSamplePolicySelfTest (7 controls)");
    }
}
