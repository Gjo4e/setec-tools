package mk.seteczaliha.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class ModelNameParserTest {
    @Test public void readsWrappedAppleModelNotInstallments() {
        List<ModelNameParser.Candidate> c=ModelNameParser.rank(Arrays.asList(
            "Лаптоп Apple",
            "MacBook NEO 13” (",
            "INDIGO )",
            "Клуб цена: 39,499 ден.",
            "2paM 12x 5590 AeH",
            "4PatM 24 x 174 AeH"
        ));
        assertFalse(c.isEmpty());
        String best=c.get(0).model.toUpperCase();
        assertTrue(best,best.contains("APPLE"));
        assertTrue(best,best.contains("MACBOOK"));
        assertTrue(best,best.contains("NEO"));
        assertTrue(best,best.contains("13"));
        assertFalse(best,best.contains("5590"));
        assertFalse(best,best.contains("12X"));
        assertTrue(ModelNameParser.likelyCorrect(c.get(0)));
    }

    @Test public void installmentPricesAreNotModels() {
        assertTrue(ModelNameParser.rank(Arrays.asList(
            "2paM 12x 5590 AeH",
            "4PatM 24 x 174 AeH",
            "Клуб цена: 39,499 ден.",
            "Шифра: 85437"
        )).isEmpty());
    }

    @Test public void readsMotherboardModelWithSlash() {
        List<ModelNameParser.Candidate> c=ModelNameParser.rank(
            Arrays.asList("ASUS", "PRO B560M-C/CSM"));
        assertFalse(c.isEmpty());
        assertTrue(c.get(0).model.contains("B560M-C/CSM"));
        assertTrue(ModelNameParser.likelyCorrect(c.get(0)));
    }

    @Test public void keepsRealMemoryModel() {
        List<ModelNameParser.Candidate> c=ModelNameParser.rank(
            Arrays.asList("Kingston FURY BEAST DDR5 16GB 6000MHz"));
        assertFalse(c.isEmpty());
        assertTrue(c.get(0).model.toUpperCase().contains("KINGSTON"));
    }

    @Test public void ignoresEmptyUnclearWords() {
        assertTrue(ModelNameParser.rank(Arrays.asList(
            "Apple", "Нова", "Многу", "Сетек"
        )).isEmpty());
    }
}
