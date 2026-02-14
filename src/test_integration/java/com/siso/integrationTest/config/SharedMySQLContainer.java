package com.siso.integrationTest.config;

import org.testcontainers.containers.MySQLContainer;

/**
 * 모든 통합 테스트에서 공유하는 MySQL 컨테이너
 *
 * - CI 환경: GitHub Actions services로 MySQL 실행 (Testcontainers 비활성화)
 * - 로컬 환경: Testcontainers로 MySQL 컨테이너 자동 실행
 */
public class SharedMySQLContainer {

    private static final MySQLContainer<?> MYSQL;
    private static final boolean USE_CI_DATABASE;

    static {
        USE_CI_DATABASE = System.getenv("CI_DATABASE_URL") != null;

        if (USE_CI_DATABASE) {
            MYSQL = null;
        } else {
            MYSQL = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("siso_test")
                    .withUsername("test")
                    .withPassword("test");
            MYSQL.start();
        }
    }

    public static MySQLContainer<?> getInstance() {
        return MYSQL;
    }

    public static boolean useCiDatabase() {
        return USE_CI_DATABASE;
    }

    public static String getJdbcUrl() {
        return USE_CI_DATABASE ? System.getenv("CI_DATABASE_URL") : MYSQL.getJdbcUrl();
    }

    public static String getUsername() {
        return USE_CI_DATABASE ? System.getenv("CI_DATABASE_USERNAME") : MYSQL.getUsername();
    }

    public static String getPassword() {
        return USE_CI_DATABASE ? System.getenv("CI_DATABASE_PASSWORD") : MYSQL.getPassword();
    }
}
