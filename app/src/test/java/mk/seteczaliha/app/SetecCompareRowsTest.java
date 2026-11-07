package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** End-to-end comparison selection for source-backed Setec bullet descriptions. */
public final class SetecCompareRowsTest {
    @Test public void originalBulletsCompareEvenWhenNeitherProductHasNamedSpecs(){
        List<String> one=Arrays.asList(
            "55\" QLED Google TV","Resolution 3840x2160","144Hz (VRR)",
            "HDMI x3","HDR10+");
        List<String> two=Arrays.asList(
            "50\" LED Google TV","Resolution 1920x1080","60Hz",
            "HDMI x2","HDR10");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build("TCL X",
            Collections.emptyMap(),one,"SAMSUNG Y",Collections.emptyMap(),two);
        assertTrue("Unlabelled bullet positions must never be compared",rows.isEmpty());
    }

    @Test public void labeledFactsAlignedBeforeOriginalSetecDetails(){
        Map<String,String> left=new LinkedHashMap<>();
        left.put("Моќност","900W");
        Map<String,String> right=new LinkedHashMap<>();
        right.put("Моќност","1200W");
        List<String> l=Arrays.asList("900W","7 степени на печење","Одмрзнување");
        List<String> r=Arrays.asList("1200W","6 степени на печење","Тацна за трошки");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build("Тостер Tefal TT",
            left,l,"Тостер Bosch TT",right,r);
        assertEquals(1,rows.size());
        assertEquals("Моќност",rows.get(0).label);
        assertEquals("900W",rows.get(0).left);
        assertEquals("1200W",rows.get(0).right);
        assertTrue(rows.get(0).comparable);
    }

    @Test public void rawDetailsSurviveSerializationAndHeadersAreDiscarded(){
        String displayed="НАЈВАЖНИ ТЕХНИЧКИ ПОДАТОЦИ ОД SETEC.MK\n"+
            "• Моќност: 900W\n"+
            "ОРИГИНАЛЕН ТЕХНИЧКИ ОПИС ОД SETEC.MK\n"+
            "• 7 степени на печење\n• Одмрзнување";
        List<String> lines=SetecCompareRows.parseLines(displayed);
        assertEquals(3,lines.size());
        assertEquals("Моќност: 900W",lines.get(0));
        String description=SetecCompareRows.asDescription(lines);
        assertEquals(lines,SetecCompareRows.parseLines(description));
    }

    @Test public void neverUsePriceOrStoreUiHeadingsAsComparisonSpecs(){
        String description="Клуб цена: 19.999 ден.\n"+
            "НАЈВАЖНИ ТЕХНИЧКИ ПОДАТОЦИ ОД SETEC.MK\n"+
            "• 16 GB RAM\n• 512 GB SSD\n"+
            "Редовна цена: 22.000 ден.";
        List<String> lines=SetecCompareRows.parseLines(description);
        assertEquals(Arrays.asList("16 GB RAM","512 GB SSD"),lines);
    }

    @Test public void doesNotPretendDifferentBulletPositionsAreComparableFields(){
        List<SetecCompareRows.Row> rows=SetecCompareRows.build(
            "TCL 55",Collections.emptyMap(),Collections.singletonList("144Hz VRR"),
            "MIDEA AC",Collections.emptyMap(),Collections.singletonList("18000 BTU"));
        assertTrue(rows.isEmpty());
    }

    @Test public void valuesComeOnlyFromProvidedFactsAndDetails(){
        List<SetecCompareRows.Row> empty=SetecCompareRows.build("A",
            Collections.emptyMap(),Collections.emptyList(),
            "B",Collections.emptyMap(),Collections.emptyList());
        assertTrue(empty.isEmpty());
        Map<String,String> left=new LinkedHashMap<>();
        left.put("RAM","16 GB");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build("Laptop A",
            left,Collections.emptyList(),"Laptop B",
            Collections.emptyMap(),Collections.emptyList());
        assertEquals(1,rows.size());
        assertEquals("RAM",rows.get(0).label);
        assertEquals("16 GB",rows.get(0).left);
        assertEquals("Непотврдено",rows.get(0).right);
        assertFalse("Missing values must never be compared",rows.get(0).comparable);
    }

