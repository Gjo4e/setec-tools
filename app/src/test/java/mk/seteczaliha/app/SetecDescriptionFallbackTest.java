package mk.seteczaliha.app;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SetecDescriptionFallbackTest {

    @Test public void televisionWithModelOnlyInShortDescriptionUsesTechnicalBullets(){
        String spec="TCL 98P81K\n"+
            "98\" (248cm) Google SMART Frameless 4K LED TV\n"+
            "ULTRAHD Resolution 3840x2160\n"+
            "144Hz VRR\n"+
            "Dolby Vision IQ\nGoogle TV\nHDMI x3";
        List<String> lines=SetecDescriptionFallback.select(
            "TCL 98P81K",spec,"TCL 98P81K","TCL 98P81K",5);
        assertTrue(lines.size()>1);
        assertFalse(lines.contains("TCL 98P81K"));
        assertTrue(lines.stream().anyMatch(s->s.contains("3840x2160")));
    }

    @Test public void unknownNewCategoryStillShowsOriginalTechnicalText(){
        String spec="Апарат за вафли NEWTECH ZX-33\n"+
            "Моќност 1200W\n"+
            "Регулација на температура\n"+
            "Автоматско исклучување\n"+
            "Плочи со нелеплив слој\n"+
            "Гаранција 24 месеци";
        List<String> lines=SetecDescriptionFallback.select(
            "NEWTECH ZX-33",spec,"NEWTECH ZX-33","NEWTECH ZX-33",5);
        assertTrue(lines.stream().anyMatch(s->s.contains("1200W")));
        assertTrue(lines.stream().anyMatch(s->s.contains("температура")));
        assertTrue(lines.stream().noneMatch(s->s.contains("Гаранција")));
    }

    @Test public void toasterUsesSourceTechnicalBulletsRatherThanCategoryName(){
        String spec="BOSCH TAT4M221\nТостер\n950W\n"+
            "2 парчиња тост\nОдмрзнување\nСад за трошки";
        List<String> lines=SetecDescriptionFallback.select(
            "BOSCH TAT4M221",spec,"BOSCH TAT4M221","BOSCH TAT4M221",5);
        assertTrue(lines.stream().anyMatch(s->s.equals("950W")));
        assertTrue(lines.stream().anyMatch(s->s.contains("Одмрзнување")));
    }

    @Test public void noFalseDescriptionFromOnlyModelAndPrices(){
        String text="Шифра: 79267\nКлуб цена: 17.995 ден.\n"+
            "Редовна цена: 24.999 ден.\nДостава по договор\nCashback поени 200";
        List<String> lines=SetecDescriptionFallback.select(
            "TCL 55V6C",text,"TCL 55V6C","TCL 55V6C",5);
        assertTrue(lines.isEmpty());
    }

    @Test public void technicalLabeledUnknownCategoryCanStillProduceARealFact(){
        List<GoogleModelResearch.Fact> values=SetecDescriptionFallback.labeledFacts(
            "Generic Gadget X13",
            "https://setec.mk/products/generic-gadget-x13",
            "Температура: 180 °C\nМоќност: 1600W\nГаранција 24 месеци",
            "Generic Gadget X13","Generic Gadget X13");
        assertTrue(values.stream().anyMatch(f->f.label.equals("Моќност")
            &&f.value.equals("1600W")));
        assertTrue(values.stream().noneMatch(f->f.label.contains("Гаранција")));
    }

    @Test public void mustNeverTrustTechnicalTextFromOtherDomains(){
        assertTrue(SetecDescriptionFallback.labeledFacts("X",
            "https://setec.mk.attacker.test/products/x",
            "Моќност: 2000W","","X").isEmpty());
    }

    @Test public void preserveHoodNoiseAndAirFlowFromExactSource(){
        List<String> lines=SetecDescriptionFallback.select("AKPO WK-7 MICRA",
            "АКПО WK-7 MICRA\nЦелосно вграден аспиратор\n"+
            "Капацитет на одвод 300cb3\n"+
            "3 брзини\nБучност 60dB\nДимензии 53x15.5x29 цм",
            "AKPO WK-7 MICRA","AKPO WK-7 MICRA",5);
        assertTrue(lines.stream().anyMatch(s->s.contains("300cb3")));
        assertTrue(lines.stream().anyMatch(s->s.contains("60dB")));
    }

    @Test public void newPageReaderInspectsHiddenTechnicalPanels(){
        String js=SetecPageData.READ_PRODUCT_SCRIPT;
        assertTrue(js.contains("node.querySelectorAll('li')"));
        assertTrue(js.contains("aria-controls"));
        assertTrue(js.contains("window.__setecSpecsOpened"));
        assertTrue(js.contains("textContent"));
        assertTrue(js.contains("short, category"));
    }
}
