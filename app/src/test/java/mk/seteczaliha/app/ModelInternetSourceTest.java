package mk.seteczaliha.app;

import org.junit.Test;
import org.jsoup.Jsoup;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Regression tests for screenshots where Bing returned unrelated TikTok links. */
public final class ModelInternetSourceTest {
    @Test public void rejectsTikTokEvenIfSearchSnippetContainsModel() {
        String model="GORENJE WNHVB72SDS";
        GoogleModelResearch.Result unrelated=new GoogleModelResearch.Result(
            "Can someone pls explain these TikTok live battles - Reddit",
            "Suggested for search GORENJE WNHVB72SDS", "https://www.reddit.com/r/tiktokgossip/comments/example");
        assertFalse(GoogleModelResearch.relevant(model,unrelated));
        assertTrue(GoogleModelResearch.extract(model,Arrays.asList(unrelated)).isEmpty());
        assertNull(GoogleModelResearch.description(model,Arrays.asList(unrelated)));
    }
    @Test public void acceptsVerifiedManufacturerProductPage() {
        String model="GORENJE WNHVB72SDS";
        GoogleModelResearch.Result official=new GoogleModelResearch.Result(
            "Машина за перење - WNHVB72SDS - GORENJE",
            "Капацитет за перење 7 кг, центрифуга 1200 rpm",
            "https://mk.gorenje.com/proizvodi/perenje/WNHVB72SDS");
        assertTrue(GoogleModelResearch.relevant(model,official));
        assertFalse(GoogleModelResearch.relevant(model,new GoogleModelResearch.Result(
            "GORENJE WNHVB72SDX specs", "WNHVB72SDS related product",
            "https://example.com/gorenje-wnhvb72sdx")));
    }
    @Test public void readsManufacturerSpecificationsWithoutSetecDescription() {
        String html="<html><head><title>Машина за перење - WNHVB72SDS - GORENJE</title></head>"+
            "<body><main><h1>G200 Самостојна машина за перење</h1>"+
            "<p>Модел: WNHVB72SDS</p>"+
            "<div>Капацитет за перење 7 кг</div>"+
            "<div>Брзина на центрифугирање 1/1 1200 rpm</div>"+
            "<div>Енергетска класа D</div>"+
            "<div>Број на програми 15</div>"+
            "<div>Функции SteamTech DelayEnd DrumClean</div>"+
            "</main></body></html>";
        GoogleModelResearch.Result found=OfficialModelSources.parsePage(Jsoup.parse(html),
            "GORENJE WNHVB72SDS","https://mk.gorenje.com/official/WNHVB72SDS");
        assertNotNull(found);
        assertTrue(found.snippet.contains("7 кг"));
        assertTrue(found.snippet.contains("1200 rpm"));
        assertTrue(found.snippet.contains("15"));
        assertTrue(found.snippet.contains("SteamTech"));
    }
    @Test public void refusesManufacturerPageOfAnotherProduct() {
        String html="<html><head><title>GORENJE WNHVB72SDX</title></head>"+
            "<body><main><h1>GORENJE WNHVB72SDX</h1><p>7 кг</p></main></body></html>";
        assertNull(OfficialModelSources.parsePage(Jsoup.parse(html),
            "GORENJE WNHVB72SDS","https://mk.gorenje.com/official/WNHVB72SDX"));
    }
}
