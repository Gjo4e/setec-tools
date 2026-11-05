package mk.seteczaliha.app;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SetecModelMatcherTest {
    @Test public void normalizesScreenshotOcrNoise(){
        String bad="1arOH ADple MacBook NEO 13 INDIGO Apple";
        assertEquals("Apple MacBook NEO 13 INDIGO",ModelNameParser.normalizeModel(bad));
        assertEquals("Apple MacBook NEO 13 INDIGO",
                     ModelNameParser.normalizeModel("MacBook NEO 13 INDIGO Apple lanTon"));
    }
    @Test public void onlyAcceptsExactAppleVariant(){
        String q="Apple MacBook NEO 13 INDIGO";
        assertTrue(SetecModelMatcher.score(q,"Лаптоп Apple MacBook NEO 13\" ( INDIGO )")>=100);
        assertEquals(-1,SetecModelMatcher.score(q,"Лаптоп Apple MacBook NEO 13\" ( SILVER )"));
        assertEquals(-1,SetecModelMatcher.score(q,"Lenovo Idea Tab 13 INDIGO"));
    }
    @Test public void queriesAreNotGoogleSiteOperators(){
        assertEquals("MacBook NEO",SetecModelMatcher.searchTerm("Apple MacBook NEO 13 INDIGO"));
    }
    @Test public void normalLaptopPartIsRecognized(){
        assertTrue(SetecModelMatcher.score("Lenovo Idea Tab TB336FU","Lenovo Idea TAB TB336FU + Lenovo Tab Pen 8GB/128GB")>=100);
    }
}
