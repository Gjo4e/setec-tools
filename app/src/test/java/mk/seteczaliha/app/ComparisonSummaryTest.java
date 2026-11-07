package mk.seteczaliha.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ComparisonSummaryTest {
    @Test public void washingMachinesShowOnlyFiveUsefulSpecs(){
        Map<String,String> one=new LinkedHashMap<>();
        one.put("Капацитет на перење","9 kg");
        one.put("Центрифуга","1000 rpm");
        one.put("Програми","15");
        one.put("Енергетска класа","A++");
        one.put("Димензии","85×60×55 cm");
        one.put("Бучава","75 dB");
        one.put("LED дисплеј","Да");
        Map<String,String> two=new LinkedHashMap<>(one);
        two.put("Капацитет на перење","7 kg");
        List<String> specs=ComparisonSummary.topSpecs("TELEFUNKEN TWM 1059V",one,"MIDEA MF110W70",two);
        assertEquals(5,specs.size());
        assertEquals(Arrays.asList("Капацитет на перење","Центрифуга",
            "Енергетска класа","Програми","Димензии"),specs);
    }
    @Test public void noInventedValuesWhenSpecsUnavailable(){
        assertTrue(ComparisonSummary.topSpecs("ASUS X",Collections.emptyMap(),
            "MSI Y",Collections.emptyMap()).isEmpty());
    }
    @Test public void showsOnlyTheUnionOfActuallySourcedFields(){
        Map<String,String> one=Collections.singletonMap("Капацитет на перење","7 kg");
        Map<String,String> two=Collections.singletonMap("Центрифуга","1200 rpm");
        List<String> specs=ComparisonSummary.topSpecs("washer 1",one,"washer 2",two);
        assertEquals(2,specs.size());
        assertTrue(specs.contains("Капацитет на перење"));
        assertTrue(specs.contains("Центрифуга"));
    }
    @Test public void electronicComparisonPrefersRamAndScreen(){
        Map<String,String> left=new LinkedHashMap<>();
        left.put("Капацитет","512 GB");
        left.put("Резолуција","1920x1080");
        left.put("RAM","16 GB");
        left.put("Бучава","40 dB");
        assertEquals(Arrays.asList("RAM"),
            ComparisonSummary.topSpecs("ASUS Vivobook",left,"Lenovo Laptop",left));
    }
    @Test public void washerStillPrioritizesWashLoadWhenPowerIsAlsoListed(){
        Map<String,String> a=new LinkedHashMap<>();
        a.put("Моќност","1800W");
        a.put("Центрифуга","1200 rpm");
        a.put("Капацитет на перење","9 kg");
        Map<String,String> b=new LinkedHashMap<>(a);
        b.put("Капацитет на перење","7 kg");
        List<String> fields=ComparisonSummary.topSpecs(
            "Машина за перење Bosch",a,"Машина за перење Gorenje",b);
        assertEquals("Капацитет на перење",fields.get(0));
        assertEquals("Центрифуга",fields.get(1));
    }

    @Test public void dishwasherUsesOnlyDishwasherCriteria(){
        Map<String,String> facts=new LinkedHashMap<>();
        facts.put("Бучава","45 dB");
        facts.put("Програми","7");
        facts.put("RAM","16 GB");
        facts.put("Енергетска класа","B");
        assertEquals(Arrays.asList("Програми","Енергетска класа","Бучава"),
            ComparisonSummary.topSpecs("Машина за садови Bosch",facts,
                "Машина за садови Beko",facts));
    }

    @Test public void tvNeverRanksWashingCapacity(){
        Map<String,String> facts=new LinkedHashMap<>();
        facts.put("Капацитет на перење","8 kg");
        facts.put("Освежување","144 Hz");
        facts.put("Резолуција","3840x2160");
        facts.put("Дијагонала","55 in");
        assertEquals(Arrays.asList("Дијагонала","Резолуција","Освежување"),
            ComparisonSummary.topSpecs("Телевизор TCL",facts,
                "Телевизор Samsung",facts));
    }

    @Test public void washerDryerComboHasBothLoads(){
        Map<String,String> facts=new LinkedHashMap<>();
        facts.put("Капацитет на сушење","6 kg");
        facts.put("Капацитет на перење","10 kg");
        facts.put("Центрифуга","1400 rpm");
        assertEquals(Arrays.asList("Капацитет на перење",
            "Капацитет на сушење","Центрифуга"),
            ComparisonSummary.topSpecs("WASHER DRYER LG",facts,
                "WASHER DRYER GORENJE",facts));
    }

}
