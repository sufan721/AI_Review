package com.aireview.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 集成测试基类。
 *
 * <p>默认启动一个与 deploy/docker-compose.yml 相同版本的 MySQL 容器，并把 docs/sql/schema.sql
 * 挂到容器的初始化目录，保证测试库结构与文档中的唯一建表来源一致。
 *
 * <p>若所在环境无法启动 Testcontainers（例如宿主已经是容器、Docker socket 不可用），
 * 可设置环境变量 {@code TEST_DB_URL} 指向一个已建表的 MySQL，直接复用而不启动容器：
 * <pre>
 * TEST_DB_URL='jdbc:mysql://localhost:3307/memo?useUnicode=true&amp;characterEncoding=UTF-8' \
 * TEST_DB_USERNAME=root TEST_DB_PASSWORD=root mvn test
 * </pre>
 * 注意：测试会清空所用库中的业务表，必须指向专用测试库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IndexingTestConfig.class)
public abstract class AbstractIntegrationTest {
    private static final String CONTAINER_SCHEMA_PATH = "/docker-entrypoint-initdb.d/01-schema.sql";

    @Autowired
    private ControlledTaskExecutor taskExecutor;

    @Autowired
    private InMemoryVectorStore vectorStore;

    @Autowired
    private FakeEmbeddingClient embeddingClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 每个用例前清空索引测试双与切片表，保证索引状态跨测试隔离。 */
    @BeforeEach
    void resetIndexing() {
        taskExecutor.clear();
        vectorStore.clear();
        embeddingClient.setFail(false);
        jdbcTemplate.execute("TRUNCATE TABLE `document_chunk`");
    }

    private static final String EXTERNAL_DB_URL = System.getenv("TEST_DB_URL");

    /** 静态单例：整个测试 JVM 只启动一次容器。 */
    private static final MySQLContainer<?> MYSQL = EXTERNAL_DB_URL == null ? startMySql() : null;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (MYSQL == null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB_URL);
            registry.add("spring.datasource.username", () -> envOrDefault("TEST_DB_USERNAME", "root"));
            registry.add("spring.datasource.password", () -> envOrDefault("TEST_DB_PASSWORD", "root"));
            return;
        }
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    private static MySQLContainer<?> startMySql() {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("memo")
            .withUsername("memo")
            .withPassword("memo")
            .withCopyFileToContainer(MountableFile.forHostPath(schemaScript()), CONTAINER_SCHEMA_PATH);
        container.start();
        return container;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 兼容从 backend 目录或仓库根目录执行 mvn test 两种工作目录。 */
    private static Path schemaScript() {
        List<Path> candidates = List.of(
            Path.of("..", "docs", "sql", "schema.sql"),
            Path.of("docs", "sql", "schema.sql"));
        return candidates.stream()
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .filter(Files::exists)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "找不到 docs/sql/schema.sql，请在仓库根目录或 backend 目录下执行测试"));
    }
}
