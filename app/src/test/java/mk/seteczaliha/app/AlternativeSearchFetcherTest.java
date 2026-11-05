package mk.seteczaliha.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class AlternativeSearchFetcherTest {
    @Test public void bingOnlyKeepsExactModel() {
        String html="<html><body><ul>"+
          "<li class='b_algo'><h2><a href='https://www.reddit.com/r/tiktokgossip/example'>TikTok live battles</a></h2>"+
          "<div class='b_caption'><p>Random results for WNHVB72SDS</p></div></li>"+
          "<li class='b_algo'><h2><a href='https://mk.gorenje.com/products/WNHVB72SDS'>Машина за перење WNHVB72SDS - GORENJE</a></h2>"+
          "<div class='b_caption'><p>Капацитет за перење 7 кг, брзина на центрифуга 1200 rpm.</p></div></li>"+
          "</ul></body></html>";
        List<GoogleModelResearch.Result> found=AlternativeSearchFetcher.parse(html,"GORENJE WNHVB72SDS","bing");
        assertEquals(1,found.size());
        assertTrue(found.get(0).url.contains("gorenje.com"));
    }
    @Test public void duckDuckGoRedirectIsDecodedAndFiltered() {
        String html="<html><body><div class='result'>"+
          "<a class='result__a' href='https://duckduckgo.com/l/?uddg=https%3A%2F%2Fmk.gorenje.com%2Fproducts%2FWNHVB72SDS'>WNHVB72SDS GORENJE</a>"+
          "<a class='result__snippet'>Washing capacity 7 kg</a></div></body></html>";
        List<GoogleModelResearch.Result> found=AlternativeSearchFetcher.parse(html,"GORENJE WNHVB72SDS","ddg");
        assertEquals(1,found.size());
        assertTrue(found.get(0).url.startsWith("https://mk.gorenje.com/"));
    }
}
