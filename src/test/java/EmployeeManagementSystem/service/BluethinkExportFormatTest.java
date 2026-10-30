package EmployeeManagementSystem.service;

import java.io.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BluethinkExportFormatTest {
    @Test void workbookHasBrandingWithoutChangingRecordTypesOrValues() throws Exception {
        try(var book=new XSSFWorkbook()){
            var sheet=book.createSheet("Records");var header=sheet.createRow(0);header.createCell(0).setCellValue("Employee ID");header.createCell(1).setCellValue("Hours");
            var row=sheet.createRow(1);row.createCell(0).setCellValue("00123");row.createCell(1).setCellValue(7.5);
            BluethinkReportBranding.workbook(book,sheet,"Attendance Report","2026-10-12 to 2026-10-18 | Scope: self",true);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();book.write(bytes);
            try(var read=new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))){
                var actual=read.getSheet("Records");assertEquals("Attendance Report",actual.getRow(3).getCell(0).getStringCellValue());assertEquals("Employee ID",actual.getRow(8).getCell(0).getStringCellValue());assertEquals("00123",actual.getRow(9).getCell(0).getStringCellValue());assertEquals(CellType.NUMERIC,actual.getRow(9).getCell(1).getCellType());assertEquals(7.5,actual.getRow(9).getCell(1).getNumericCellValue());assertFalse(read.getAllPictures().isEmpty());assertEquals(9,actual.getPaneInformation().getHorizontalSplitPosition());assertTrue(actual.getFooter().getLeft().contains("Confidential"));
            }
        }
    }
    @Test void pdfRepeatsBrandingAndPreservesVeryLongRecordsAcrossPages() throws Exception {
        String task="LONG_RECORD_MARKER "+"detailed project notes ".repeat(1000)+" FINAL_RECORD_MARKER";
        byte[] bytes=BluethinkPdfReport.render("Attendance Report","Scope: self",new String[]{"Employee","Task"},new float[]{.2f,.8f},List.of(List.of("EMP0004",task)));
        try(var doc=Loader.loadPDF(bytes)){
            assertTrue(doc.getNumberOfPages()>1);String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("LONG_RECORD_MARKER"));assertTrue(text.contains("FINAL_RECORD_MARKER"));assertTrue(text.contains("CONFIDENTIAL"));
            for(int i=0;i<doc.getNumberOfPages();i++){var stripper=new PDFTextStripper();stripper.setStartPage(i+1);stripper.setEndPage(i+1);String page=stripper.getText(doc);assertTrue(page.contains("BLUETHINK IT CONSULTING"));assertTrue(page.contains("Page "+(i+1)+" of "+doc.getNumberOfPages()));assertTrue(doc.getPage(i).getResources().getXObjectNames().iterator().hasNext());}
        }
    }
}
