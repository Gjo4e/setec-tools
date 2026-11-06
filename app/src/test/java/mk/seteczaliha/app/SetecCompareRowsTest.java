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
        assertEquals(5,rows.size());
        assertTrue(rows.get(0).left.contains("55"));
        assertTrue(rows.get(0).right.contains("50"));
        assertFalse(rows.get(0).comparable);
    }

    @Test public void labeledFactsAlignedBeforeOriginalSetecDetails(){
        Map<String,String> left=new LinkedHashMap<>();
        left.put("Моќност","900W");
        Map<String,String> right=new LinkedHashMap<>();
        right.put("Моќност","1200W");
        List<String> l=Arrays.asList("900W","7 степени на печење","Одмрзнување");
        List<String> r=Arrays.asList("1200W","6 степени на печење","Тацна за трошки");
        List<SetecCompareRows.Row> rows=SetecCompareRows.build("Tefal TT",
            left,l,"Bosch TT",right,r);
        assertEquals(3,rows.size());
        assertEquals("Моќност",rows.get(0).label);
        assertEquals("900W",rows.get(0).left);
        assertEquals("1200W",rows.get(0).right);
        assertTrue(rows.get(0).comparable);
        assertFalse(rows.get(1).comparable);
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
        assertEquals(1,rows.size());
        assertFalse(rows.get(0).comparable);
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
        assertEquals("Непотврдено",rows.get(0).right);
    }
}
