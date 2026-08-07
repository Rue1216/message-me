package com.esun.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.esun.social.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 煙霧測試：完整的 Spring 應用程式（含 DataSource）能在真實資料庫上啟動。
 *
 * <p>自 PR #4 起應用程式必須連得上資料庫才算啟動成功，因此這個測試改為整合測試，
 * 由 {@link MySqlContainerSupport} 提供資料庫。
 */
class MessageMeApplicationIT extends MySqlContainerSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Spring Context 載入成功，共用層與資料層的 Bean 都就位")
    void contextLoads() {
        assertThat(applicationContext.getBean(com.esun.social.common.exception.GlobalExceptionHandler.class))
                .isNotNull();
        assertThat(applicationContext.getBean(com.esun.social.data.support.StoredProcedureCallFactory.class))
                .isNotNull();
    }
}
