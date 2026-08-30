# SISO (시니어 특화 선(先) 음성 매칭 플랫폼)

5060 시니어 유저의 디지털 접근성을 극대화하고, 신뢰 기반의 음성 매칭을 제공하는 백엔드 시스템

## 1. 프로젝트 개요

### 1-1. 기획 배경 및 프로젝트 진화

- **Phase 1. 팀 프로젝트 (MVP 구축):** 시니어 세대의 텍스트 입력 부담과 로맨스 스캠 위험을 해결하기 위해 '카카오 간편 로그인'과 '8분 음성 통화' 중심의 매칭 서비스를 구축했습니다. 핵심 인증, 실시간 STOMP 채팅, CI/CD 파이프라인을 전담하여 안정적인 MVP 서비스를 출시했습니다.
- **전환 계기 (The Turning Point):** 서비스 구축 후 테스트 및 운영 과정에서 동기 처리로 인한 외부 API 블로킹(FCM), 매칭 연산 시 과도한 CPU 점유(80% 이상), H2/MySQL 환경 차이로 인한 테스트 한계 등 3가지 구조적 병목을 발견했습니다.
- **Phase 2. 개인 고도화 (시스템 아키텍처 재설계):** 체감한 기술적 한계를 극복하고자 RabbitMQ 메시지 브로커, Redis Caching, Testcontainers 기반 통합 테스트 환경을 도입하여 시스템 응답성과 확장성을 대폭 강화했습니다.

### 1-2. 프로젝트 목표

- 무상태(Stateless) JWT 기반의 실시간 STOMP/WebSocket 보안 체계 확립
- 외부 API 장애 영향도를 격리하는 메시지 큐 기반의 비동기 아키텍처 전환
- 반복적인 매칭 연산 및 DB I/O 부하 최소화
- 운영 환경과 100% 동일한 통합 테스트 환경 구축을 통한 배포 안정성 확보

### 1-3. 기술 스택

- **Core:** Java 17, Spring Boot 3.x, Spring Data JPA, QueryDSL
- **Security & Auth:** Spring Security, JWT, Kakao OAuth2
- **Real-time & Messaging:** WebSocket (STOMP), RabbitMQ, Agora RTC, Firebase FCM
- **Database & Cache:** MySQL 8.0, Redis
- **DevOps:** Docker, GitHub Actions, AWS CodeDeploy, AWS EC2, AWS S3
- **Testing:** JUnit5, Mockito, AssertJ, Testcontainers
- **Docs**: Swagger

### 1-4. 시스템 아키텍처 및 CI/CD 파이프라인

**[시스템 아키텍처]**

```text
  [ Users & Clients ]
         │ (REST API / OAuth2 / STOMP / Agora RTC)
         ▼
    [ AWS EC2 ]
   ┌────────────────────────────────────────────────────────┐
   │  Spring Boot Backend Server (Docker Container)         │
   │  - Auth / Core API / Realtime Chat / Matching Logic    │
   └──────┬──────────────────┬────────────────────┬─────────┘
          │                  │                    │
          ▼                  ▼                    ▼
     [ MySQL 8 ]       [ Redis Cache ]       [ RabbitMQ ]
   (Data Storage)      (Matching Cache)    (Message Broker)
                                                  │
                                                  ▼
                                     [ FCM Push & Matching Service ]

```

**[CI/CD 배포 파이프라인]**

```text
GitHub Push (main/develop)
  └─► GitHub Actions
       ├─► JDK Setup & Testcontainers 통합 테스트 실행
       ├─► Gradle Build (bootJar) & Docker Image 빌드
       └─► AWS S3 업로드 ──► AWS CodeDeploy ──► EC2 (Docker Compose 자동 재배포)

```

## 2. 도메인 및 구조 설계

### 2-1. 패키지 및 프로젝트 구조

