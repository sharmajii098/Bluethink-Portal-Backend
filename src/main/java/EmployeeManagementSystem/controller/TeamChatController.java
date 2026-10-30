package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.ChatConversation;
import EmployeeManagementSystem.entity.ChatMessage;
import EmployeeManagementSystem.repository.ChatConversationRepository;
import EmployeeManagementSystem.repository.ChatMessageRepository;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.repository.chat.ChatReadReceiptRepository;
import EmployeeManagementSystem.entity.chat.ChatReadReceipt;
import EmployeeManagementSystem.entity.RegisterEmployee;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/team-chat")
public class TeamChatController {
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final RegisterEmployeeRepository users;
    private final ChatReadReceiptRepository receipts;
    public TeamChatController(ChatConversationRepository conversations, ChatMessageRepository messages, RegisterEmployeeRepository users, ChatReadReceiptRepository receipts){this.conversations=conversations;this.messages=messages;this.users=users;this.receipts=receipts;}
    private String currentUser(){Authentication a=SecurityContextHolder.getContext().getAuthentication(); if(a==null||!a.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required"); return a.getName();}
    private ChatConversation memberConversation(Long id){ChatConversation c=conversations.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Conversation not found")); if(!c.getMembers().contains(currentUser())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You are not a member of this conversation"); return c;}
    @GetMapping("/conversations") @Transactional(readOnly=true)
    public List<Map<String,Object>> list(){String user=currentUser(); return conversations.findDistinctByMembersContainingOrderByCreatedAtDesc(user).stream().map(c->{List<ChatMessage> last=messages.findTop1ByConversation_IdOrderByCreatedAtDesc(c.getId()); Map<String,Object> m=new LinkedHashMap<>();m.put("id",c.getId());m.put("title",c.getTitle());m.put("type",c.getType());m.put("members",c.getMembers());m.put("createdAt",c.getCreatedAt());m.put("lastMessage",last.isEmpty()?"":last.get(0).getContent());m.put("unreadCount",messages.unread(c.getId(),user));m.put("lastSender",last.isEmpty()?"":last.get(0).getSender());m.put("lastMessageAt",last.isEmpty()?c.getCreatedAt():last.get(0).getCreatedAt());return m;}).collect(Collectors.toList());}
    public record CreateRequest(String title, String type, List<String> usernames) {}

    private String currentRole(){
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null) return "EMPLOYEE";
        return a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_", "").toUpperCase(Locale.ROOT))
                .filter(Set.of("ADMIN","HR","MANAGER","EMPLOYEE")::contains).findFirst().orElse("EMPLOYEE");
    }
    private boolean canChatWith(RegisterEmployee me, RegisterEmployee other){
        if(me==null||other==null||Objects.equals(me.getUserId(),other.getUserId())) return false;
        String role=currentRole();
        if(Set.of("ADMIN","HR","MANAGER").contains(role)) return true;
        String otherRole=other.getRole()==null?"EMPLOYEE":other.getRole().replace("ROLE_","").toUpperCase(Locale.ROOT);
        if(Set.of("ADMIN","HR","MANAGER").contains(otherRole)) return true;
        if(me.getDepartmentId()!=null && Objects.equals(me.getDepartmentId(),other.getDepartmentId())) return true;
        // Permit direct contact with the employee's reporting manager and the manager's team.
        if(me.getReportingManagerId()!=null && Objects.equals(me.getReportingManagerId(),other.getId())) return true;
        return other.getReportingManagerId()!=null && Objects.equals(other.getReportingManagerId(),me.getId());
    }
    @GetMapping("/people") @Transactional(readOnly=true)
    public List<Map<String,Object>> people(){
        RegisterEmployee me=users.findByUserId(currentUser());
        return users.findAll().stream().filter(u->canChatWith(me,u)).sorted(Comparator.comparing(u->u.getName()==null?u.getUserId():u.getName(),String.CASE_INSENSITIVE_ORDER)).map(u->{
            Map<String,Object> item=new LinkedHashMap<>(); item.put("username",u.getUserId()); item.put("name",u.getName()==null||u.getName().isBlank()?u.getUserId():u.getName());
            item.put("role",u.getRole()==null?"EMPLOYEE":u.getRole().replace("ROLE_","").toUpperCase(Locale.ROOT)); item.put("designation",u.getDesignation()); item.put("departmentId",u.getDepartmentId()); return item;
        }).collect(Collectors.toList());
    }
    @PostMapping("/conversations") @Transactional
    public Map<String,Object> create(@RequestBody CreateRequest req){if(req==null||req.usernames()==null||req.usernames().size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose between 1 and 100 participants");String me=currentUser();String type="GROUP".equalsIgnoreCase(req.type())?"GROUP":"DIRECT";Set<String> members=new LinkedHashSet<>();members.add(me);if(req.usernames()!=null)req.usernames().stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isBlank()).forEach(members::add); RegisterEmployee actor=users.findByUserId(me); for(String username:members){if(username.equals(me))continue; RegisterEmployee target=users.findByUserId(username); if(target==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"User not found: "+username); if(!canChatWith(actor,target))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You can chat only with HR, managers, and your team members");} if(members.size()<2)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Add at least one other employee username");if("DIRECT".equals(type)&&members.size()>2)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Direct messages can have only two participants");String title=req.title()==null||req.title().isBlank()?(type.equals("GROUP")?"New group":"Direct message"):req.title().trim();if(title.length()>120)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Conversation name must be 120 characters or fewer");if("DIRECT".equals(type)){for(ChatConversation existing:conversations.findDistinctByMembersContainingOrderByCreatedAtDesc(me)){if("DIRECT".equals(existing.getType())&&existing.getMembers().equals(members))return Map.of("id",existing.getId(),"title",existing.getTitle(),"type",existing.getType(),"members",existing.getMembers(),"createdAt",existing.getCreatedAt());}}ChatConversation c=conversations.save(new ChatConversation(title,type,me,members));return Map.of("id",c.getId(),"title",c.getTitle(),"type",c.getType(),"members",c.getMembers(),"createdAt",c.getCreatedAt());}
    @GetMapping("/conversations/{id}/messages") @Transactional
    public List<Map<String,Object>> history(@PathVariable Long id,@RequestParam(required=false) Long before) {
        ChatConversation conversation=memberConversation(id);
        List<ChatMessage> history=messages.findByConversation_IdAndIdLessThanOrderByIdDesc(id,before==null?Long.MAX_VALUE:before,org.springframework.data.domain.PageRequest.of(0,100));
        Map<Long,List<ChatReadReceipt>> byMessage=receipts.findByMessageIdIn(history.stream().map(ChatMessage::getId).toList()).stream().collect(Collectors.groupingBy(ChatReadReceipt::getMessageId));
        return history.stream().sorted(Comparator.comparing(ChatMessage::getId)).map(m->messageMap(m,conversation,byMessage.getOrDefault(m.getId(),List.of()))).toList();
    }
    public record ReadRequest(Long throughId) {}
    @PostMapping("/conversations/{id}/read") @Transactional
    public Map<String,Object> markRead(@PathVariable Long id,@RequestBody ReadRequest request) {
        memberConversation(id);String reader=currentUser();
        if(request.throughId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Last visible message is required");
        ChatMessage through=messages.findById(request.throughId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Message not found"));
        if(!id.equals(through.getConversation().getId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Message belongs to another conversation");
        List<ChatMessage> visible=messages.findByConversation_IdAndIdLessThanOrderByIdDesc(id,request.throughId()+1,org.springframework.data.domain.PageRequest.of(0,100));
        Set<Long> read=receipts.findByMessageIdIn(visible.stream().map(ChatMessage::getId).toList()).stream().filter(r->reader.equals(r.getUsername())).map(ChatReadReceipt::getMessageId).collect(Collectors.toSet());
        for(ChatMessage message:visible)if(!reader.equals(message.getSender())&&!read.contains(message.getId()))receipts.save(new ChatReadReceipt(message.getId(),reader));
        return Map.of("success",true,"unreadCount",messages.unread(id,reader));
    }

    /** Hide a conversation for the current user without deleting other participants' history. */
    @DeleteMapping("/conversations/{id}") @Transactional
    public Map<String,Object> deleteConversationForMe(@PathVariable Long id) {
        ChatConversation conversation = memberConversation(id);
        conversation.getMembers().remove(currentUser());
        conversations.save(conversation);
        return Map.of("success", true, "conversationId", id, "deletedForCurrentUser", true);
    }

    /** A sender may retract their own message; other users cannot delete it. */
    @DeleteMapping("/messages/{messageId}") @Transactional
    public Map<String,Object> deleteOwnMessage(@PathVariable Long messageId) {
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        memberConversation(message.getConversation().getId());
        if (!message.getSender().equalsIgnoreCase(currentUser())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can delete only your own messages");
        receipts.deleteByMessageId(messageId);
        messages.delete(message);
        return Map.of("success", true, "messageId", messageId);
    }

    public record SendRequest(String content) {}
    @PostMapping("/conversations/{id}/messages")
    @Transactional
    public Map<String,Object> send(@PathVariable Long id, @RequestBody SendRequest req) {
        ChatConversation conversation = memberConversation(id);
        String content = req == null || req.content() == null ? "" : req.content().trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty");
        }
        // The deployed schema has both content VARCHAR(5000) and body VARCHAR(4000).
        // Enforce the narrower column limit so the insert cannot fail on longer messages.
        if (content.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message exceeds 4000 characters");
        }
        ChatMessage saved = messages.save(new ChatMessage(conversation, currentUser(), content));
        return messageMap(saved, conversation);
    }
    private Map<String,Object> messageMap(ChatMessage m){return messageMap(m,m.getConversation());}
    private Map<String,Object> messageMap(ChatMessage m, ChatConversation conversation){
        return messageMap(m,conversation,receipts.findByMessageId(m.getId()));
    }
    private Map<String,Object> messageMap(ChatMessage m,ChatConversation conversation,List<ChatReadReceipt> read){
        Set<String> readers=read.stream().map(ChatReadReceipt::getUsername).collect(Collectors.toSet());
        List<String> recipients=conversation.getMembers().stream().filter(u->!u.equals(m.getSender())).toList();
        boolean readByRecipient=!recipients.isEmpty()&&recipients.stream().allMatch(readers::contains);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("id",m.getId()); result.put("conversationId",conversation.getId()); result.put("sender",m.getSender()); result.put("content",m.getContent()); result.put("createdAt",m.getCreatedAt()); result.put("readByRecipient",readByRecipient); return result;
    }
}
