package EmployeeManagementSystem.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service public class UploadSafety {
 private final boolean required; private final String host;private final int port;
 public UploadSafety(@Value("${chat.antivirus.required:true}") boolean required,@Value("${chat.antivirus.host:127.0.0.1}") String host,@Value("${chat.antivirus.port:3310}") int port){this.required=required;this.host=host;this.port=port;}
 public String validate(String filename,byte[] bytes){
  String name=AttachmentScanClient.safeFilename(filename);String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
  if(!Set.of("pdf","png","jpg","jpeg","docx","txt").contains(ext)||!ChatFileRules.matches(ext,bytes))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported file or content does not match its extension. Use PDF, DOCX, TXT, PNG or JPG.");
  try{if(!"OK".equals(scan(bytes)))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"File rejected by malware scanning");}
  catch(java.io.IOException e){if(required)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Malware scanner unavailable; upload blocked");}
  return switch(ext){case "pdf"->"application/pdf";case "png"->"image/png";case "jpg","jpeg"->"image/jpeg";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";default->"text/plain";};
 }
 protected String scan(byte[] bytes)throws java.io.IOException{return AttachmentScanClient.scan(bytes,host,port);}
}
