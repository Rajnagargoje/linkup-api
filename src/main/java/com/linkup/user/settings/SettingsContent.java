package com.linkup.user.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.List;
import com.linkup.user.exception.ResourceNotFoundException;

@Component
public class SettingsContent {
    public final String operator;
    public final String email;
    public final String downloadUrl;
    public final String webUrl;
    public final String instagramUrl;
    public final String tiktokUrl;
    public SettingsContent(
            @Value("${app.legal.operator-name:Ganesh Nagargoje}") String operator,
            @Value("${app.support-email:gn7057@gmail.com}") String email,
            @Value("${app.download-url:}") String downloadUrl,
            @Value("${app.web-url:}") String webUrl,
            @Value("${app.instagram-url:}") String instagramUrl,
            @Value("${app.tiktok-url:}") String tiktokUrl) {
        this.operator=operator; this.email=email;
        this.downloadUrl=https(downloadUrl); this.webUrl=https(webUrl);
        this.instagramUrl=https(instagramUrl); this.tiktokUrl=https(tiktokUrl);
    }
    private String https(String value) {
        if(value==null||value.isBlank()) return "";
        try { var uri=URI.create(value.strip());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null ? uri.toString() : "";
        } catch(IllegalArgumentException exception) { return ""; }
    }
    public record Section(String title, String text) {}
    public record LegalDocument(String title, String updatedAt, String operator, String contactEmail, List<Section> sections) {}
    public record PublicConfig(String operator, String supportEmail, String downloadUrl, String instagramUrl, String tiktokUrl) {}
    public PublicConfig config() { return new PublicConfig(operator,email,downloadUrl,instagramUrl,tiktokUrl); }
    private Section section(String title,String text) { return new Section(title,text); }

    public LegalDocument document(String kind) {
        if("privacy".equals(kind)) return new LegalDocument("Privacy policy","2026-10-01",operator,email,List.of(
            section("About this policy","LinkUp helps adults meet people, make friends and chat. This policy explains the information used by the app and the controls available to you."),
            section("Account and profile information","We store your username, email address, a password hash, verification status and account activity. Profile information can include your date of birth, gender, photos, bio, interests and matching preferences. Other users can see the profile information you choose to provide; your email, password and exact date of birth are not displayed on your public profile."),
            section("Location and permissions","When you use nearby discovery, the app can request your device location and send coordinates to the server to calculate distance. Other people see an approximate distance rather than your coordinates. Turning off discovery hides you from People and Nearby; it does not erase a previously saved location. You can revoke location or camera access in device settings. Camera and photo access are used when you choose to add a photo. Notification permission is used for push alerts."),
            section("Messages and safety records","The service processes and stores messages to deliver conversations and chat history. It also stores friend requests, blocks, reports, notification preferences and support requests. Messages are processed on our servers; the app does not provide end-to-end encrypted messaging. Avoid sending passwords, financial details or other sensitive information in chat."),
            section("How information is used","Information is used to operate your account, show profiles and nearby results, deliver messages and notifications, apply your settings, respond to support requests and investigate misuse. Push delivery uses a device registration token. Email verification, hosting, database, media storage and push providers process the information necessary to provide those services."),
            section("Sharing and profile links","Your profile information and messages are shared with the people and conversations you interact with. A profile invite link or QR code can show your username and let a recipient open your profile in LinkUp. Anyone who receives the link may forward it. Service administrators may access records when providing support or reviewing reports. Data may also need to be disclosed where required by law."),
            section("Your privacy controls","Account privacy lets you hide from discovery, hide your online and last-seen status, and turn off direct message requests from non-friends. Friends can still message you. Choosing to enter a random chat or a room is a separate action. Hiding discovery does not make your profile private to someone who already has your profile link. You can block users, manage notifications and edit your profile."),
            section("Account deletion and retention","Deleting an account disables access, invalidates its sessions and anonymizes identifying profile fields used by the service. Existing conversation content, reports, support records and some account records can remain in the database. Copies already received or saved by other people cannot be removed from their devices by LinkUp. Use a Privacy request in Help & support, or contact the email below, to request access, correction or further deletion of your information. We may need to verify account ownership before acting."),
            section("Age and policy updates","LinkUp is intended for people aged 18 or older. If you believe a child has provided personal information, contact support. This page is updated when the app's data practices change; the date above identifies this version."),
            section("Contact", "For privacy questions or requests, contact " + operator + " at " + email + ". You can also use Settings → Help & support → Privacy request.")
        ));
        if("terms".equals(kind)) return new LegalDocument("Terms & community rules","2026-10-01",operator,email,List.of(
            section("Using LinkUp","You must be at least 18 and provide accurate account information. Keep your login details secure and use the service responsibly."),
            section("Respect other people","Do not harass, threaten, impersonate, scam or discriminate against others. Do not share illegal content, sexual content involving minors, private information without permission or content that exploits other people. Respect blocks and requests to stop contact."),
            section("Your content","Only upload photos and content you have permission to share. You retain your rights in your content and permit LinkUp to store, process and display it as needed to provide the features you use. You are responsible for what you send and publish."),
            section("Profiles and meeting safely","Profiles and verification indicators are not guarantees about someone's identity or intentions. Use your own judgement when sharing information or arranging a meeting. Report concerning behavior using the available report controls."),
            section("Moderation","LinkUp may review reports and restrict or remove accounts or content that violate these rules. Blocking prevents contact through supported app features. Unblocking does not restore a removed friendship or withdraw a report."),
            section("Availability and account controls","Features may change and the service can be interrupted. You can manage permissions and privacy settings, leave conversations, log out or delete your account. The privacy policy describes data handling and deletion behavior."),
            section("Questions or disputes","Contact " + operator + " at " + email + " or submit a Help & support request. These rules do not remove rights you have under applicable law.")
        ));
        if("deletion".equals(kind)) return new LegalDocument("Account & data deletion","2026-10-01",operator,email,List.of(
            section("Delete from the app","Sign in to LinkUp, open Settings → Account, and choose Delete account. Read the confirmation before continuing."),
            section("Request deletion without the app","Email " + email + " from the email address associated with your account. Use the subject LinkUp account deletion and include your username. Never include your password or a verification code. Support may ask for account ownership verification."),
            section("What happens","The existing account deletion flow disables the account, invalidates sessions and anonymizes identifying profile fields. Conversation content, safety reports, support records and some account records can remain. Specify any further data deletion you are requesting so support can review and process it.")));
        throw new ResourceNotFoundException("Document not found.");
    }
}