    @Test public void preservesFiveMostImportantNamedSpecsForOneSavedTelevision(){
        Map<String,String> tv=new LinkedHashMap<>();
        tv.put("Дијагонала","55\"");
        tv.put("Резолуција","3840×2160");
        tv.put("Освежување","144Hz");
        tv.put("Технологија на екран","QLED");
        tv.put("Smart TV","Да");
        List<String> h=SetecCompareRows.highlights("TCL TV",tv,
            Collections.emptyList(),"");
        assertEquals(5,h.size());
        assertEquals("Дијагонала: 55\"",h.get(0));
        assertEquals("Освежување: 144Hz",h.get(2));
    }

    @Test public void fiveOriginalSetecBulletsRemainWhenNoNamedFactsRecognized(){
        List<String> list=Arrays.asList("55\" QLED Google TV",
            "Resolution 3840x2160","144Hz VRR","HDMI x3","HDR10+");
        List<String> h=SetecCompareRows.highlights("TCL X",
            Collections.emptyMap(),list,"");
        assertEquals(5,h.size());
        assertEquals("55\" QLED Google TV",h.get(0));
        assertEquals("HDR10+",h.get(4));
    }

    @Test public void exactScannerVisibleTextIsStillSavedWithoutIntermediateFacts(){
        String summary="НАЈВАЖНИ ТЕХНИЧКИ ПОДАТОЦИ ОД SETEC.MK\n"+
            "• Моќност: 900W\n"+
            "ОРИГИНАЛЕН ТЕХНИЧКИ ОПИС ОД SETEC.MK\n"+
            "• 7 степени на печење\n• Одмрзнување";
        List<String> h=SetecCompareRows.highlights("Tefal toaster",
            Collections.emptyMap(),Collections.emptyList(),summary);
        assertEquals(3,h.size());
        assertEquals("Моќност: 900W",h.get(0));
        assertEquals("Одмрзнување",h.get(2));
    }

    @Test public void prioritizeNamedFactsButFillRemainingFromTechnicalBullets(){
        Map<String,String> facts=new LinkedHashMap<>();
        facts.put("Моќност","1800W");
        List<String> original=Arrays.asList("1800W","Нелепливи плочи",
            "Термостат","Автоматско исклучување","Плочи што се вадат");
        List<String> h=SetecCompareRows.highlights("ST GT1800SL",
            facts,original,"");
        assertEquals(5,h.size());
        assertEquals("Моќност: 1800W",h.get(0));
        assertTrue(h.contains("Нелепливи плочи"));
        assertFalse(h.contains("1800W"));
    }

    @Test public void noPriceOrUnverifiedDescriptionsAreAddedAsSpecs(){
        List<String> h=SetecCompareRows.highlights("TCL 55",
            Collections.emptyMap(),Collections.emptyList(),
            "Клуб цена: 19.999 ден.\n• 55\" LED TV\n"+
            "Редовна цена: 29.999 ден.");
        assertEquals(Collections.singletonList("55\" LED TV"),h);
    }
    @Test public void washerAndFridgeNeverCompareTheirCapacity(){
        Map<String,String> washer=new LinkedHashMap<>();
        washer.put("Капацитет","9 kg");
        washer.put("Енергетска класа","A");
        Map<String,String> fridge=new LinkedHashMap<>();
        fridge.put("Капацитет","330 L");
        fridge.put("Енергетска класа","E");
        assertTrue(SetecCompareRows.build("Машина за перење Gorenje",
            washer,Collections.emptyList(),"Фрижидер Gorenje",
            fridge,Collections.emptyList()).isEmpty());
    }

    @Test public void sameCategoryRejectsDifferentUnitsOnSameLabel(){
        Map<String,String> left=new LinkedHashMap<>();
        left.put("Капацитет","8 kg");
        left.put("Центрифуга","1200 rpm");
        Map<String,String> right=new LinkedHashMap<>();
        right.put("Капацитет","300 L");
        right.put("Центрифуга","1400 rpm");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build(
            "Машина за перење A",left,Collections.emptyList(),
            "Машина за перење B",right,Collections.emptyList());
        assertEquals(1,rows.size());
        assertEquals("Центрифуга",rows.get(0).label);
        assertEquals("1200 rpm",rows.get(0).left);
        assertEquals("1400 rpm",rows.get(0).right);
    }

    @Test public void sameCategoryComparesMatchingCapacityUnits(){
        Map<String,String> left=new LinkedHashMap<>();
        left.put("Капацитет на перење","7 kg");
        Map<String,String> right=new LinkedHashMap<>();
        right.put("Капацитет на перење","9 kg");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build(
            "Машина за перење A",left,Collections.emptyList(),
            "Машина за перење B",right,Collections.emptyList());
        assertEquals(1,rows.size());
        assertTrue(rows.get(0).comparable);
    }

