package pl.s16.dungeons;

import java.util.List;

/** Test every shaft is included and surveyed before a build transaction. */
public final class ShaftSiteAuditSelfTest {
    static void reject(List<DungeonMultiEntrancePlan.Site> sites,int expected,int radius) {
        try {DungeonMultiEntrancePlan.validateSites(sites,expected,66,-64,320,radius);}
        catch(IllegalArgumentException correct) {return;}
        throw new AssertionError("Zly zestaw szybikow powinien byc odrzucony");
    }
    public static void main(String[] args) {
        var first=new DungeonMultiEntrancePlan.Site(0,10,10,90);
        var second=new DungeonMultiEntrancePlan.Site(3,30,30,91);
        var third=new DungeonMultiEntrancePlan.Site(7,60,60,89);
        DungeonMultiEntrancePlan.validateSites(List.of(first,second,third),3,66,-64,320,3500);
        reject(List.of(first,second),3,3500); // Missing prepared shaft
        reject(List.of(first,first),2,3500); // Duplicate room
        reject(List.of(first,new DungeonMultiEntrancePlan.Site(9,15,15,90)),2,3500); // Overlapping 7x7 surveys
        reject(List.of(first,new DungeonMultiEntrancePlan.Site(9,3501,0,90)),2,3500); // Outside radius
        reject(List.of(first,new DungeonMultiEntrancePlan.Site(9,90,90,75)),2,3500); // Too shallow
        reject(List.of(first,new DungeonMultiEntrancePlan.Site(9,90,90,200)),2,3500); // Too deep
        reject(List.of(),0,3500);
        System.out.println("PASS: ShaftSiteAuditSelfTest (8 cases)");
    }
}
