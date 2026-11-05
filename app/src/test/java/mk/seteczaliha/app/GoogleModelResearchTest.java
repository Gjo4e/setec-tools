package mk.seteczaliha.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class GoogleModelResearchTest {
    @Test public void repeatsBrandOnlyOnceOnPrintedLabel() {
        assertEquals("Gorenje WNHVB72SDS",
            ModelNameParser.normalizeModel("Gorenje GORENJE WNHVB72SDS"));
        assertEquals("GORENJE WNHVB72SDS",
            ModelNameParser.normalizeModel("GORENJE WNHVB72SDS"));
        List<ModelNameParser.Candidate> list=ModelNameParser.rank(
            Arrays.asList("Gorenje", "GORENJE WNHVB72SDS", "Клуб цена: 14.999 ден."));
        assertFalse(list.isEmpty());
        assertTrue(list.get(0).model.toUpperCase().contains("WNHVB72SDS"));
        assertFalse(list.get(0).model.toUpperCase().contains("GORENJE GORENJE"));
    }

    @Test public void searchGoogleForExactModelRatherThanShopName() {
        assertEquals("GORENJE WNHVB72SDS technical specifications",
            GoogleModelResearch.searchQuery("Gorenje GORENJE WNHVB72SDS"));
    }

    @Test public void extractOnlyLiteralFactsFromExactModelSources() {
        String model="GORENJE WNHVB72SDS";
        GoogleModelResearch.Result exact=new GoogleModelResearch.Result(
            "Gorenje WNHVB72SDS - full technical specifications",
            "Washing capacity 7 kg. Spin speed 1200 rpm. 15 programmes. Energy rating A.",
            "https://www.example.com/gorenje-wnhvb72sds");
        GoogleModelResearch.Result other=new GoogleModelResearch.Result(
            "Gorenje WNHVB72SDX - product specifications",
            "Washing capacity 11 kg. Spin speed 1800 rpm.",
            "https://www.example.com/other-model");
        assertTrue(GoogleModelResearch.relevant(model,exact));
        assertFalse(GoogleModelResearch.relevant(model,other));
        List<GoogleModelResearch.Fact> facts=GoogleModelResearch.extract(model,Arrays.asList(other,exact));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Капацитет на перење") && f.value.equals("7 kg")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Центрифуга") && f.value.equals("1200 rpm")));
        assertFalse(facts.stream().anyMatch(f->f.value.contains("1800") || f.value.contains("11")));
        assertTrue(facts.stream().allMatch(f->f.url.equals("https://www.example.com/gorenje-wnhvb72sds")));
    }

    @Test public void refusesSpeculationWhenOnlyPriceIsFound() {
        String model="GORENJE WNHVB72SDS";
        GoogleModelResearch.Result onlyPrice=new GoogleModelResearch.Result(
            "GORENJE WNHVB72SDS",
            "Клуб цена 14.999 ден. Купете го овој производ.",
            "https://www.example.com/product");
        assertTrue(GoogleModelResearch.extract(model,Arrays.asList(onlyPrice)).isEmpty());
    }
}
