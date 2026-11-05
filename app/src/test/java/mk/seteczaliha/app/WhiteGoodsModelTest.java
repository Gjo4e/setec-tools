package mk.seteczaliha.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class WhiteGoodsModelTest {
    @Test public void readMideaModelFromThreeOcrLines() {
        List<ModelNameParser.Candidate> names=ModelNameParser.rank(Arrays.asList(
            "MIDEA", "LUNAR", "MF110W70/W HR", "Клуб цена: 14.399 ден."
        ));
        assertFalse(names.isEmpty());
        assertTrue(names.get(0).model.toUpperCase().contains("MF110W70"));
        assertTrue(names.get(0).model.toUpperCase().contains("MIDEA"));
        assertTrue(ModelNameParser.likelyCorrect(names.get(0)));
    }
    @Test public void splitHyphenAndSlashAreEquivalentForMatch() {
        String scan="MIDEA LUNAR MF110W70/W HR";
        assertEquals("MF110W70WHR",SetecModelMatcher.modelCode(scan));
        assertEquals("MF110W70WHR",SetecModelMatcher.modelCode("MIDEA LUNAR MF110W70/W-HR"));
        assertTrue(SetecModelMatcher.score(scan,"MIDEA LUNAR MF110W70/W-HR")>=100);
        assertEquals(-1,SetecModelMatcher.score(scan,"MIDEA LUNAR MF110W60/W-HR"));
        assertEquals(-1,SetecModelMatcher.score(scan,"MIDEA LUNAR MF110W70B/W-HR"));
    }
    @Test public void alternateOfficialUrlSlugBasedOnExactModel() {
        assertEquals("midea-lunar-mf110w70-w-hr",
            SetecModelMatcher.possibleOfficialSlug("MIDEA LUNAR MF110W70/W HR"));
        assertTrue(SetecModelMatcher.searchTerms("MIDEA LUNAR MF110W70/W HR").contains("MF110W70"));
    }
    @Test public void doNotTreatRamCapacityAsModel() {
        assertEquals("TB336FU",SetecModelMatcher.modelCode(
            "Lenovo Idea TAB TB336FU + Lenovo Tab Pen 8GB/128GB"));
        assertTrue(SetecModelMatcher.score("Lenovo Idea Tab TB336FU",
            "Lenovo Idea TAB TB336FU + Lenovo Tab Pen 8GB/128GB")>=100);
    }
    @Test public void genericWhirlpoolCodeStillMatchesWithoutMarketingWords() {
        assertTrue(SetecModelMatcher.score("Whirlpool W7 W845WB",
            "WHIRLPOOL W7 W845WB - Машина за перење алишта")>=100);
    }
}
