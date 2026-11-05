package dev.xyat.taczworkshop.crafting;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MaterialPlannerTest {
    private static MaterialPlanner.Stock stock(long count, int tier, boolean... matches) {
        return new MaterialPlanner.Stock(count, tier, matches);
    }
    @Test void consumesRsThenPlayerThenBackpack() {
        assertArrayEquals(new long[]{3,2,0}, MaterialPlanner.allocate(new long[]{5},
                List.of(stock(3,0,true), stock(4,1,true), stock(8,2,true))));
    }
    @Test void noRsStillPrefersPlayerInventory() {
        assertArrayEquals(new long[]{3,2}, MaterialPlanner.allocate(new long[]{5},
                List.of(stock(3,1,true), stock(8,2,true))));
    }
    @Test void overlappingInputsCannotSpendOneItemTwice() {
        assertNull(MaterialPlanner.allocate(new long[]{1,1}, List.of(stock(1,0,true,true))));
    }
    @Test void broadInputDoesNotStealOnlyMatchOfSpecificInput() {
        assertArrayEquals(new long[]{1,1}, MaterialPlanner.allocate(new long[]{1,1},
                List.of(stock(1,0,true,true), stock(1,1,true,false))));
    }
    @Test void rsPrioritySurvivesOverlappingAlternatives() {
        assertArrayEquals(new long[]{1,0,1}, MaterialPlanner.allocate(new long[]{1,1},
                List.of(stock(1,0,true,false), stock(2,1,true,false), stock(1,2,false,true))));
    }
    @Test void largeCountsDoNotOverflowOrExpandOneNodePerItem() {
        assertArrayEquals(new long[]{3_000_000_000L}, MaterialPlanner.allocate(new long[]{1_500_000_000L,1_500_000_000L},
                List.of(stock(3_000_000_000L,0,true,true))));
    }
    @Test void negativeRequirementsAreRejected() {
        assertNull(MaterialPlanner.allocate(new long[]{-1}, List.of(stock(5,0,true))));
    }
}
