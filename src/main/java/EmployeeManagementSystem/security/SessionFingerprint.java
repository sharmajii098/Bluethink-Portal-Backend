package EmployeeManagementSystem.security;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Store only a non-reusable fingerprint of each bearer credential. */
public final class SessionFingerprint {
 private SessionFingerprint() {}
 public static String of(String token) {
  if(token == null || token.isBlank()) throw new IllegalArgumentException("Missing session credential");
  try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
