package mk.seteczaliha.app;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SetecKeySpecsTest {
    private static final String PAGE="https://setec.mk/products/laptop-acer-aspire-go-15-ag15-71p-75al";

    @Test public void acerShowsOnlyImportantSpecsFromTechnicalTab(){
        String technical="Технички спецификации\n"+
            "Лаптоп ACER Aspire Go 15 (AG15-71P-75AL)\n"+
            "-Процесор Intel Core i7-13620H (24MB Cache)\n"+
            "ГрафичкаIntel UHD Graphics\n"+
            "РАМ 16GB DDR5\n"+
            "ДИСК 512GB PCIe NVMe SSD\n"+
            "2x USB Type-C / 2x USB 3.2\n"+
            "15.6\" FullHD (1920 x 1080) resolution\n";
        List<GoogleModelResearch.Fact> facts=SetecKeySpecs.extract(
            "ACER Aspire Go 15 (AG15-71P-75AL)",PAGE,technical);
        assertEquals("Should find top five specs",5,facts.size());
        assertEquals("Процесор",facts.get(0).label);
        assertEquals("RAM",facts.get(1).label);
        assertEquals("Диск",facts.get(2).label);
        assertEquals("Графичка",facts.get(3).label);
        assertEquals("Екран",facts.get(4).label);
        assertTrue(facts.stream().allMatch(f->f.url.equals(PAGE)));
    }
    @Test public void washerGetsFiveSpecsNoInstallments(){
        String txt="Технички спецификации\nМашина за перење\n9кг\n1000 вртежи\n"+
            "15 програми\nA++ Енергетска класа\nДимензии: 85 x 60 x 55 cm\n"+
            "Клуб цена: 16.999 ден.\nШифра: 84554\n12 рати: 1500 ден.";
        List<GoogleModelResearch.Fact> facts=SetecKeySpecs.extract(
            "TELEFUNKEN TWM 1059V",
            "https://www.setec.mk/products/telefunken-twm-1059v",txt);
        assertEquals(5,facts.size());
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Капацитет на перење")&&f.value.contains("9")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Центрифуга")&&f.value.contains("1000")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Програми")&&f.value.contains("15")));
        assertTrue(facts.stream().noneMatch(f->f.value.contains("16.999")||f.value.contains("1500")));
    }
    @Test public void rejectUnverifiedOrNonSetecLinks(){
        String text="РАМ 16GB DDR5\nДИСК 512GB SSD";
        assertTrue(SetecKeySpecs.extract("Acer Aspire 15","https://example.com/products/foo",text).isEmpty());
        assertTrue(SetecKeySpecs.extract("Acer Aspire 15","https://setec.mk.evil.com/products/foo",text).isEmpty());
        assertTrue(SetecKeySpecs.extract("Acer Aspire 15","https://setec.mk/search-result?q=acer",text).isEmpty());
    }
    @Test public void doNotMakeUpSpecsFromPricesOrShipping(){
        String noise="Клуб цена: 33.999 ден.\nРедовна цена: 35.999 ден.\n"+
            "24 рати: 1900 ден.\nГаранција: 24 месеци\nДостава 599 ден.";
        assertTrue(SetecKeySpecs.extract("ACER Aspire Go 15",PAGE,noise).isEmpty());
    }
}
