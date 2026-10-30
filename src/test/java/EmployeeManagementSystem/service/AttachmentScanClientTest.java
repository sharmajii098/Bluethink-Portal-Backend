package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentScanClientTest {
    @Test void rejectsInvalidPayloadBeforeConnecting() {
        assertThrows(IOException.class, () -> AttachmentScanClient.scan(null,"127.0.0.1",3310));
        assertThrows(IOException.class, () -> AttachmentScanClient.scan(new byte[0],"127.0.0.1",3310));
        assertThrows(IOException.class, () -> AttachmentScanClient.scan(new byte[10*1024*1024+1],"127.0.0.1",3310));
    }
    private String check(String response) throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            ExecutorService worker=Executors.newSingleThreadExecutor();
            try {
                Future<?> task=worker.submit(() -> {
                    try(Socket peer=server.accept()) {
                        DataInputStream stream=new DataInputStream(peer.getInputStream());
                        byte[] command=new byte[10];stream.readFully(command);
                        assertEquals("zINSTREAM\0",new String(command,StandardCharsets.US_ASCII));
                        assertEquals(3,stream.readInt());assertEquals('a',stream.readUnsignedByte());
                        assertEquals('b',stream.readUnsignedByte());assertEquals('c',stream.readUnsignedByte());
                        assertEquals(0,stream.readInt());
                        peer.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                        peer.getOutputStream().flush();
                    }catch(IOException e){throw new UncheckedIOException(e);}
                });
                String verdict=AttachmentScanClient.scan("abc".getBytes(StandardCharsets.US_ASCII),"127.0.0.1",server.getLocalPort());
                task.get(3,TimeUnit.SECONDS);return verdict;
            }finally{worker.shutdownNow();}
        }
    }
    @Test void usesClamAvNulTerminators() throws Exception {
        assertEquals("OK",check("stream: OK\0"));
        assertEquals("INFECTED",check("stream: Eicar-Test-Signature FOUND\0"));
    }
}
