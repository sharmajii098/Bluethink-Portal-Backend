package EmployeeManagementSystem.service;
import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
@Service
public class AiReportExportService {
    private final AiAssistantService ai;
    private final AiV10AnalyticsService analytics;
    public AiReportExportService(AiAssistantService ai,AiV10AnalyticsService analytics){this.ai=ai;this.analytics=analytics;}
    public byte[] detailedXlsx(java.time.LocalDate from,java.time.LocalDate to,String scope)throws IOException {
        AiV10AnalyticsService.Report data=analytics.report(from,to,scope);
        try(Workbook book=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet summary=book.createSheet("Summary");
            String[] labels={"Scope","From","To","Employees","Attendance entries","Timesheet entries","Logged hours"};
            Object[] values={data.scope(),data.start().toString(),data.end().toString(),data.employees(),data.attendanceEntries(),data.timesheetEntries(),data.loggedHours()};
            for(int i=0;i<labels.length;i++){Row r=summary.createRow(i);r.createCell(0).setCellValue(labels[i]);if(values[i] instanceof Number n)r.createCell(1).setCellValue(n.doubleValue());else r.createCell(1).setCellValue(String.valueOf(values[i]));}
            summary.setColumnWidth(0,24*256);summary.setColumnWidth(1,28*256);
            Sheet rows=book.createSheet("Records");
            String[] keys={"employeeId","employeeName","module","date","status","hours","project","task"};
            Row header=rows.createRow(0);for(int c=0;c<keys.length;c++)header.createCell(c).setCellValue(keys[c]);
            int index=1;
            for(Map<String,String> entry:data.rows()){Row r=rows.createRow(index++);for(int c=0;c<keys.length;c++){String value=entry.getOrDefault(keys[c],"");if(keys[c].equals("hours")&&!value.isBlank())r.createCell(c).setCellValue(Double.parseDouble(value));else r.createCell(c).setCellValue(value);}}
            for(int c=0;c<keys.length;c++)rows.setColumnWidth(c,(c==7?48:22)*256);
            rows.createFreezePane(0,1);rows.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(index-1,0),0,keys.length-1));
            BluethinkReportBranding.workbook(book,summary,"Attendance & timesheet summary",data.start()+" to "+data.end()+" | Scope: "+data.scope(),false);
            BluethinkReportBranding.workbook(book,rows,"Attendance & timesheet records",data.start()+" to "+data.end()+" | Scope: "+data.scope(),true);
            book.write(out);return out.toByteArray();
        }
    }
    public byte[] detailedPdf(java.time.LocalDate from,java.time.LocalDate to,String scope)throws IOException {
        AiV10AnalyticsService.Report data=analytics.report(from,to,scope);
        List<List<String>> records=new ArrayList<>();
        for(Map<String,String> r:data.rows()) records.add(Arrays.asList(r.get("date"),r.get("employeeId"),r.get("employeeName"),r.get("module"),r.get("status"),r.get("hours"),r.get("project"),r.get("task")));
        String context=data.start()+" to "+data.end()+" | Scope: "+data.scope()+" | Employees: "+data.employees()+" | Attendance: "+data.attendanceEntries()+" | Timesheets: "+data.timesheetEntries()+" | Hours: "+data.loggedHours()+" | Attendance statuses: "+data.attendanceStatuses()+" | Timesheet statuses: "+data.timesheetStatuses();
        return BluethinkPdfReport.render("Attendance & Timesheet Report",context,new String[]{"Date","Employee ID","Employee","Module","Status","Hours","Project","Task"},new float[]{.10f,.10f,.13f,.09f,.09f,.07f,.14f,.28f},records);
    }

    public String text(String prompt){return ai.authorizedOverview(prompt);}
    public byte[] xlsx(String prompt)throws IOException {
        String report=text(prompt);
        try(Workbook book=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet sheet=book.createSheet("HRMS Overview");
            CellStyle title=book.createCellStyle(); Font bold=book.createFont();bold.setBold(true);bold.setFontHeightInPoints((short)14);title.setFont(bold);
            int rowNum=0;
            for(String line:report.split("\n")){
                Row row=sheet.createRow(rowNum++);Cell cell=row.createCell(0);cell.setCellValue(line);
                if(rowNum==1)cell.setCellStyle(title);
            }
            sheet.setColumnWidth(0,Math.min(255*256,110*256));
            BluethinkReportBranding.workbook(book,sheet,"HRMS Overview","Authorized HR overview",true);
            book.write(out);return out.toByteArray();
        }
    }
    public byte[] pdf(String prompt)throws IOException {
        String report=text(prompt);
        return renderPdf(Arrays.asList(report.split("\n", -1)));
    }

    private byte[] renderPdf(List<String> lines) throws IOException {
        List<List<String>> rows=new ArrayList<>();for(String line:lines)rows.add(List.of(line));
        return BluethinkPdfReport.render("HRMS Overview","Authorized HR overview",new String[]{"Overview"},new float[]{1f},rows);
    }

    static List<String> wrapLine(String line, PDType1Font font, float size, float maxWidth) throws IOException {
        List<String> parts = new ArrayList<>();
        if (line.isEmpty()) return List.of("");
        int start = 0;
        while (start < line.length()) {
            int end = start;
            float width = 0;
            while (end < line.length()) {
                float next = font.getStringWidth(line.substring(end, end + 1)) * size / 1000f;
                if (end > start && width + next > maxWidth) break;
                width += next; end++;
            }
            parts.add(line.substring(start, end)); start = end;
        }
        return parts;
    }
}
