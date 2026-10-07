package com.linkup.user.randomai;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;

/** PostgreSQL atomic counters. Failed or cancelled provider requests keep their reservation. */
@Service @RequiredArgsConstructor
public class AiBudget {
    private final JdbcTemplate jdbc;
    private final AiProperties settings;
    @Transactional
    public void reserve(String ownerKey, long maximumInputTokens, int maximumOutputTokens) {
        double inputRate = settings.getInputUsdPerMillion(), outputRate = settings.getOutputUsdPerMillion();
        if (!(inputRate > 0) || !(outputRate > 0) || !(settings.getDailyBudgetUsd() > 0))
            throw new IllegalArgumentException("AI chat is temporarily unavailable. You can keep looking for a person.");
        long micros = Math.max(1, (long) Math.ceil(maximumInputTokens * inputRate + maximumOutputTokens * outputRate));
        long cap = (long) Math.floor(settings.getDailyBudgetUsd() * 1_000_000);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        debit(today + ":global", today, micros, cap, settings.getDailyRequests());
        debit(today + ":" + hash(ownerKey), today, 0, Long.MAX_VALUE, settings.getUserDailyRequests());
        // Bounded retention; budget keys contain a hash, never a username or transcript.
        jdbc.update("delete from ai_daily_usage where usage_day < ?", today.minusDays(7));
    }
    private void debit(String key, LocalDate day, long amount, long limit, int requests) {
        jdbc.update("insert into ai_daily_usage(bucket_key,usage_day,requests,reserved_micros) values(?,?,0,0) on conflict do nothing", key, day);
        int updated = jdbc.update("update ai_daily_usage set requests=requests+1,reserved_micros=reserved_micros+? where bucket_key=? and requests<? and reserved_micros<=?",
            amount, key, Math.max(0, requests), limit - amount);
        if (updated != 1) throw new IllegalArgumentException("Today's AI chat limit has been reached. You can still chat with people.");
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Could not reserve AI usage."); }
    }
}
