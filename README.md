# SISO Backend


> 실시간 채팅 · 음성 통화를 기반으로 신뢰도 높은 연결을 제공하는 매칭 플랫폼 백엔드 시스템


단순 텍스트 기반 매칭의 한계를 개선하기 위해
**실시간 상호작용(채팅 · 음성 통화)** 중심으로 신뢰를 형성하는 구조를 설계했습니다.


---


## 1. 프로젝트 소개


### 기획 배경


기존 소개팅 서비스는 프로필과 텍스트 기반 대화에 의존합니다.
하지만 실제 신뢰 형성은 **실시간 상호작용**에서 이루어진다고 판단했습니다.


SISO는 다음을 목표로 설계되었습니다:


- 실시간 채팅 및 음성 통화를 통한 신뢰도 형성
- JWT + OAuth2 기반 인증/인가 시스템 구축
- 확장 가능한 비동기 메시징 구조 설계
- 캐시 계층을 통한 성능 최적화
- 테스트 자동화 + CI/CD 기반 안정적 배포


---


## 2. 기술 스택


| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Security | Spring Security, JWT (jjwt), OAuth2 (Kakao) |
| ORM | Spring Data JPA, QueryDSL |
| Database | MariaDB / MySQL 8 |
| Cache | Redis |
| Message Queue | RabbitMQ |
| Real-Time | WebSocket (STOMP) |
| Call | Agora RTC SDK |
| Push | Firebase Cloud Messaging |
| Storage | AWS S3 |
| Infra | Docker, Docker Compose, AWS EC2 |
| CI/CD | GitHub Actions, AWS CodeDeploy |
| Test | JUnit5, Mockito, AssertJ, Testcontainers |
| Docs | Swagger (springdoc-openapi) |


---


## 3. 시스템 아키텍처


```
                              ┌──────────┐
                              │  Client  │
                              └────┬─────┘
                                   │
                                   ▼
                       ┌───────────────────────┐
                       │   Spring Boot Server  │
                       │ (REST API + Security) │
                       └───────────┬───────────┘
                                   │
      ┌──────────┬─────────────────┼─────┬──────────┬──────────┐
      │          │           │           │          │          │
      ▼          ▼           ▼           ▼          ▼          ▼
 ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌───────┐ ┌───────┐
 │  Redis  │ │RabbitMQ │ │ MariaDB │ │  AWS S3 │ │ Agora │ │  FCM  │
 │ (Cache) │ │ (Queue) │ │  (DB)   │ │ (File)  │ │(Voice)│ │(Push) │
 └─────────┘ └─────────┘ └─────────┘ └─────────┘ └───────┘ └───────┘
```

### 설계 원칙

- **DDD 기반 패키지 구조**
    - 도메인별 패키지 분리 (call, chat, user, matching, notification 등)
    - 계층 분리: application / domain / presentation / infrastructure / dto
- **계층별 역할 분리**
    - Controller → 요청/응답만 담당
    - Service → 비즈니스 로직 집중
    - Entity → 도메인 로직 캡슐화 (상태 전이, 유효성 검증)
- **인프라 연동**
    - RabbitMQ → 채팅/알림 비동기 처리
    - Redis → 매칭 결과 캐싱
    - FCM → 푸시 알림
- **상태 관리**
    - Enum 기반 상태 관리 (CallStatus, ChatRoomStatus, PresenceStatus 등)


---


## 4. 프로젝트 구조


```
com.siso
├── call/              # 음성 통화 (Agora RTC)
├── callreview/        # 통화 리뷰
├── chat/              # 채팅 (WebSocket + RabbitMQ)
├── common/            # 공통 설정, 보안, 예외 처리
├── image/             # 이미지 관리 (S3)
├── matching/          # AI 매칭 알고리즘
├── notification/      # 푸시 알림 (FCM)
├── report/            # 신고
├── user/              # 사용자, 인증, 프로필
└── voicesample/       # 음성 샘플 (S3)
```


---


## 5. 핵심 기능


### 인증 / 인가


- Kakao OAuth2 소셜 로그인
- JWT Access Token (2시간)
- JWT Refresh Token (2주)
- Spring Security 필터 체인 기반 인증 처리
- Refresh Token Rotation 구조 설계


---


### AI 매칭 알고리즘


