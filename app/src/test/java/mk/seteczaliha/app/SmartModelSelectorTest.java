package mk.seteczaliha.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class SmartModelSelectorTest {
    private static SmartModelSelector.Line line(String text,int height) {
        return new SmartModelSelector.Line(text,0,height);
    }

    @Test public void entireSetecProductPageYieldsOnlyCircledMideaModel(){
        List<ModelNameParser.Candidate> candidates=SmartModelSelector.rank(Arrays.asList(
            line("MIDEA LUNAR MF110W60/W-HR",26),
            line("Бренд:MIDEA",15),
            line("Краток опис: MIDEA LUNAR MF110W60/W-HR",14),
            line("Шифра: 12635",14),
            line("Гаранција: 60 месеци",13),
            line("Комфорт достава 599 ден. Само за Скопје",12),
            line("Клуб цена: 13.999 ден.",26),
            line("Редовна цена: 16.999 ден.",19),
            line("12 рати: 1201 ден.",14),
            line("24 рати: 618 ден.",14),
            line("Cashback поени: 420",14)
        ));
        assertEquals(1,candidates.size());
        assertEquals("MIDEA LUNAR MF110W60/W-HR",candidates.get(0).model);
    }

    @Test public void wholePrintedPriceTagDoesNotPreferLargePrice(){
        List<ModelNameParser.Candidate> candidates=SmartModelSelector.rank(Arrays.asList(
            line("Gorenje",13),
            line("GORENJE WNHVB72SDS",23),
            line("Клуб цена 14.999 ден.",42),
            line("Редовна цена 21.999 ден.",28),
            line("Шифра 77449",18),
            line("60 месеци гаранција",18),
            line("12x 5590 ден.",19)
        ));
        assertEquals("GORENJE WNHVB72SDS",candidates.get(0).model);
        assertEquals(1,candidates.size());
    }

    @Test public void separateBrandHeaderAndCodeAreJoined(){
        List<ModelNameParser.Candidate> candidates=SmartModelSelector.rank(Arrays.asList(
            line("MIDEA LUNAR",12),
            line("MF110W60/W-HR",25),
            line("Шифра 12635",12)
        ));
        assertEquals("MIDEA LUNAR MF110W60/W-HR",candidates.get(0).model);
    }

    @Test public void washedOutPriceLabelStillReadsTelefunkenModel(){
        List<ModelNameParser.Candidate> candidates=SmartModelSelector.rank(Arrays.asList(
            line("TELEFUNKEN TWM 1059V",24),
            line("15 програми",15),
            line("ЦЕНА 16.999 ден.",40)
        ));
        assertEquals("TELEFUNKEN TWM 1059V",candidates.get(0).model);
    }

    @Test public void ignoresPricesInstallmentsAndSkusWithoutModel(){
        List<ModelNameParser.Candidate> candidates=SmartModelSelector.rank(Arrays.asList(
            line("Клуб цена: 13.999 ден.",35),
            line("Шифра: 12635",24),
            line("Гаранција: 60 месеци",18),
            line("12 рати: 1201 ден.",22),
            line("24 рати: 618 ден.",21),
            line("Достапност",15)
        ));
        assertTrue(candidates.isEmpty());
    }

    @Test public void keepsMacBookFamilyFromMultipleLines(){
        List<ModelNameParser.Candidate> candidates=SmartModelSelector.rank(Arrays.asList(
            line("Лаптоп Apple",13),
            line("MacBook NEO 13",25),
            line("INDIGO",18),
            line("Клуб цена: 39,499 ден.",31)
        ));
        assertFalse(candidates.isEmpty());
        assertEquals("Apple MacBook NEO 13 INDIGO",candidates.get(0).model);
    }

    @Test public void neverMergeTwoDifferentModelCodes(){
        List<ModelNameParser.Candidate> candidates=SmartModelSelector.rank(Arrays.asList(
            line("MIDEA LUNAR MF110W60/W-HR",26),
            line("MIDEA LUNAR MF110W70/W-HR",18),
            line("Шифра 12635",15)
        ));
        assertEquals(2,candidates.size());
        assertEquals("MIDEA LUNAR MF110W60/W-HR",candidates.get(0).model);
        assertEquals("MIDEA LUNAR MF110W70/W-HR",candidates.get(1).model);
    }
}
