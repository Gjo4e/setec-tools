package mk.seteczaliha.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class RichSpecificationsTest {
    private static final String MODEL="TELEFUNKEN TWM 1059V";
    private static final String EXTERNAL="https://stefan.mk/product/telefunken-twm-1059v/";
    private static final String DESC="TELEFUNKEN TWM 1059V Машина за перење алишта бела боја "
        +"ЛЕД Дисплеј, 9кг, 1000 вртежи, Опција за одбирање на вртежи при перење "
        +"Предперење, 15 програми, Заштита од протекување, А++ Енергетска класа";

    @Test public void telefunkenHasAtLeastSixUsefulVerifiedSpecs(){
        List<GoogleModelResearch.Fact> facts=GoogleModelResearch.extract(MODEL,
            Collections.singletonList(new GoogleModelResearch.Result(
                "TELEFUNKEN TWM 1059V - STEFAN STORE",DESC,EXTERNAL)));
        assertTrue("Found only "+facts.size()+" facts",facts.size()>=6);
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Капацитет на перење")&&f.value.contains("9")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Центрифуга")&&f.value.contains("1000")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Програми")&&f.value.equals("15")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("LED дисплеј")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Заштита од протекување")));
        assertTrue(facts.stream().anyMatch(f->f.label.equals("Енергетска класа")&&f.value.contains("++")));
    }

    @Test public void neverUseSetecAsDescriptionSource(){
        GoogleModelResearch.Result source=new GoogleModelResearch.Result(
            "TELEFUNKEN TWM 1059V - setec.mk",DESC,"https://www.setec.mk/products/telefunken-twm-1059v");
        assertFalse(GoogleModelResearch.relevant(MODEL,source));
        assertTrue(GoogleModelResearch.extract(MODEL,Collections.singletonList(source)).isEmpty());
        assertFalse(GoogleModelResearch.relevant(MODEL,
            new GoogleModelResearch.Result("TikTok - Reddit","Unrelated story","https://reddit.com/r/sample")));
    }

    @Test public void rejectDifferentApplianceVariants(){
        assertFalse(GoogleModelResearch.relevant(MODEL,
            new GoogleModelResearch.Result("TELEFUNKEN TWM 1059B",DESC,
                "https://stefan.mk/product/telefunken-twm-1059b/")));
    }

    @Test public void enrichWholeNonSetecProductPage(){
        GoogleModelResearch.Result result=new GoogleModelResearch.Result(
            "TELEFUNKEN TWM 1059V","15 програми",EXTERNAL);
        String html="<html><head><title>TELEFUNKEN TWM 1059V</title></head><body><main>"
            +"<h1>TELEFUNKEN TWM 1059V</h1>"
            +"<div class='woocommerce-product-details__short-description'>"+DESC+"</div>"
            +"</main></body></html>";
        GoogleModelResearch.Result full=IndependentProductDetails.fromHtml(MODEL,result,html);
        assertNotNull(full);
        assertTrue(GoogleModelResearch.extract(MODEL,
            Collections.singletonList(full)).size()>=6);
    }
}