6가지 지표를 가중치 기반으로 계산하여 상위 20명 추천


| 지표 | 가중치 | 방식 |
|------|--------|------|
| 관심사 유사도 | 30% | Jaccard Similarity |
| 나이 호환성 | 20% | 거리 기반 감쇠 |
| MBTI | 15% | 궁합 매트릭스 |
| 지역 | 15% | 지역 단위 비교 |
| 활동성 | 10% | 최근 접속 시간 |
| 생활습관 | 10% | 음주/흡연 호환 |


#### 성능 개선


- Redis 캐싱 (TTL 10분)
- RabbitMQ Consumer 3~10개 병렬 처리
- 1000명 기준 성능 테스트 진행


---


### 실시간 채팅 시스템


- WebSocket (STOMP) 기반 양방향 통신
- RabbitMQ 기반 메시지 안정성 확보
- 온라인 사용자 → WebSocket 전송
- 오프라인 사용자 → FCM 푸시 알림
- 매칭 전 5회 메시지 제한 로직


**비동기 설계 이유**


채팅 메시지 저장과 알림 전송을 분리하여
응답 지연 감소 및 장애 내성 확보


---


### 음성 통화 시스템


- Agora RTC SDK 기반 1:1 통화
- 통화 요청 → 수락 → 거절 → 종료 상태 관리
- 통화 종료 후 채팅방 생성 옵션
- 패킷 손실률, 지터, RTT 등 품질 메트릭 수집


---


## 6. 성능 개선 및 설계 고민


### 1) RabbitMQ 도입


**문제**
- 채팅/알림 동기 처리로 응답 지연 발생


**해결**
- Producer/Consumer 구조 설계
- 메시지 큐 기반 비동기 처리
- 재시도 + 중복 방지 로직 구현


---


### 2) Redis 캐시 전략


- 매칭 후보 조회 결과 캐싱
- Cache Key: `matching:{userId}`
- TTL: 10분
- 조회 DB 부하 감소


---


## 7. 테스트 전략


```
src/test_unit/          # 단위 테스트 (5개)
src/test_integration/   # 통합 테스트 (11개)
```


Gradle Custom SourceSet으로 분리 관리


### 단위 테스트 (5개)


| 파일 | 대상 |
|------|------|
| `JwtTokenUtilTest` | JWT 토큰 생성/검증/만료 |
| `OAuthServiceTest` | OAuth2 인증 로직 |
| `MatchingAlgorithmServiceTest` | 매칭 스코어 6가지 계산 |
| `AgoraCallServiceTest` | 통화 상태 전환 로직 |
| `ChatMessageServiceTest` | 메시지 전송/제한 로직 |


### 통합 테스트 - API (5개)


| 파일 | 대상 |
|------|------|
| `UserControllerIntegrationTest` | 사용자 API |
| `MatchingControllerIntegrationTest` | 매칭 API |
| `CallQualityControllerIntegrationTest` | 통화 품질 API |
| `InputValidationTest` | 입력값 검증 (@Valid) |
| `JwtAuthenticationExceptionTest` | JWT 인증 예외 |


### 통합 테스트 - Infrastructure (5개)


| 파일 | 대상 |
|------|------|
| `AgoraCallServiceIntegrationTest` | 통화 전체 플로우 |
| `MatchingAlgorithmIntegrationTest` | 매칭 알고리즘 DB 연동 |
| `MatchingAlgorithmPerformanceTest` | 매칭 성능 (1000명 기준) |
| `ChatMessageQueueIntegrationTest` | RabbitMQ 메시지 큐 |
| `ConcurrencyTest` | 동시성 (100개 동시 메시지) |


### 통합 테스트 - Repository (1개)


| 파일 | 대상 |
|------|------|
| `UserRepositoryIntegrationTest` | 사용자 조회 쿼리 |


### 실행


```bash
# 단위 테스트
./gradlew unitTest

# 통합 테스트
./gradlew integrationTest

# 전체 테스트
./gradlew check
```


---


## 8. CI/CD


### 전체 파이프라인

