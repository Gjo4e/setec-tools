package mk.seteczaliha.app;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Text samples reflect TV technical lists on Setec product pages. */
public final class SetecTvSpecsTest {
    private static String PAGE="https://setec.mk/products/samsung-qe55ls03fauxxh";
    private static List<GoogleModelResearch.Fact> read(String model,String spec){
        return SetecKeySpecs.extract(model,PAGE,spec,model,model,"ТВ/Аудио/Видео / Телевизори");
    }
    private static String value(List<GoogleModelResearch.Fact> facts,String label){
        for(GoogleModelResearch.Fact f:facts)if(label.equals(f.label))return f.value;
        return "";
    }

    @Test public void samsungOnlyModelInTitleStillShowsImportantTvSpecs(){
        List<GoogleModelResearch.Fact> tv=read("SAMSUNG QE55LS03FAUXXH",
            "SAMSUNG QE55LS03FAUXXH\n"+
            "55\" (138cm) UltraHD QLED Smart Frame TV\n"+
            "Resolution: 3.840 x 2.160\n"+
            "HDR 10+\nConnections: 4 x HDMI");
        assertEquals("55\"",value(tv,"Дијагонала"));
        assertEquals("3840×2160",value(tv,"Резолуција"));
        assertEquals("QLED",value(tv,"Технологија на екран"));
        assertEquals("Да",value(tv,"Smart TV"));
        assertEquals(5,tv.size());
        assertTrue(tv.stream().allMatch(f->f.url.equals(PAGE)));
    }
    @Test public void tclAndroidGoogleTvFreeformSpecLines(){
        List<GoogleModelResearch.Fact> tv=read("TCL 55V6C",
            "TCL 55V6C\n55\" (127cm) Android SMART Frameless 4K LED TV\n"+
            "Google TV\nULTRAHD Resolution 3840x2160\n4K HDR10\nHDMI x3");
        assertEquals("55\"",value(tv,"Дијагонала"));
        assertEquals("3840×2160",value(tv,"Резолуција"));
        assertEquals("LED",value(tv,"Технологија на екран"));
        assertEquals("Да",value(tv,"Smart TV"));
        assertEquals("Google TV",value(tv,"Оперативен систем"));
    }
    @Test public void hisenseVidAAAndOneHundredFortyFourHertz(){
        List<GoogleModelResearch.Fact> tv=read("Hisense 75E7Q PRO",
            "Hisense 75E7Q PRO\n75\" (190cm) SMART 4K UltraHD QLED TV\n"+
            "Resolution 4K 3840x2160\n144Hz (VRR)\nVIDAA U6");
        assertEquals("75\"",value(tv,"Дијагонала"));
        assertEquals("3840×2160",value(tv,"Резолуција"));
        assertEquals("144Hz",value(tv,"Освежување"));
        assertEquals("QLED",value(tv,"Технологија на екран"));
    }
    @Test public void sonyOledTvAnd120Hz(){
        List<GoogleModelResearch.Fact> tv=read("SONY K83A65PB.CEI",
            "83\" (210cm) 4K UltraHD OLED Google TV\n"+
            "Resolution 3840x2160\nRefresh rate: 120Hz\nGoogle TV");
        assertEquals("83\"",value(tv,"Дијагонала"));
        assertEquals("3840×2160",value(tv,"Резолуција"));
        assertEquals("120Hz",value(tv,"Освежување"));
        assertEquals("OLED",value(tv,"Технологија на екран"));
    }
    @Test public void philipsTitanOsAndDottedFourK(){
        List<GoogleModelResearch.Fact> tv=read("PHILIPS 50PUS7810",
            "PHILIPS 50PUS7810\n50\" (126cm) Ultra Slim UltraHD Smart QLED TV\n"+
            "Resolution: 3.840 x 2.160 (4K Ultra HD)\n"+
            "OS: TITAN\nHDR10+\nWi-Fi 11n");
        assertEquals("50\"",value(tv,"Дијагонала"));
        assertEquals("3840×2160",value(tv,"Резолуција"));
        assertEquals("QLED",value(tv,"Технологија на екран"));
        assertEquals("TITAN",value(tv,"Оперативен систем"));
    }
    @Test public void basicStTvWithHdReadyIsNotInventedAsSmart(){
        List<GoogleModelResearch.Fact> tv=read("ST ST-32DH4300",
            "32\" LED TV\nResolution: 1366x768");
        assertEquals("32\"",value(tv,"Дијагонала"));
        assertEquals("1366×768",value(tv,"Резолуција"));
        assertEquals("LED",value(tv,"Технологија на екран"));
        assertEquals("",value(tv,"Smart TV"));
    }
    @Test public void monsterGamingAcceleratorIsNotNativeRefresh(){
        List<GoogleModelResearch.Fact> tv=read("TCL 85C69B",
            "85\" Android SMART Frameless 4K QLED TV\n"+
            "ULTRAHD Resolution 3840x2160\n"+
            "120Hz Game Accelerator\nGame Mode\nGoogle Assistant");
        assertEquals("",value(tv,"Освежување"));
    }
    @Test public void noVerifiedSpecsAreNotMadeUpFromTvModelNumber(){
        List<GoogleModelResearch.Fact> tv=read("SAMSUNG QE55Q7FAAUXXH","");
        assertTrue(tv.isEmpty());
    }
    @Test public void onlyOfficialSetecPagesCanProduceTvFacts(){
        assertTrue(SetecKeySpecs.extract("TCL 55V6C",
            "https://example.com/products/tcl-55v6c","55\" 4K QLED Smart TV",
            "TCL 55V6C","TCL 55V6C","Телевизори").isEmpty());
    }
    @Test public void samsungModelWithoutTvDescriptionUsesBreadcrumbCategory(){
        List<GoogleModelResearch.Fact> tv=SetecKeySpecs.extract(
            "SAMSUNG QE55LS03FAUXXH",PAGE,
            "55\" (138cm)\nResolution: 3840x2160",
            "SAMSUNG QE55LS03FAUXXH",
            "SAMSUNG QE55LS03FAUXXH","Телевизори");
        assertEquals("55\"",value(tv,"Дијагонала"));
        assertEquals("3840×2160",value(tv,"Резолуција"));
    }
}