```text
com.siso
├── common            # Security, WebSocket, RabbitMQ, Redis 설정 및 Global Error, Util
└── domain
    ├── user          # 시니어 프로필, OAuth2 (Kakao/Apple) 및 사용자 인증 도메인
    ├── matching      # 라이프스타일 기반 매칭 연산, Redis 캐싱 및 RabbitMQ 비동기 처리
    ├── chat          # STOMP 메시징 및 RabbitMQ Consumer (concurrency="3-10")
    ├── call          # Agora RTC 기반 음성 통화 도메인
    ├── callreview    # 통화 종료 후 매칭 평가 및 리뷰 도메인
    ├── notification  # FCM 푸시 알림 비동기 이벤트 처리
    ├── image         # 프로필 및 통화 관련 이미지 업로드/관리
    ├── report        # 부적절 유저 신고 도메인
    └── voicesample   # 시니어 음성 샘플 등록 및 관리

```

## 3. 핵심 기능 및 담당 도메인

### 3-1. 핵심 기능 요약

- **시니어 간편 인증:** 카카오 OAuth2 + JWT 기반 무상태 인증
- **선(先) 음성 통화 (Agora RTC):** 프로필 사진 공개 전 8분간 음성 통화로 상대방 사전 검증
- **라이프스타일 매칭:** 유저 관심사/활동 지역 기반 맞춤형 추천 및 비동기 파이프라인 연산
- **실시간 STOMP 채팅 & 알림:** 소켓 연결 기반 1:1 채팅 및 FCM 푸시 알림

### 3-2. 핵심 비즈니스 로직

| 구분                    | 비즈니스 규칙 및 지표                                        | 처리 방식                                                    |
| ----------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **인증/보안**           | HTTP Handshake 이후 WebSocket 세션 보안 유실 방지            | STOMP CONNECT 프레임 내 JWT 직접 검증 및 SecurityContext 수동 주입 |
| **매칭 캐싱**           | 반복 조회 시 CPU 점유율 대폭 상승 방지                       | matching:{userId} 키로 Redis Caching, TTL 10분 적용으로 정합성 유지 |
| **채팅 및 매칭 메시징** | DB 저장, WebSocket 전송, FCM 푸시, 매칭 연산의 동기 처리 병목 해소 | RabbitMQ Exchange/Queue 분리 및 Consumer 병렬 처리 (concurrency="3-10") |

### 3-3. 담당 영역 및 역할

| 구분        | 기간 및 형태                    | 역할 및 주요 담당 도메인                              |
| ----------- | ------------------------------- | ----------------------------------------------------- |
| **Phase 1** | 2025.08 ~ 2025.09 (팀 10명)     | **Backend Developer (인증, 실시간 통신, CI/CD 전담)** |
| **Phase 2** | 2026.10 ~ 2026.01 (개인 고도화) | **시스템 아키텍처 재설계 및 성능 최적화**             |

**Phase 1 주요 담당**

- Kakao OAuth2 + JWT 인증 체계 수립
- STOMP WebSocket 및 Agora RTC 음성 통화 연동
- GitHub Actions + Docker + CodeDeploy CI/CD 파이프라인 구축

**Phase 2 주요 담당**

- QueryDSL Fetch Join 적용으로 유저 필터 검색 N+1 문제 해결
- 매칭 연산 Redis 캐싱 및 RabbitMQ 비동기 워커 도입
- RabbitMQ 비동기 메시징 기반 FCM 장애 격리
- Testcontainers 도입으로 테스트 환경 완전 격리

## 4. 엔지니어링 문제 해결 및 회고

■ **[Phase 전환 배경] 팀 프로젝트 완료 후 인식한 구조적 한계와 고도화 명분**

- **외부 API 동기 블로킹:** 채팅 메시지 저장과 FCM 발송이 메인 트랜잭션에 동기로 묶여 외부 FCM 장애 발생 시 전체 응답이 대기되는 문제 발생.
- **과도한 CPU 점유:** complex 매칭 연산이 매 요청마다 DB를 조회하며 반복 실행되어 서버 CPU 점유율이 80% 이상 상승.
- **환경 불일치 리스크:** 로컬 H2 DB와 운영 MySQL DB의 함수/인덱스 차이로 배포 전 통합 테스트의 신뢰도 저하.

