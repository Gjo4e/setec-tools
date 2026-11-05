package mk.seteczaliha.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class GoogleModelLookupFixTest {
    @Test public void ignoresTinyBrandCaptionAboveBoldProductModel(){
        List<ModelNameParser.Candidate> found=ModelNameParser.rank(Arrays.asList(
            "Gorenje","GORENJE WNHVB72SDS","Клуб цена: 14.999 ден."));
        assertFalse(found.isEmpty());
        assertEquals("GORENJE WNHVB72SDS",
            ModelNameParser.normalizeModel("Gorenje GORENJE WNHVB72SDS").toUpperCase());
        assertTrue(found.get(0).model.toUpperCase().contains("WNHVB72SDS"));
        assertFalse(found.get(0).model.toUpperCase().contains("GORENJE GORENJE"));
        assertEquals(1,found.size());
    }

    @Test public void readsExactManufacturerResultFromGoogleHtml(){
        String html="<html><body>"+
            "<div class='MjjYud'><a href='https://mk.gorenje.com/proizvodi/gorenje-wnhvb72sds'>"+
            "<h3>Машина за перење - WNHVB72SDS - GORENJE</h3></a>"+
            "<div class='VwiC3b'>Капацитет за перење: 7 кг, Брзина на центрифуга: 1200 rpm, 15 програми. Енергетска класа D.</div></div>"+
            "<div class='MjjYud'><a href='https://shop.example.com/WNHVB72SDX'>"+
            "<h3>Gorenje WNHVB72SDX</h3></a><div class='VwiC3b'>Капацитет за перење 11 кг</div></div>"+
            "</body></html>";
        List<GoogleModelResearch.Result> results=GoogleSearchFetcher.parseHtml(
            html,"GORENJE WNHVB72SDS");
        assertEquals(1,results.size());
        assertTrue(results.get(0).url.startsWith("https://mk.gorenje.com/"));
        assertNotNull(GoogleModelResearch.description("GORENJE WNHVB72SDS",results));
        List<GoogleModelResearch.Fact> facts=GoogleModelResearch.extract(
            "GORENJE WNHVB72SDS",results);
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Капацитет на перење")&&f.value.equals("7 kg")));
        assertFalse(facts.stream().anyMatch(f->f.value.contains("11")));
    }

    @Test public void rejectGoogleResultsForOtherModels(){
        String html="<a href='https://example.com/gorenje-wmhvb72sds'>"+
            "<h3>GORENJE WMHVB72SDS</h3></a>";
        assertTrue(GoogleSearchFetcher.parseHtml(html,"GORENJE WNHVB72SDS").isEmpty());
    }
}
