package EmployeeManagementSystem.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** ClamAV INSTREAM client; responses are NUL terminated, not EOF terminated. */
public final class AttachmentScanClient {
    private AttachmentScanClient() { }
    public static String safeFilename(String name) {
        return Optional.ofNullable(name).orElse("file").replaceAll("[\\p{Cntrl}\\\\/\"]", "_");
    }
    public static String scan(byte[] bytes, String host, int port) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > 10*1024*1024)
            throw new IOException("Unsupported scanner input size");
        if (host == null || host.isBlank() || port < 1 || port > 65535)
            throw new IOException("Malware scanner endpoint is not configured");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < bytes.length;) {
                int size = Math.min(8192, bytes.length - offset);
                out.writeInt(size); out.write(bytes, offset, size); offset += size;
            }
            out.writeInt(0); out.flush();
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            InputStream input = socket.getInputStream();
            int ch; boolean terminated = false;
            while ((ch = input.read()) != -1) {
                if (ch == 0) { terminated = true; break; }
                if (reply.size() >= 1024) throw new IOException("Invalid scanner response");
                reply.write(ch);
            }
            if (!terminated) throw new IOException("Incomplete scanner response");
            String answer = reply.toString(StandardCharsets.US_ASCII).trim();
            if (answer.equals("stream: OK")) return "OK";
            if (answer.startsWith("stream: ") && answer.endsWith(" FOUND")) return "INFECTED";
            throw new IOException("Malware scanner error");
        }
    }
}
