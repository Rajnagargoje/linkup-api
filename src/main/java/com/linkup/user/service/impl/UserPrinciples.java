package com.linkup.user.service.impl;


import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.linkup.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


@SuppressWarnings("serial")
public class UserPrinciples implements UserDetails {

    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;

    // Wired into isEnabled()/isAccountNonLocked() below so Spring
    // Security's own DaoAuthenticationProvider rejects banned/deleted/
    // locked accounts at login time — before, these flags existed on the
    // entity but nothing actually enforced them.
    private final boolean active;
    private final boolean banned;
    private final boolean deleted;
    private final LocalDateTime lockedUntil;
    private final Integer tokenVersion;

    public UserPrinciples(User user) {
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().name()));
        this.active = Boolean.TRUE.equals(user.getIsActive());
        this.banned = Boolean.TRUE.equals(user.getIsBanned());
        this.deleted = Boolean.TRUE.equals(user.getIsDeleted());
        this.lockedUntil = user.getLockedUntil();
        this.tokenVersion = user.getTokenVersion();
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !deleted;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active && !banned && !deleted;
    }
}
