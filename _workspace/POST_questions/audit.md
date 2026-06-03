# 사후 감사 (tdd-auditor) — POST /api/v1/questions

> 구현자와 독립된 감사자가 git 로그·소스·게이트 로그·테스트 실행을 직접 교차검증.

## 판정: PASS (NITS)

| 항목 | 결과 | 근거 |
|------|:---:|------|
| RED 정직성 | ✓ | 3 게이트 로그 모두 컴파일 성공 후 *assertion* 단계 실패(컴파일/런타임 예외 0). domain :39/52/60/69/80/93, application :63/77/93/127, interface :32/78 |
| 커밋 순서 test<impl | ✓ | 3증분 R→G→R 별도 커밋. RED 커밋만 테스트 추가, feat은 impl만. 테스트 파일은 자신의 RED 커밋 1개에만 등장(동결 무결성) |
| 3계층 + 목표동작 존재 | ✓ | 단위7 · 통합4 · E2E4 · 목표동작9 = 24 테스트 전부 green |
| 컨벤션 준수 | ✓ | QuestionService에 @Transactional 없음(Facade만), 컨트롤러 Entity 미노출, DTO 체인 4단, domain 순수(cross-domain import 0) |
| 전체 테스트 | ✓ | ./gradlew clean test → BUILD SUCCESSFUL, 73 tests 0 failures |
| 약한 단언/취약 테스트 | ✓ | errorCode·경계값(2001)·null 기본값·실제 DB 저장·동시성 불변식까지 단언 |

## NITS (참고 — 판정 무영향)
- 🟢 게이트4(ArchUnit 객관 룰)·게이트8(뮤테이션) 생략(`arch_cmd`/`mutation_cmd` 미설정) → 컨벤션 회귀 방지는 사람 리뷰 의존.
- 🟢 domain 브랜치 커버리지 83.3%: 미커버 분기 = `content == null` 가드(테스트는 ""/공백/2001자로 검증). base 바닥(70) 충족.
- 🟢 QuestionE2ETest(4)와 QuestionTargetBehaviorE2ETest(9)가 201/400/401/404에서 일부 중복 — 의도된 설계(루프 자체 테스트 vs 동결 블랙박스)지만 실행시간 중복.
