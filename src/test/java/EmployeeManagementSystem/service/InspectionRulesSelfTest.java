package EmployeeManagementSystem.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.*;

/** Run without Maven dependencies; exercises production helpers rather than copies. */
public final class InspectionRulesSelfTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(!PasswordPolicy.valid(null),"missing password");
        check(!PasswordPolicy.valid("short123"),"minimum length");
        check(!PasswordPolicy.valid("abcdefghijkl"),"number required");
        check(!PasswordPolicy.valid("123456789012"),"letter required");
        check(PasswordPolicy.valid("abcdefghijkl1"),"valid password");
        check(PasswordPolicy.valid("a".repeat(71)+"1"),"72 ASCII bytes accepted");
        check(!PasswordPolicy.valid("a".repeat(72)+"1"),"73 ASCII bytes rejected before BCrypt");
        check(PasswordPolicy.valid("é".repeat(35)+"a1"),"72 UTF-8 bytes accepted");
        check(!PasswordPolicy.valid("é".repeat(36)+"a1"),"UTF-8 byte limit, not character count");
        check(AttachmentScanClient.safeFilename("annual-report.png").equals("annual-report.png"),"r and n must not be removed");
        check(AttachmentScanClient.safeFilename("a\r\nb\\c/\".pdf").equals("a__b_c__.pdf"),"control characters and separators removed");
        check(AttachmentScanClient.safeFilename(null).equals("file"),"missing filename");
        LocalDate sunday=LocalDate.of(2026,10,18);
        check(AiCalendarWeeks.weekStart(sunday).equals(LocalDate.of(2026,10,12)),"Monday start");
        check(AiCalendarWeeks.weekEnd(sunday).equals(sunday),"Sunday end");
        check(scanReply("stream: OK\0",true).equals("OK"),"clean response returns without socket EOF");
        check(scanReply("stream: Eicar-Test-Signature FOUND\0",false).equals("INFECTED"),"malware is distinct from scanner failure");
        boolean failed=false;
        try { scanReply("stream: OK",false); } catch(IOException expected) {failed=true;}
        check(failed,"unterminated response rejected");
        failed=false;
        try { scanReply("stream: scanner ERROR\0",false); } catch(IOException expected) {failed=true;}
        check(failed,"scanner error rejected");
        System.out.println("PASS: "+assertions+" inspection regression assertions");
    }
    private static String scanReply(String reply, boolean keepOpen) throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch release=new CountDownLatch(1);
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            Future<?> peer=executor.submit(() -> {
                try(Socket socket=server.accept()) {
                    DataInputStream input=new DataInputStream(socket.getInputStream());
                    byte[] command=input.readNBytes(10);
                    if(!new String(command,StandardCharsets.US_ASCII).equals("zINSTREAM\0"))throw new IOException("Wrong scan command");
                    int length;
                    while((length=input.readInt())!=0)input.readNBytes(length);
                    socket.getOutputStream().write(reply.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    if(keepOpen)release.await(6,TimeUnit.SECONDS);
                } catch(Exception ex) {throw new RuntimeException(ex);}
            });
            try {
                return AttachmentScanClient.scan("sample".getBytes(StandardCharsets.US_ASCII),
                        server.getInetAddress().getHostAddress(),server.getLocalPort());
            } finally {release.countDown();peer.get(10,TimeUnit.SECONDS);}
        } finally {release.countDown();executor.shutdownNow();}
    }
}
