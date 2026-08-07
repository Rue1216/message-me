package com.esun.social.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 整合測試的共用基底：啟動一座真實的 MySQL 8.4，並載入 {@code DB/} 之下的正式腳本。
 *
 * <p>測試對象因此是<strong>實際會部署的那份 SQL</strong>，而不是為測試另寫的簡化版：
 * Stored Procedure 的交易語意、EXIT HANDLER、定序設定與最小權限授權都在測試中生效。
 *
 * <h2>三個刻意的選擇</h2>
 * <ul>
 *   <li><strong>用 {@code withCopyFileToContainer} 而非 {@code withInitScript}</strong>：
 *       後者以單一批次送出整份腳本，無法處理 {@code DELIMITER}——而 SP 的定義全靠它。
 *       複製到 {@code /docker-entrypoint-initdb.d/} 則是由容器內的 mysql client 依檔名順序執行，
 *       行為與 docker compose 啟動時完全一致。</li>
 *   <li><strong>單例容器，不用 {@code @Container}</strong>：
 *       {@code @Container} 標註的靜態欄位會在每個測試類別的 afterAll 被停掉，
 *       繼承同一個基底的下一個類別就會連到已停止的容器。改由靜態區塊啟動、
 *       全程不關閉，由 Testcontainers 的 Ryuk 在 JVM 結束後回收，
 *       整個測試回合只付一次約 30 秒的啟動成本。</li>
 *   <li><strong>使用者設為 {@code app_user}</strong>：
 *       與 docker compose 相同的帳號名稱，{@code 01_DDL_schema.sql} 的 REVOKE/GRANT 才會作用在它身上。
 *       測試因此是在「只有 EXECUTE 權限」的條件下進行，與正式環境一致。</li>
 * </ul>
 */
@SpringBootTest
public abstract class MySqlContainerSupport {

    /** 相對於 backend 模組的專案根目錄；測試的工作目錄為 {@code backend/}。 */
    private static final Path DB_DIRECTORY = Path.of("..", "DB");

    private static final List<String> INIT_SCRIPTS =
            List.of("01_DDL_schema.sql", "02_DDL_stored_procedures.sql", "03_DML_seed_data.sql");

    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL = createContainer();

    static {
        MYSQL.start();
    }

    private static MySQLContainer<?> createContainer() {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("message_me")
                .withUsername("app_user")
                .withPassword("test-app-password")
                // 與 docker-compose.yml 相同的連線參數：多語句在驅動層即被禁止
                .withUrlParam("allowMultiQueries", "false")
                .withUrlParam("useSSL", "false")
                .withUrlParam("allowPublicKeyRetrieval", "true")
                .withUrlParam("characterEncoding", "utf8");

        for (String script : INIT_SCRIPTS) {
            container.withCopyFileToContainer(
                    MountableFile.forHostPath(resolveScript(script), 0644),
                    "/docker-entrypoint-initdb.d/" + script);
        }
        return container;
    }

    private static Path resolveScript(String fileName) {
        Path path = DB_DIRECTORY.resolve(fileName);
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(
                    "找不到資料庫腳本 " + path.toAbsolutePath()
                            + "；整合測試必須從 backend/ 目錄執行，才能讀到專案根目錄的 DB/。");
        }
        return path;
    }
}
