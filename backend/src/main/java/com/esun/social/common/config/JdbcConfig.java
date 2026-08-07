package com.esun.social.common.config;

import com.esun.social.data.support.StoredProcedureCallFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 資料層存取設定。
 *
 * <p>{@code JdbcTemplate} 與 {@code DataSource} 由 Spring Boot 依環境變數
 * （{@code SPRING_DATASOURCE_*}，見 docker-compose.yml）自動組態，此處只補上
 * 專案自訂的呼叫工廠。連線字串刻意帶 {@code allowMultiQueries=false}，
 * 讓「一次送出多段 SQL」在驅動層就被拒絕。
 */
@Configuration
public class JdbcConfig {

    @Bean
    StoredProcedureCallFactory storedProcedureCallFactory(JdbcTemplate jdbcTemplate) {
        return new StoredProcedureCallFactory(jdbcTemplate);
    }
}
