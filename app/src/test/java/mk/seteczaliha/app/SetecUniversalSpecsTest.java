package mk.seteczaliha.app;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression examples modelled on the itemized technical bullet lists at Setec. */
public final class SetecUniversalSpecsTest {
    private static String product(String path){return "https://setec.mk/products/"+path;}
    private static List<GoogleModelResearch.Fact> extract(String model,String spec,String fullTitle){
        return SetecKeySpecs.extract(model,product("test-product"),spec,"",fullTitle);
    }
    private static String get(List<GoogleModelResearch.Fact> fs,String key){
        for(GoogleModelResearch.Fact f:fs)if(key.equals(f.label))return f.value;
        return "";
    }

    @Test public void tefalToasterGetsRelevantFiveSpecsInsteadOfRam(){
        List<GoogleModelResearch.Fact> f=extract("Tefal TT420D30",
            "Tefal TT420D30\nТостер за лепчиња\nМоќност 900W\n"+
            "7 степени на работа\nОдмрзнување\nпритоплување\n"+
            "Подвижна тавичка за трошки","Tefal TT420D30");
        assertEquals("900W",get(f,"Моќност"));
        assertEquals("7",get(f,"Степени на печење"));
        assertEquals("Да",get(f,"Одмрзнување"));
        assertEquals("Да",get(f,"Тацна за трошки"));
        assertEquals("",get(f,"RAM"));
    }
    @Test public void boschToasterTwoSlicesAndNineHundredFiftyWatts(){
        List<GoogleModelResearch.Fact> f=extract("BOSCH TAT4M221",
            "BOSCH TAT4M221 Toстер за топли лепчиња\n"+
            "950W\n2 парчиња тост\nфункција за одмрзнување\n"+
            "отстранлив сад за трошки","BOSCH TAT4M221 Toстер за топли лепчиња");
        assertEquals("950W",get(f,"Моќност"));
        assertEquals("2",get(f,"Број на парчиња"));
    }
    @Test public void grillToasterHasPlatesAndPower(){
        List<GoogleModelResearch.Fact> f=extract("ST GT1800SL",
            "ST GT1800SL\nТостер\n1800W\n"+
            "нелепливи плочи што се вадат со гранитна облога\nтермостат",
            "ST GT1800SL");
        assertEquals("1800W",get(f,"Моќност"));
        assertEquals("Нелепливи",get(f,"Плочи"));
        assertEquals("Да",get(f,"Термостат"));
    }
    @Test public void philipsHairDryerGetsPowerSpeedsHeatAndIonizer(){
        List<GoogleModelResearch.Fact> f=extract("Philips BHD510/00",
            "Philips BHD510/00\nФен за коса\n2300W\n2 брзини\n"+
            "3 нивоа на топлина\nладен воздух\nЈонизатор\nДифузер",
            "Philips BHD510/00");
        assertEquals("2300W",get(f,"Моќност"));
        assertEquals("2",get(f,"Брзини"));
        assertEquals("3",get(f,"Нивоа на топлина"));
        assertEquals("Да",get(f,"Јонизатор"));
    }
    @Test public void hoodGetsAirflowSpeedsNoiseAndFilter(){
        List<GoogleModelResearch.Fact> f=extract("AKPO WK-7 MICRA",
            "AKPO WK-7 MICRA\nВграден аспиратор\n"+
            "Капацитет на одвод 300cb3\n3 брзини\n"+
            "Алуминиумски филтер\nБучност 60dB\nДимензии 53x15.5x29 цм",
            "AKPO WK-7 MICRA");
        assertEquals("300cb3",get(f,"Проток на воздух"));
        assertEquals("3",get(f,"Брзини"));
        assertEquals("60dB",get(f,"Бучава"));
        assertEquals("Метален",get(f,"Филтер"));
    }
    @Test public void klimaGetsBtuCoolingHeatingSeerScop(){
        List<GoogleModelResearch.Fact> f=extract("ST TAC-18INV32 SIBERIA",
            "ST TAC-18INV32 SIBERIA\nКлиматизер\n"+
            "18000 BTU ИНВЕРТЕР\nЛадење 5.13KW и Греење 5.23KW\n"+
            "SEER за ладење 6.3\nSCOP за греење: 4.0\nВграден WiFi",
            "ST TAC-18INV32 SIBERIA");
        assertEquals("18000 BTU",get(f,"Капацитет BTU"));
        assertEquals("5.13KW",get(f,"Ладење"));
        assertEquals("5.23KW",get(f,"Греење"));
        assertEquals("6.3",get(f,"SEER"));
        assertEquals("4.0",get(f,"SCOP"));
    }
    @Test public void blenderGetsWattsSpeedCapacityAndPrograms(){
        List<GoogleModelResearch.Fact> f=extract("Philips HR3756/00",
            "Philips HR3756/00\nВакуум Блендер\n1400W\n35000RPM\n"+
            "капацитет на садот 2.2л\n3 програми\n2 брзини и Пулс",
            "Philips HR3756/00");
        assertEquals("1400W",get(f,"Моќност"));
        assertEquals("2.2л",get(f,"Капацитет"));
        assertEquals("2",get(f,"Брзини"));
        assertEquals("35000RPM",get(f,"Број на вртежи"));
    }
    @Test public void washingMachineStillGetsFiveCorrectFacts(){
        List<GoogleModelResearch.Fact> f=extract("TELEFUNKEN TWM 1059V",
            "Машина за перење\n9кг\n1000 вртежи\n"+
            "15 програми\nA++ Енергетска класа\nДимензии: 85 x 60 x 55 cm",
            "Машина за перење TELEFUNKEN TWM 1059V");
        assertEquals(5,f.size());
        assertEquals("9кг",get(f,"Капацитет на перење"));
        assertEquals("1000 вртежи",get(f,"Центрифуга"));
        assertEquals("A++",get(f,"Енергетска класа"));
    }
    @Test public void laptopExistingFiveMustStay(){
        List<GoogleModelResearch.Fact> f=extract("ACER Aspire Go 15 (AG15-42P-R844)",
            "Процесор AMD Ryzen 7 5825U\nRAM 16GB DDR4\n"+
            "SSD 512GB\nГрафичка AMD Radeon Graphics\n"+
            "Екран 15.6\" FHD IPS",
            "Лаптоп ACER Aspire Go 15 (AG15-42P-R844)");
        assertEquals(5,f.size());
        assertEquals("16GB DDR4",get(f,"RAM"));
        assertFalse(get(f,"Процесор").isEmpty());
    }
    @Test public void nonSetecLinkAlwaysRejected(){
        assertTrue(SetecKeySpecs.extract("Tefal TT420D30",
            "https://example.com/products/toaster","Тостер\n900W","","").isEmpty());
    }
    @Test public void unknownElectricApplianceReturnsActualWatts(){
        List<GoogleModelResearch.Fact> f=extract("Generic Device QZ120",
            "Generic Device QZ120\nМоќност: 1200W\nБрзини: 3\n"+
            "Тајмер\nДимензии 30x20x40 см",
            "Generic Device QZ120");
        assertEquals("1200W",get(f,"Моќност"));
        assertEquals("3",get(f,"Брзини"));
        assertEquals("Да",get(f,"Тајмер"));
    }
    @Test public void kitchenPriceIsNotMistakenForPower(){
        List<GoogleModelResearch.Fact> f=extract("Tefal TT420D30",
            "Тостер\nКлуб цена: 1.999 ден.\nШифра: 838383\n"+
            "Гаранција 24 месеци", "Tefal TT420D30");
        assertTrue(f.isEmpty());
    }
    @Test public void dishwasherPlaceSettings(){
        List<GoogleModelResearch.Fact> f=extract("Midea Dish X1",
            "Машина за садови\n14 комплети\n6 програми\n"+
            "Енергетска класа D\nБучава 45 dB","Midea Dish X1");
        assertEquals("14",get(f,"Комплети садови"));
        assertEquals("6",get(f,"Програми"));
    }
    @Test public void kettleCapacityAndAutoShutoff(){
        List<GoogleModelResearch.Fact> f=extract("ST KETTLE J1",
            "Електричен бокал\nКапацитет 1.7л\nМоќност 2200W\n"+
            "Автоматско исклучување\nТермостат","ST KETTLE J1");
        assertEquals("1.7л",get(f,"Капацитет"));
        assertEquals("2200W",get(f,"Моќност"));
        assertEquals("Да",get(f,"Автоматско исклучување"));
    }
}
