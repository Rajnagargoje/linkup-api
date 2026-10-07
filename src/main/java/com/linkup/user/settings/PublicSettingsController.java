package com.linkup.user.settings;

import com.linkup.user.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.util.HtmlUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class PublicSettingsController {
    private final SettingsContent content;
    private final UserRepository users;
    public PublicSettingsController(SettingsContent content, UserRepository users) { this.content=content; this.users=users; }
    @GetMapping("/api/public/settings") public SettingsContent.PublicConfig config() { return content.config(); }
    @GetMapping("/api/public/legal/{kind}") public SettingsContent.LegalDocument document(@PathVariable String kind) { return content.document(kind); }
    @GetMapping(value="/privacy-policy",produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<String> privacy() { return legal("privacy"); }
    @GetMapping(value="/terms",produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<String> terms() { return legal("terms"); }
    @GetMapping(value="/account-deletion",produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<String> deletion() { return legal("deletion"); }
    private ResponseEntity<String> legal(String kind) {
        var doc=content.document(kind);
        StringBuilder body=new StringBuilder("<p class='eyebrow'>LINKUP</p><h1>"+escape(doc.title())+"</h1><p>Updated "+doc.updatedAt()+" · "+escape(doc.operator())+"</p>");
        doc.sections().forEach(s->body.append("<section><h2>").append(escape(s.title())).append("</h2><p>").append(escape(s.text())).append("</p></section>"));
        body.append("<a href='mailto:").append(escape(content.email)).append("'>Contact support</a>");
        return page(doc.title(),body.toString(),HttpStatus.OK);
    }
    @GetMapping(value="/invite/{publicId}",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> invite(@PathVariable String publicId) {
        if(publicId.length()>80) return page("Invite unavailable","<h1>This invite is unavailable.</h1>",HttpStatus.NOT_FOUND);
        var user=users.findByPublicId(publicId).filter(u->Boolean.TRUE.equals(u.getIsActive()) && !Boolean.TRUE.equals(u.getIsDeleted()) && !Boolean.TRUE.equals(u.getIsBanned()));
        if(user.isEmpty()) return page("Invite unavailable","<h1>This invite is unavailable.</h1>",HttpStatus.NOT_FOUND);
        String id=URLEncoder.encode(publicId,StandardCharsets.UTF_8);
        String body="<p class='eyebrow'>LINKUP</p><h1>Meet @"+escape(user.get().getUsername())+"</h1><p>Open their profile in LinkUp. Sign in or create your account to connect.</p>"
            +"<a class='button' href='linkup://profile/"+id+"'>Open LinkUp</a>";
        if(!content.webUrl.isEmpty()) body+="<p><a href='"+escape(content.webUrl+(content.webUrl.contains("?")?"&":"?")+"invite="+id)+"'>Continue on the web</a></p>";
        if(!content.downloadUrl.isEmpty()) body+="<p><a href='"+escape(content.downloadUrl)+"'>Get the LinkUp app</a></p>";
        else body+="<p class='muted'>Don't have LinkUp yet? Ask the person who invited you for the current app download.</p>";
        body+="<footer><a href='/privacy-policy'>Privacy</a> · <a href='/terms'>Terms</a></footer>";
        return page("LinkUp invitation",body,HttpStatus.OK);
    }
    private static String escape(String value) { return HtmlUtils.htmlEscape(value==null?"":value); }
    private ResponseEntity<String> page(String title,String body,HttpStatus status) {
        String html="<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='robots' content='noindex'><title>"+escape(title)+" · LinkUp</title><style>body{margin:0;background:#f6f5fb;color:#272438;font:16px/1.7 system-ui,sans-serif}main{max-width:700px;margin:32px auto;padding:28px;background:white;border-radius:24px}h1{font-size:32px;line-height:1.2}h2{font-size:19px;margin-top:30px}a{color:#7055c7;overflow-wrap:anywhere}.eyebrow{color:#7055c7;font-weight:800;letter-spacing:3px}.button{display:inline-block;background:#7055c7;border-radius:16px;padding:13px 24px;color:white;text-decoration:none}.muted,footer{color:#686377;font-size:14px}footer{margin-top:40px}@media(max-width:760px){main{margin:16px;padding:24px}}</style></head><body><main>"+body+"</main></body></html>";
        return ResponseEntity.status(status).contentType(new MediaType("text","html",StandardCharsets.UTF_8))
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
            .body(html);
    }
}
