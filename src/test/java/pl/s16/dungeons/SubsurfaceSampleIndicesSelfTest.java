package pl.s16.dungeons;

import java.util.List;

public final class SubsurfaceSampleIndicesSelfTest {
    public static void main(String[] args) {
        for (int total : new int[]{1,2,3,15,31,32,256,8192,212000,999999}) {
            for (int budget : new int[]{1,2,32,128,1024,8192}) {
                List<Integer> indices=SubsurfaceSampleIndices.select(total,budget);
                int expected=Math.min(total,budget);
                if(indices.size()!=expected)throw new AssertionError("Unexpected sample size");
                int previous=-1;
                for(int index:indices) {
                    if(index<=previous||index<0||index>=total)
                        throw new AssertionError("Nonmonotonic, duplicate or out-of-bounds sample");
                    previous=index;
                }
                if(indices.size()>1 && total>budget*2) {
                    int first=indices.get(0), last=indices.get(indices.size()-1);
                    if(first>=(total+expected-1)/expected || last<total-1-(total+expected-1)/expected)
                        throw new AssertionError("Samples do not cover the full plan");
                }
            }
        }
        for(int total:new int[]{-1,0}) {
            try {SubsurfaceSampleIndices.select(total,100);throw new AssertionError("Should reject empty blueprint");}
            catch(IllegalArgumentException expected) {}
        }
        System.out.println("OK: 60 deterministic sample budgets + invalid empty plans");
    }
}
