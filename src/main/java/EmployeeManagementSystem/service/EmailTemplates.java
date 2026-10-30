package EmployeeManagementSystem.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import java.util.Arrays;
import java.util.stream.Collectors;

/** One escaped, responsive HTML template with an identical plain-text alternative. */
public final class EmailTemplates {
 private EmailTemplates() {}
 public static String escape(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
 public static String html(String subject,String text,String action,String url){
  String paragraphs=Arrays.stream((text==null?"":text).split("\\n\\n")).map(p->"<p style=\"margin:0 0 18px;line-height:1.7\">"+escape(p).replace("\n","<br>")+"</p>").collect(Collectors.joining());
  String button="";
  if(url!=null){var uri=java.net.URI.create(url);if(!"https".equals(uri.getScheme())&&!("http".equals(uri.getScheme())&&java.util.Set.of("localhost","127.0.0.1").contains(uri.getHost())))throw new IllegalArgumentException("Email action requires HTTPS");button="<p style=\"margin:26px 0\"><a href=\""+escape(url)+"\" style=\"display:inline-block;background:#205c42;color:#fff;padding:14px 24px;border-radius:6px;text-decoration:none;font-weight:bold\">"+escape(action)+"</a></p>";}
  return "<!doctype html><html lang=\"en\"><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body style=\"margin:0;background:#f2f1e9;color:#25352b;font-family:Arial,sans-serif\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:28px 12px\"><table role=\"presentation\" width=\"600\" style=\"max-width:100%;background:#fffefa;border:1px solid #dde1d5;border-radius:12px\" cellpadding=\"28\"><tr><td style=\"background:#243a2d;color:#fff\"><strong style=\"font-size:22px\">BlueThink Inc.</strong><br><span style=\"font-size:12px;color:#cbd8bc\">Think beyond.</span></td></tr><tr><td><h1 style=\"font-size:23px;line-height:1.3;margin:0 0 24px\">"+escape(subject)+"</h1>"+paragraphs+button+"</td></tr><tr><td style=\"border-top:1px solid #e3e6dc;color:#64705f;font-size:12px;line-height:1.6\">BlueThink workforce portal · This message is intended for the addressed recipient. For assistance, contact your company HR or IT team. Never share passwords or account setup links.</td></tr></table></td></tr></table></body></html>";
 }
 public static void send(JavaMailSender sender,SimpleMailMessage source){send(sender,source,null,null);}
 public static void send(JavaMailSender sender,SimpleMailMessage source,String action,String url){
  try { var message=sender.createMimeMessage();var helper=new MimeMessageHelper(message,true,"UTF-8");
  if(source.getFrom()!=null&&!source.getFrom().isBlank())helper.setFrom(source.getFrom());
  if(source.getTo()!=null)helper.setTo(source.getTo());if(source.getCc()!=null)helper.setCc(source.getCc());if(source.getBcc()!=null)helper.setBcc(source.getBcc());
  String subject=source.getSubject()==null?"BlueThink portal update":source.getSubject().replaceAll("[\\r\\n]"," ");helper.setSubject(subject);
  helper.setText(source.getText()==null?"":source.getText(),html(subject,source.getText(),action,url));sender.send(message); } catch(MessagingException e){throw new org.springframework.mail.MailPreparationException("Unable to render email",e);}
 }
}