### 4-1. 성능 개선 및 구조 최적화

| 개선 항목                                      | 개선 전                                     | 개선 후                                           | 개선 효과                                       |
| ---------------------------------------------- | ------------------------------------------- | ------------------------------------------------- | ----------------------------------------------- |
| **JPA N+1 쿼리 최적화** *(유저 필터 검색 API)* | 연관 엔티티 지연 로딩으로 41회 DB 조회 발생 | QueryDSL fetchJoin() 단일 쿼리 즉시 로딩        | **450ms → 32ms** (응답 속도 97% 개선)           |
| **매칭 알고리즘 캐싱 & 비동기화**              | 매 요청마다 DB I/O 및 매칭 연산 재수행      | RabbitMQ 비동기 처리 + Redis 캐싱 (TTL 10분) 적용 | **3,500ms → 8ms** (437배 향상), DB I/O 65% 절감 |
| **채팅/알림 비동기 전환**                      | 채팅 저장 → FCM 푸시 → 소켓 전송 동기 처리  | RabbitMQ 메시지 브로커 기반 비동기 분리           | **2,100ms → 45ms** (46배 향상), FCM 장애 격리   |

### 4-2. Phase 1 트러블슈팅 (MVP 팀 프로젝트 단계)

- **Stateless JWT 환경의 WebSocket(STOMP) 세션 인증 유실 및 NPE 결함 해결** 
  - **문제 상황**: 실시간 1:1 채팅 및 음성 통화 연결 구축 당시, 최초 HTTP Handshake 단계를 통과했음에도 불구하고 WebSocket(STOMP) 세션이 유지되는 동안 Spring Security의 인증 정보(SecurityContext)가 메시지 핸들러까지 자동으로 전파되지 않는 현상이 발생했습니다. 이로 인해 유저 식별이 불가능해져 메시지 전송이 차단되었으며, 세션 속성 Map에 접근하는 과정에서 null을 참조하여 NPE(NullPointerException)가 발생하는 결함이 확인되었습니다.
  - **원인 분석**: HTTP 요청마다 독립적으로 인증하는 무상태(Stateless) 프로토콜인 JWT와, 한 번 연결을 맺으면 지속해서 세션을 유지하는 상태유지(Stateful) 프로토콜인 WebSocket 간의 파이프라인 차이 때문에 발생한 문제였습니다. Spring Security의 기본 필터 체인이 STOMP 메시지 프레임 단위까지는 인증 객체를 자동으로 유지해주지 못했습니다.
  - **해결 방법**: JwtChannelInterceptor를 직접 구현하여 STOMP의 CONNECT 프레임 헤더에 포함된 JWT 토큰을 수동으로 직접 검증하도록 개선했습니다. 검증을 통과한 인증 객체는 WebSocket 전용 SecurityContext에 수동으로 주입(Injection)했습니다. 또한, STOMP 프레임 초기화 시점의 null 참조 이슈를 방지하기 위해 accessor.getSessionAttributes()의 반환값을 검증하고, null인 경우 동락성(Thread-safe)이 보장되는 ConcurrentHashMap으로 수동 초기화해주는 세션 안정화 코드를 구축했습니다.
  - **최종 결과**: 무상태 인증 체계의 보안성을 유지하면서도 실시간 소켓 통신에서 사용자 식별 오류를 완전히 제거하여 보안 정합성과 세션 안정성을 확보했습니다.

### 4-3. Phase 2 성능 최적화 (개인 리팩토링 및 고도화 단계)

