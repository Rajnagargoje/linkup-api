package com.linkup.user;

import com.linkup.user.randomai.AiBudget;
import com.linkup.user.randomai.AiProperties;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AiBudgetTest {
    JdbcTemplate jdbc;
    AiProperties settings;
    AiBudget budget;
    TransactionTemplate transaction;
    @BeforeEach void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table ai_daily_usage(bucket_key varchar(100) primary key, usage_day date not null, requests bigint not null, reserved_micros bigint not null)");
        settings = new AiProperties();
        budget = new AiBudget(jdbc, settings);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    void reserve(String owner) { transaction.executeWithoutResult(status -> budget.reserve(owner, 1000, 220)); }
    long global(String column) { return jdbc.queryForObject("select " + column + " from ai_daily_usage where bucket_key like '%:global'", Long.class); }
    @Test void reservesConservativeCostAndStoresNoAccountIdentifier() {
        reserve("u:alice");
        assertEquals(752, global("reserved_micros"));
        assertEquals(1, global("requests"));
        assertEquals(2, jdbc.queryForObject("select count(*) from ai_daily_usage", Integer.class));
        assertFalse(jdbc.queryForList("select bucket_key from ai_daily_usage", String.class).stream().anyMatch(key -> key.contains("alice")));
    }
    @Test void perUserRejectionRollsBackGlobalReservationAndOthersCanContinue() {
        settings.setUserDailyRequests(1);
        reserve("u:alice");
        assertThrows(IllegalArgumentException.class, () -> reserve("u:alice"));
        assertEquals(1, global("requests"));
        reserve("u:bob");
        assertEquals(2, global("requests"));
    }
    @Test void dollarLimitCannotBeExceededEvenAfterServiceRestart() {
        settings.setDailyBudgetUsd(0.001);
        reserve("u:alice");
        budget = new AiBudget(jdbc, settings);
        assertThrows(IllegalArgumentException.class, () -> reserve("u:bob"));
        assertEquals(752, global("reserved_micros"));
        assertEquals(1, global("requests"));
    }
    @Test void requestCountAlsoCapsSpending() {
        settings.setDailyRequests(1);
        reserve("u:alice");
        assertThrows(IllegalArgumentException.class, () -> reserve("u:bob"));
        assertEquals(1, global("requests"));
    }
}
