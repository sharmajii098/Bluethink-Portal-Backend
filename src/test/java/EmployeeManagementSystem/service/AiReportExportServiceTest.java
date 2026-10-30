package EmployeeManagementSystem.service;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AiReportExportServiceTest {
    @Test void wrappingPreservesAllTextAndRespectsPhysicalWidth() throws Exception {
        var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        String line="W".repeat(250)+" last project and task details";
        var parts=AiReportExportService.wrapLine(line,font,10,532);
        assertEquals(line,String.join("",parts)); assertTrue(parts.size()>1);
        for(String part:parts) assertTrue(font.getStringWidth(part)*10/1000f <= 532);
    }
}
