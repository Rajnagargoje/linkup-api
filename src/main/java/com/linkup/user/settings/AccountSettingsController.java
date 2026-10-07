package com.linkup.user.settings;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/settings")
public class AccountSettingsController {
    private final AccountSettingsService settings;
    public AccountSettingsController(AccountSettingsService settings) { this.settings=settings; }
    @GetMapping("/privacy") public AccountSettingsService.Privacy privacy(Principal p) { return settings.privacy(p.getName()); }
    @PutMapping("/privacy") public AccountSettingsService.Privacy save(Principal p,@Valid @RequestBody AccountSettingsService.Privacy value) { return settings.savePrivacy(p.getName(),value); }
    @GetMapping("/blocked") public AccountSettingsService.PageResult<AccountSettingsService.BlockedUser> blocked(Principal p,@RequestParam(defaultValue="0") int page) { return settings.blocked(p.getName(),page); }
    @DeleteMapping("/blocked/{id}") public void unblock(Principal p,@PathVariable Long id) { settings.unblock(p.getName(),id); }
    @GetMapping("/support") public AccountSettingsService.PageResult<AccountSettingsService.TicketView> tickets(Principal p,@RequestParam(defaultValue="0") int page) { return settings.tickets(p.getName(),page); }
    @GetMapping("/support/{id}") public AccountSettingsService.TicketView ticket(Principal p,@PathVariable Long id) { return settings.ticket(p.getName(),id); }
    @PostMapping("/support") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public AccountSettingsService.TicketView support(Principal p,@Valid @RequestBody AccountSettingsService.SupportRequest value) { return settings.createTicket(p.getName(),value); }
}
