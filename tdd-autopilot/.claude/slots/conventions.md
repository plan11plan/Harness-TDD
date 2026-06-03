# 컨벤션 슬롯 (프로젝트 제공) — 면훈소 예시

> 프로젝트가 제공하는 **원본 컨벤션 입력**. 조립기가 이걸 읽어 ① 에이전트가 `project-conventions` 어댑터로 참조하는 가이드와 ② `arch_tool` 룰(객관 강제)로 컴파일한다. 보편 엔진엔 고정 컨벤션이 없다 — 프로젝트마다 이 슬롯만 갈아끼운다.
> (면훈소 상세 설계: `/.claude/docs/`의 02.아키텍처·03.도메인-모델링·04.ERD·05.API-설계·06.DB-설계.)

## 1. 아키텍처·레이어
- 레이어드: `interface → application → domain ← infrastructure`. domain은 순수(아무 데도 의존 안 함), infrastructure가 domain port를 구현(의존성 역전).
- application = `{도메인}Facade`: 오케스트레이션 + **트랜잭션 경계**. 도메인 간 조합은 오직 Facade에서.
- domain = `{도메인}Service`: 자기 도메인 규칙만(자기 Repository·Entity만 참조, 도메인 서비스끼리 직접 의존 금지).
- 금지: `@Transactional`을 Service·Controller에 / Entity를 interface 밖 노출 / 빈혈 서비스(불변식은 Entity가 책임).

## 2. 테스트 레벨 ↔ 위치 매핑
| 레벨 | 계층 | 위치 패턴 |
|------|------|----------|
| 단위 | domain | `*.domain.*Test` |
| 통합 | application | `*.application.*FacadeTest`, `*IntegrationTest` |
| E2E | interface | `*.interfaces.*E2ETest` |

## 3. 테스트 더블·환경 규약 (레벨별)
- 단위(domain): fake/mock repository로 순수 로직 검증.
- 통합(application): `@SpringBootTest` + Testcontainers MySQL, 테스트 `@Transactional` 금지.
- E2E(interface): `@SpringBootTest(RANDOM_PORT)` + 실제 HTTP + 인증 헤더.

## 4. 네이밍 패턴
- **한글 서술형**: `<조건>_<동작>_<기대>()` — 예: `남의_카테고리에_질문_생성_시_404를_반환한다()`, `깊이_2를_초과하면_거부된다()`. 1 테스트 1 동작.

## 5. DB·API 규약
- soft delete(`deleted_at` + 생성 컬럼 기반 활성 유니크), 물리 삭제 금지.
- 없는/남의 리소스 = **404 통일**. 에러 형식 `{code,message,errors[]}`. 시간 KST ISO-8601(`+09:00`). offset 페이징(`page,size,sort`). DTO 체인 `Request→Command→Result→Response`.

## 6. 객관 룰 (arch_tool로 기계 강제 = ★ — *있을 때만*)
> ★ 항목이 하나도 없으면 `arch_tool`/ArchUnit을 두지 않는다 → 게이트 4 생략, 컨벤션은 Auditor가 주관 검사. (이 프로젝트는 ★가 있으므로 ArchUnit 생성.)
- ★ 레이어 의존 방향 / ★ @Transactional은 Facade에만 / ★ Entity 미노출 / ★ DTO 체인.
- (주관 — 감사자 판정: 빈혈 서비스 금지, Facade 조합 적정성.)
