package com.siso.integrationTest.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Repository 테스트 기본 클래스
 *
 * @DataJpaTest를 사용하여 JPA 관련 컴포넌트만 로드합니다.
 * CI: GitHub Actions MySQL 서비스, 로컬: Testcontainers MySQL 컨테이너
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestBase.QueryDslConfig.class)
@ComponentScan(basePackages = "com.siso.user.infrastructure.persistence")
public abstract class RepositoryTestBase {
    @TestConfiguration
    static class QueryDslConfig {
        @Bean
        public JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedMySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", SharedMySQLContainer::getUsername);
        registry.add("spring.datasource.password", SharedMySQLContainer::getPassword);
    }
}