- **QueryDSL** **fetchJoin()** **도입을 통한 JPA N+1 쿼리 최적화** 
  - **도입 배경**: 유저 목록 및 맞춤 필터 검색 API를 개발한 후 연관 엔티티(프로필, 관심사, 지역 등)를 함께 불러오는 과정에서 쿼리 실행 횟수가 폭증하는 현상을 체감했습니다. AI 도구를 활용해 코드 및 SQL 실행 흐름을 분석하는 과정에서, 지연 로딩(Lazy Loading)으로 인해 N+1 문제가 발생하고 있음을 정확히 인지했습니다.
  - **개선 내용**: JPA 기본 조회 방식 대신 QueryDSL을 도입하고, 연관된 엔티티들을 단 1회의 쿼리로 한 번에 묶어서 끌어오는 fetchJoin() 기법을 적용했습니다.
  - **성능 성과**: 메인 목록 조회 시 기존 41회에 달하던 반복 DB 조회를 단 **1회**로 줄여, API 응답 속도를 **450ms에서 32ms로 약 97% 대폭 개선**했습니다.
- **AI 가중치 매칭 연산의 비동기 파이프라인 전환 및 Redis Caching 전략** 
  - **도입 배경**: 기존 MVP의 단순 무작위 매칭 방식을 개선하고자 관심사(30%), 나이(20%), MBTI(15%), 지역(15%), 활동성(10%), 생활습관(10%) 등 6가지 다차원 지표를 계산하는 '가중치 추천 알고리즘'으로 고도화했습니다. 그러나 유저가 매칭을 요청할 때마다 이 복잡한 가중치 수식을 매번 동기식으로 재연산하다 보니 화면 버벅임과 응답 지연 현상이 발생했습니다.
  - **기술 선택 명분**: 연산 부하를 스레드 풀 밖으로 이관하기 위해 메시지 큐 도입을 결정했습니다. 대규모 분산 스트리밍에 특화된 Kafka는 1인 고도화 환경에서 클러스터 구축 및 운영 공수가 과도하다고 판단하여, 메시지 Routing 기능이 우수하고 단독 운영이 가벼운 **RabbitMQ**를 메시지 브로커로 선택했습니다.
  - **개선 내용**: 매칭 연산 프로세스를 RabbitMQ 비동기 워커(Worker)로 넘겨 메인 요청-응답 스레드의 블로킹을 해소했습니다. 또한, 연산이 완료된 결과는 matching:{userId} 키 구조를 적용해 Redis에 캐싱(TTL 10분 설정)하여 10분 이내 재조회 요청 시 DB I/O와 CPU 연산을 완전히 스킵하도록 구조화했습니다.
  - **성능 성과**: 반복적인 매칭 요청 조회 응답 속도를 **3,500ms에서 8ms로 약 437배 향상**시켰으며, DB I/O 부하를 **65% 절감**했습니다.
- **실시간 채팅 및 외부 API(FCM) 파이프라인 비동기 전환을 통한 장애 격리** 
  - **도입 배경**: 기존에는 채팅 메시지 DB 저장, 소켓 전송, 외부 FCM 푸시 알림 발송이 하나의 동기 트랜잭션으로 묶여 있었습니다. 이로 인해 외부 FCM 서버에 네트워크 지연이나 장애가 발생하면 전체 채팅 메시지 응답까지 대기 상태에 빠지는 구조적 가용성 한계를 경험했습니다.
  - **개선 내용**: RabbitMQ를 활용해 외부 푸시 알림 이벤트를 비동기로 완전 분리했습니다. 채팅 Consumer에 병렬 처리 옵션(concurrency="3-10")을 부여하고 메시지 재시도(Retry) 및 중복 방지(Idempotency) 로직을 탑재했습니다.
  - **성능 성과**: FCM 푸시 알림 발송이 외부 API 장애로 타격을 입더라도 핵심 채팅 메시지 전송은 즉시 완료되는 장애 격리(Fault Isolation)를 이뤄냈으며, 채팅 응답 속도를 **2,100ms에서 45ms로 46배 향상**시켰습니다.

### 4-4. Phase 2 기술적 도전 (아키텍처 정합성 설계)