    @Test public void categoryDetectorDistinguishesWasherDryerAndTv(){
        assertFalse(CompareCategoryGuard.canCompare("Машина за перење",
            "Машина за сушење"));
        assertFalse(CompareCategoryGuard.canCompare("ТЕЛЕВИЗОР TCL 55",
            "МОНИТОР Samsung 27"));
        assertTrue(CompareCategoryGuard.canCompare("Машина за перење Bosch",
            "Машина за перење Gorenje"));
    }

    @Test public void washerCategoryShowsOnlyAppropriateCriteriaInOrder(){
        Map<String,String> left=new LinkedHashMap<>();
        left.put("Моќност","1800W");
        left.put("Програми","16");
        left.put("Капацитет на перење","9 kg");
        left.put("Центрифуга","1400 rpm");
        Map<String,String> right=new LinkedHashMap<>();
        right.put("Моќност","2200W");
        right.put("Капацитет на перење","7 kg");
        right.put("Центрифуга","1200 rpm");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build(
            "Машина за перење LG",left,Collections.emptyList(),
            "Машина за перење Bosch",right,Collections.emptyList());
        assertEquals(3,rows.size());
        assertEquals("Капацитет на перење",rows.get(0).label);
        assertEquals("Центрифуга",rows.get(1).label);
        assertEquals("Програми",rows.get(2).label);
        assertEquals("Непотврдено",rows.get(2).right);
        assertFalse(rows.get(2).comparable);
        assertTrue(rows.get(0).comparable);
        assertFalse(rows.stream().anyMatch(x->x.label.equals("Моќност")));
    }

    @Test public void tvComparisonIgnoresWashingFacts(){
        Map<String,String> a=new LinkedHashMap<>();
        a.put("Дијагонала","55 in");
        a.put("Освежување","120 Hz");
        a.put("Капацитет на перење","9 kg");
        Map<String,String> b=new LinkedHashMap<>();
        b.put("Дијагонала","65 in");
        b.put("Освежување","60 Hz");
        b.put("Капацитет на перење","8 kg");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build(
            "Телевизор TCL",a,Collections.emptyList(),
            "Телевизор Samsung",b,Collections.emptyList());
        assertEquals(2,rows.size());
        assertEquals("Дијагонала",rows.get(0).label);
        assertEquals("Освежување",rows.get(1).label);
    }

    @Test public void unknownAndWasherCategoryCannotBeAssumedCompatible(){
        Map<String,String> known=Collections.singletonMap("Капацитет на перење","8 kg");
        Map<String,String> unknown=Collections.singletonMap("Моќност","800W");
        assertTrue(SetecCompareRows.build("Машина за перење Gorenje",known,
            Collections.emptyList(),"Unclassified X",unknown,
            Collections.emptyList()).isEmpty());
        assertFalse(CompareCategoryGuard.incompatibility("Машина за перење Gorenje",
            known,"Unclassified X",unknown).isEmpty());
    }

    @Test public void titleWithoutCategoryMayUseDistinctiveVerifiedSpecs(){
        Map<String,String> a=new LinkedHashMap<>();
        a.put("Капацитет на перење","8 kg");
        a.put("Центрифуга","1200 rpm");
        Map<String,String> b=new LinkedHashMap<>();
        b.put("Капацитет на перење","9 kg");
        b.put("Центрифуга","1400 rpm");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build("MODEL ABC123",a,
            Collections.emptyList(),"MODEL XYZ789",b,Collections.emptyList());
        assertEquals(2,rows.size());
        assertEquals("Капацитет на перење",rows.get(0).label);
    }

    @Test public void everySupportedFamilyHasFiveNamedCriteria(){
        for(CompareCategoryGuard.Kind kind:CompareCategoryGuard.Kind.values()){
            if(kind==CompareCategoryGuard.Kind.UNKNOWN)continue;
            assertEquals(kind.label,5,CompareCategoryProfiles.criteria(kind).size());
        }
    }

    @Test public void twoUnknownProductsAreNotAssumedSameType(){
        Map<String,String> a=Collections.singletonMap("Моќност","1200W");
        Map<String,String> b=Collections.singletonMap("Моќност","800W");
        assertTrue(SetecCompareRows.build("A123",a,Collections.emptyList(),
            "B456",b,Collections.emptyList()).isEmpty());
        assertFalse(CompareCategoryGuard.incompatibility("A123",a,"B456",b).isEmpty());
    }

}
