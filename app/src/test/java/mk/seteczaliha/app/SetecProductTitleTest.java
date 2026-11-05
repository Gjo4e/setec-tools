package mk.seteczaliha.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SetecProductTitleTest {
    @Test public void screenshotAcerTitleStopsBeforeProcessor(){
        String pageTitle="Лаптоп ACER Aspire Go 15 (AG15-42P-R844) - Процесор AMD Ryzen™ 7 5825U (16M Cache";
        assertEquals("ACER Aspire Go 15 (AG15-42P-R844)",
            SetecProductTitle.modelFromProductName(pageTitle));
    }

    @Test public void usesOnlyMainProductNameWhenMarketingInfoFollows(){
        assertEquals("ACER Aspire Go 15 (AG15-42P-R844)",
            SetecProductTitle.modelFromProductName(
                "Лаптоп ACER Aspire Go 15 (AG15-42P-R844) - RAM 16GB / SSD 512GB"));
    }

    @Test public void keepWhiteGoodsExactModelAndHyphen(){
        assertEquals("MIDEA LUNAR MF110W60/W-HR",
            SetecProductTitle.modelFromProductName(
                "MIDEA LUNAR MF110W60/W-HR - Капацитет: 7kg"));
        assertEquals("GORENJE WNHVB72SDS",
            SetecProductTitle.modelFromProductName(
                "Машина за перење GORENJE WNHVB72SDS - Гаранција: 60 месеци"));
    }

    @Test public void preserveSimpleModelAndLaptopFamily(){
        assertEquals("Apple MacBook NEO 13 (INDIGO)",
            SetecProductTitle.modelFromProductName(
                "Лаптоп Apple MacBook NEO 13 (INDIGO)"));
        assertEquals("TELEFUNKEN TWM 1059V",
            SetecProductTitle.modelFromProductName(
                "TELEFUNKEN TWM 1059V - Цена: 17.000 ден."));
    }

    @Test public void googleSearchUsesFormattedManufacturerCode(){
        String model=SetecProductTitle.modelFromProductName(
            "Лаптоп ACER Aspire Go 15 (AG15-42P-R844) - Процесор AMD Ryzen 7 5825U");
        assertEquals("AG15-42P-R844",GoogleModelResearch.formattedModelCode(model));
        assertEquals("ACER AG15-42P-R844 technical specifications",
            GoogleModelResearch.searchQuery(model));
    }

    @Test public void neverReturnUnboundedSpecDescription(){
        assertEquals("",SetecProductTitle.modelFromProductName(
            "Лаптоп "+new String(new char[120]).replace('\0','A')));
        assertEquals("",SetecProductTitle.modelFromProductName(null));
    }

    @Test public void preserveAirConditionerModelCodeFromLongMarketingTitle(){
        String title="Midea MFA2U-17HRFNX(GA)/MOX330U-18HFN8-Q(GA) "+
            "Конзолен тип на клима уред Breezeless СО ДВОЕН ГРЕАЧ НА КУЌИШТЕ И НА КОМПРЕСОР";
        assertEquals("Midea MFA2U-17HRFNX(GA)/MOX330U-18HFN8-Q(GA)",
            SetecProductTitle.modelFromProductName(title));
    }
}