```
┌─────────────────────────────────────────────────────────────────┐
│                        GitHub Actions                           │
├─────────────────────────────────────────────────────────────────┤
│  push / PR (main, develop)                                      │
│           ↓                                                     │
│  ┌─────────────────┐                                            │
│  │   Test Stage    │                                            │
│  │  ┌───────────┐  │                                            │
│  │  │ unitTest  │  │                                            │
│  │  └───────────┘  │                                            │
│  │  ┌─────────────────────────┐                                 │
│  │  │    integrationTest      │                                 │
│  │  │  (Testcontainers MySQL) │                                 │
│  │  │  (CI Service: Redis)    │                                 │
│  │  │  (CI Service: RabbitMQ) │                                 │
│  │  └─────────────────────────┘                                 │
│  └─────────────────┘                                            │
│           ↓ 테스트 통과 시                                         │
│  ┌─────────────────┐                                            │
│  │  Build Stage    │                                            │
│  │  - JAR 빌드     │                                             │
│  │  - 배포 번들 생성│                                              │
│  └─────────────────┘                                            │
│           ↓                                                     │
│  ┌─────────────────┐                                            │
│  │   AWS S3 업로드  │                                             │
│  └─────────────────┘                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      AWS CodeDeploy                             │
├─────────────────────────────────────────────────────────────────┤
│  S3에서 번들 다운로드 → EC2로 배포                                    │
│                                                                 │
│  배포 파일:                                                       │
│  - build/libs/*.jar                                             │
│  - Dockerfile                                                   │
│  - docker-compose.yml                                           │
│  - scripts/deploy.sh                                            │
│  - .env                                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        AWS EC2                                  │
├─────────────────────────────────────────────────────────────────┤
│  deploy.sh 실행                                                  │
│           ↓                                                     │
│  ┌─────────────────────────────────────┐                        │
│  │         Docker Compose              │                        │
│  │  ┌─────────────┐  ┌─────────────┐   │                        │
│  │  │   MariaDB   │  │ Spring Boot │   │                        │
│  │  │  Container  │  │  Container  │   │                        │
│  │  └─────────────┘  └─────────────┘   │                        │
│  └─────────────────────────────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```


### 현재 상태

- **활성화**: 테스트 자동화 (unitTest, integrationTest)
- **비활성화**: 빌드 & 배포 (AWS 인프라 구성 후 주석 해제하여 사용)


### 주요 파일

| 파일 | 역할 |
|------|------|
| `.github/workflows/gradle.yml` | CI/CD 파이프라인 정의 |
| `Dockerfile` | Spring Boot 앱 컨테이너 이미지 |
| `docker-compose.yml` | MariaDB + Spring Boot 컨테이너 구성 |
| `appspec.yml` | CodeDeploy 배포 설정 |
| `scripts/deploy.sh` | EC2에서 Docker Compose 실행 |


---


## 9. 트러블슈팅


### 1. WebSocket 세션 정리 문제


- 서버 재시작 시 세션 정리되지 않음
- `onCompletion` / `onTimeout` 처리 추가로 해결


### 2. 메시지 중복 처리 문제


- 비동기 재시도로 인한 중복 수신 발생
- 메시지 ID 기반 중복 검증 로직 구현


### 3. 조회 성능 저하


- N+1 문제 발생
- Fetch Join + QueryDSL 적용으로 해결


---


## 10. 실행 방법


### 1. 인프라 실행


```bash
docker run -d --name siso-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=test \
  -e MYSQL_DATABASE=siso_test \
  mysql:8.0

docker run -d --name siso-redis -p 6379:6379 redis:7-alpine

docker run -d --name siso-rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin123 \
  rabbitmq:3.12-management
```


### 2. 애플리케이션 실행


```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```


### 3. Swagger


```
http://localhost:8080/swagger-ui/index.html
```


---


## 프로젝트를 통해 얻은 경험


- 실시간 시스템 설계 경험
- JWT + OAuth2 인증 직접 구현
- RabbitMQ 기반 비동기 메시징 설계
- Redis 캐시 전략 설계
- N+1 문제 해결 및 쿼리 최적화
- Testcontainers 기반 통합 테스트 환경 구축
- CI/CD 자동화 경험


---


## 담당 영역


- 실시간 채팅 시스템 설계 및 구현
- 음성 통화 기능 구현
- 매칭 알고리즘 설계
- RabbitMQ 도입 및 비동기 구조 설계
- Redis 캐시 적용
- 테스트 코드 작성
- CI/CD 파이프라인 구축
