package mk.seteczaliha.app;

import org.junit.Test;
import org.json.JSONException;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class OfferExporterTest {
    private static final String OFFER="{\"installment\":\"48-zero\",\"items\":["+
        "{\"name\":\"Матична <Test> & компонента\",\"sku\":\"=HYPERLINK(\\\"test\\\")\",\"qty\":2,\"club\":1500,\"regular\":1700},"+
        "{\"name\":\"RAM 16GB\",\"sku\":\"12345\",\"qty\":1,\"club\":4000,\"regular\":4500}]}";

    @Test public void interestFree48MonthsOnlyThreePercent() throws Exception {
        OfferExporter.Quote quote=OfferExporter.parse(OFFER);
        assertEquals(48,quote.months);
        assertEquals(0,quote.interestRate,0.00001);
        assertEquals(7000,quote.clubTotal,0.00001);
        assertEquals(210,quote.admin(),0.00001);
        assertEquals(0,quote.interest(),0.00001);
        assertEquals(7210.0/48.0,quote.monthly(),0.00001);
        assertTrue(quote.canCalculateInstallments());
    }

    @Test public void missingPricesDoNotProduceMisleadingPayment() throws Exception {
        OfferExporter.Quote quote=OfferExporter.parse(
            "{\"installment\":\"12\",\"items\":[{\"name\":\"Без цена\",\"qty\":1,\"club\":null,\"regular\":null}]}");
        assertEquals(1,quote.clubMissing);
        assertEquals(1,quote.regularMissing);
        assertFalse(quote.canCalculateInstallments());
    }

    @Test public void genuineXlsxHasSafeXmlAndFormulas() throws Exception {
        OfferExporter.Quote quote=OfferExporter.parse(OFFER);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        OfferExporter.excel(output,quote);
        assertTrue(output.size()>1500);
        Map<String,String> parts=new HashMap<>();
        try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()),StandardCharsets.UTF_8)){
            ZipEntry entry;
            byte[] b=new byte[8192];
            while((entry=zin.getNextEntry())!=null){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                int n;
                while((n=zin.read(b))>0)bytes.write(b,0,n);
                parts.put(entry.getName(),new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
        }
        assertTrue(parts.containsKey("[Content_Types].xml"));
        assertTrue(parts.containsKey("xl/workbook.xml"));
        assertTrue(parts.containsKey("xl/styles.xml"));
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"));
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(parts.get("xl/worksheets/sheet1.xml"))));
        String sheet=parts.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("Матична &lt;Test&gt; &amp; компонента"));
        assertTrue(sheet.contains("=HYPERLINK")); // Treated as inline string, not a formula.
        assertTrue(sheet.contains("<f>D4*E4</f>"));
        assertTrue(sheet.contains("<f>SUM(G4:G5)</f>"));
        assertTrue(sheet.contains("48"));
        assertTrue(sheet.contains("Камата (0%)"));
        assertFalse(sheet.contains("0.1199")); // Never charge interest on 48-zero.
    }

    @Test(expected=JSONException.class)
    public void rejectUnboundedQuantity() throws Exception {
        OfferExporter.parse("{\"installment\":\"12\",\"items\":[{\"name\":\"Test\",\"qty\":999999,\"club\":1}]}");
    }
}
