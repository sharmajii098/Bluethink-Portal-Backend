package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.EmployeeLetterRequest;
import java.util.*;
import java.math.BigDecimal;
import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HrLetterPdfIntegrityTest {
 @Test void sixSectionSampleRetainsReferencesAndCompensation()throws Exception{
  String body="";for(int i=1;i<=5;i++)body+=(i>1?"\f":"")+"# Sample section "+i+"\nThis is synthetic test content.\n";body+="\f# Compensation\n@@COMPENSATION@@";
  var snapshot=new LetterPdfService.Snapshot("SAMPLE - NOT VALID","OFFER_LETTER","BT-SAMPLE-ONLY","2026-10-19","BlueThink sample employer","Synthetic test address, not a registered company address","nobody@example.invalid","","Sample Signatory","HR Test",body,List.of(new LetterPdfService.Component("Basic",new BigDecimal("600000.00"),"EARNING")));
  byte[] bytes=new LetterPdfService().render(snapshot,true);
  Path output=Path.of("target/release-preview/sample-offer-DRAFT.pdf");Files.createDirectories(output.getParent());Files.write(output,bytes);
  try(var doc=Loader.loadPDF(bytes)){assertTrue(doc.getNumberOfPages()>=6);String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("600000.00"));assertTrue(text.contains("DRAFT - NOT ISSUED"));assertTrue(text.contains("BT-SAMPLE-ONLY"));}
 }
 @Test void storedPdfIsImmutableAndHashChecked(){var x=new EmployeeLetterRequest();x.setStatus("ISSUED");x.setIssuedPdf(new byte[]{1,2,3});x.setIssuedPdfHash(LetterPdfService.digest(x.getIssuedPdf()));var service=new LetterPdfService();byte[] copy=service.generate(x);copy[0]=99;assertEquals(1,x.getIssuedPdf()[0]);x.setIssuedPdf(new byte[]{9,2,3});assertThrows(IllegalStateException.class,()->service.generate(x));}
 @Test void nonIssuedPdfIsRejected(){var x=new EmployeeLetterRequest();assertThrows(IllegalStateException.class,()->new LetterPdfService().generate(x));}
}