- **RabbitMQ** **MatchingConsumer** **트랜잭션 경계 분리 및 정합성 순서 강제** 
  - **문제 상황**: 비동기 매칭 워커(MatchingConsumer) 연산이 끝난 후, RDB에 매칭 상태를 COMPLETED로 업데이트하는 시점과 Redis 캐시 데이터를 생성하는 시점 간에 실행 순서가 꼬이는 레이스 조건(Race Condition) 위험을 발견했습니다. 캐시는 정상 생성되었으나 DB 트랜잭션이 최종 롤백되거나, DB 반영 전에 조회가 들어와 데이터가 일치하지 않을 수 있었습니다.
  - **해결 방법**: 트랜잭션 경계를 명확히 설정했습니다. RDB의 데이터 수정 트랜잭션이 완전히 Commit된 것이 확인되는 시점(After-Commit 이벤트)에만 Redis 캐시를 생성/갱신하도록 event-driven 형태의 처리 순서를 명시적으로 고정했습니다.
  - **최종 결과**: 비동기 메시지 기반 아키텍처 환경에서도 RDB와 In-Memory 캐시 간의 데이터 불일치 가능성을 차단하고 시스템 신뢰성을 보장했습니다.

### 4-5. 시스템 한계 인지 및 확장성

- **Agora RTC 토큰 만료 정책 파이프라인** 
  - **현상 및 인지**: Agora RTC 기반 음성 통화 구현 시, 토큰 TTL과 통화 세션 유지 간의 관계를 검토했습니다. 프로젝트 스펙이 '8분 선(先) 음성 통화'라는 명확한 제한을 두고 있었으므로, MVP 구현 시에는 토큰 TTL을 1시간(3,600초)으로 부여하여 단선 위험을 최소화하는 단일 발급 구조로 안전하게 처리했습니다.
  - **향후 확장 설계**: 추후 1시간 이상의 지속적인 채팅/통화 서비스로 승급·확장될 경우를 대비해, Agora SDK의 onTokenPrivilegeWillExpire 만료 예고 콜백 이벤트를 활용하여 클라이언트가 백엔드로 토큰 재발급(Refresh)을 요청하고 renewToken()으로 세션을 Seamless하게 연장하는 기술 구조를 미리 파악 및 설계해 두었습니다.
- **findAll()** **메모리 스트림 필터링의 병목 가능성 인지** 
  - **현상 및 인지**: 매칭 후보군을 추출하는 알고리즘 일부 메서드에서 userRepository.findAll()로 전체 데이터를 애플리케이션 메모리로 끌어온 뒤 Java Stream 필터로 가공하고 있는 지점을 발견했습니다. 현재 유저 수에서는 동작하지만, 향후 유저 수가 증가할 경우 서버 메모리 고갈 및 CPU I/O 폭증을 일으킬 수 있는 명확한 병목 포인트임을 스스로 인지했습니다.
  - **개선 방향성**: 앞서 QueryDSL fetchJoin()으로 N+1 문제를 해결했던 노하우를 바탕으로, 해당 메모리 필터링 로직을 QueryDSL 조건절 기반의 커스텀 DB 쿼리(findMatchingCandidatesWithRelations)로 완전히 교체하여 DB 레벨에서 페이징 및 필터링이 이루어지도록 개편하는 것을 차기 기술 과제로 등록해 두었습니다.

## 5. 테스트 전략

- **Testcontainers 기반 통합 테스트:** 로컬 H2 환경과의 불일치 문제를 해결하기 위해 실제 운영 환경과 동일한 MySQL 8.0 Docker 컨테이너를 테스트 수행 시 동적 구동(SharedMySQLContainer).
- **독립적 테스트 수행:** @ActiveProfiles("test") 환경에서 컨테이너 생성/소멸을 자동화하여 환경에 독립적이고 신뢰도 높은 통합 테스트 구동.

## 6. 실행 방법 (Local Run)

```bash
# 1. Repository Clone
$ git clone <https://github.com/HyunRe/SISO.git>
$ cd SISO

# 2. Infra Containers Run (MySQL, Redis, RabbitMQ)
$ docker-compose up -d

# 3. Application Run
$ ./gradlew bootRun

```
