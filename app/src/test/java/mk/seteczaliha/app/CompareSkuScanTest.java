package mk.seteczaliha.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CompareSkuScanTest {
    @Test public void completeProductPageGivesOnlySetecSku(){
        String card="MIDEA LUNAR MF110W60/W-HR\nБренд MIDEA\n"+
            "Шифра: 12635\nГаранција: 60 месеци\nКлуб цена: 13.999 ден.\n"+
            "Редовна цена: 16.999 ден.\n12 рати 1201 ден.\n24 рати 618 ден.";
        assertEquals("12635",SkuExtractor.extract(card));
    }
    @Test public void priceWithoutSkuIsNeverInterpretedAsCode(){
        assertNull(SkuExtractor.extract("Клуб цена: 13.999 ден.\n"+
            "Редовна цена: 16.999 ден.\nГаранција 60 месеци\n"+
            "12 рати 1201 ден."));
    }
    @Test public void stillReadsSifraSlashKod(){
        assertEquals("77449",SkuExtractor.extract("Шифра/код: 77449\nГОРЕЊЕ"));
    }
    @Test public void unsupportedLengthIsNotAccepted(){
        assertNull(SkuExtractor.extract("Шифра: 1234567890123"));
    }
}
