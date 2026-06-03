---
name: test-design-patterns
description: "효과적인 테스트 설계를 위한 패턴, 경계값 분석, 동등 분할, 상태 전이 테스트 등 체계적 테스트 케이스 도출 방법론. '테스트 설계', '테스트 케이스 도출', '경계값 분석', '동등 분할', '상태 전이 테스트', '페어와이즈', '테스트 매트릭스' 등 테스트 설계 시 이 스킬을 사용한다. test-strategist와 unit-tester의 테스트 설계 역량을 강화한다. 단, 테스트 인프라 구축이나 CI/CD 설정은 이 스킬의 범위가 아니다."
---

# Test Design Patterns — 체계적 테스트 설계 가이드

테스트 케이스를 체계적으로 도출하고 효과적으로 구조화하는 방법론.

## 테스트 케이스 도출 기법

### 1. 동등 분할 (Equivalence Partitioning)

```
입력: 나이 (1~150 정수)
├── 유효 클래스: [1, 150] → 대표값: 25
├── 무효 클래스 1: < 1   → 대표값: 0, -1
├── 무효 클래스 2: > 150 → 대표값: 151, 999
├── 무효 클래스 3: 비정수 → 대표값: 25.5, "abc"
└── 무효 클래스 4: null  → 대표값: null, undefined
```

### 2. 경계값 분석 (Boundary Value Analysis)

```
범위: [1, 150]
테스트 값: 0, 1, 2, ... , 149, 150, 151
          ↑  ↑  ↑        ↑    ↑    ↑
         무효 경계 유효    유효 경계  무효

3점 경계값: min-1, min, max, max+1
7점 경계값: min-1, min, min+1, nominal, max-1, max, max+1
```

### 3. 상태 전이 테스트

```
주문 상태 전이:
CREATED ─(결제)→ PAID ─(배송시작)→ SHIPPING ─(배송완료)→ DELIVERED
   │                │                  │                    │
   └─(취소)→ CANCELLED  └─(환불)→ REFUNDED  └─(반품)→ RETURNED  └─(반품)→ RETURNED

테스트 케이스:
1. Happy Path: CREATED → PAID → SHIPPING → DELIVERED
2. 주문 취소: CREATED → CANCELLED
3. 결제 후 환불: CREATED → PAID → REFUNDED
4. 배송 중 반품: CREATED → PAID → SHIPPING → RETURNED
5. 무효 전이: CANCELLED → PAID (거부되어야 함)
6. 무효 전이: DELIVERED → SHIPPING (거부되어야 함)
```

### 4. 페어와이즈 테스트 (Pairwise Testing)

```
파라미터:
- OS: Windows, macOS, Linux
- 브라우저: Chrome, Firefox, Safari
- 언어: ko, en, ja

전수 조합: 3×3×3 = 27개
페어와이즈: 9개로 축소 (모든 2-way 조합 커버)

| # | OS      | 브라우저 | 언어 |
|---|---------|---------|------|
| 1 | Windows | Chrome  | ko   |
| 2 | Windows | Firefox | en   |
| 3 | Windows | Safari  | ja   |
| 4 | macOS   | Chrome  | en   |
| 5 | macOS   | Firefox | ja   |
| 6 | macOS   | Safari  | ko   |
| 7 | Linux   | Chrome  | ja   |
| 8 | Linux   | Firefox | ko   |
| 9 | Linux   | Safari  | en   |
```

### 5. 결정 테이블 (Decision Table)

```
할인 규칙:
조건                     | R1 | R2 | R3 | R4 |
─────────────────────────┼────┼────┼────┼────┤
VIP 회원인가?            | Y  | Y  | N  | N  |
주문금액 ≥ 50,000원?     | Y  | N  | Y  | N  |
─────────────────────────┼────┼────┼────┼────┤
15% 할인                 | X  |    |    |    |
10% 할인                 |    | X  | X  |    |
할인 없음                |    |    |    | X  |
```

## 성공·실패 케이스 분류 (모든 기능에서 이 체크리스트를 훑는다)

> **정의 — 성공 케이스 = 시스템이 *수행*해야 하는 것 / 실패 케이스 = 시스템이 *거부*해야 하는 것.** 둘 다 동등하게 중요하다. 신입이 가장 흔히 빠뜨리는 건 *실패 케이스, 특히 권한*이다. 각 항목을 "있음/없음(사유)"로 판정해 빠뜨림을 막는다.

| 분류 | 성공/실패 | 무엇을 검증 | 기대(예) |
|------|:---:|------|------|
| 정상(happy) | 성공 | 대표 유효 입력 → 정상 동작 | 201 / 정상 결과 |
| 경계 | 성공·실패 | 유효/무효 경계값 양쪽(깊이 N vs N+1) | 경계 안=성공, 밖=거부 |
| 잘못된 입력(검증) | 실패 | 형식·필수·범위 위반 | 400 + `{code,message,errors[]}` |
| **권한·소유권** | 실패 | *남의/없는* 리소스 접근 | **404 통일** |
| 상태 위반 | 실패 | 불가능한 상태 전이 | 409/규약 |
| 충돌(유니크/중복) | 실패 | 활성 유니크 위반·중복 생성 | 409/규약 |
| 불변식·부수효과 | 성공 | soft delete 후 조회 제외, 멱등성, 트랜잭션 원자성 | 규약대로 |
| 동시성(고위험) | 성공·실패 | 동시 요청(find-or-create·활성 유니크) | 중복 없음 |

> 권한·상태·충돌·동시성은 `project-conventions`·`risk-profile`이 프로젝트별로 무엇이 해당하는지 정한다. 목표 동작은 위 *실패* 케이스를 반드시 포함한다.

## 테스트 네이밍 규칙

> **원칙: 테스트 이름 = 실행 가능한 명세.** 읽기만 해도 *무슨 동작이 / 어떤 조건에서 / 무엇을 기대하는지* 드러나야 한다. **1 테스트 = 1 동작.** 성공/실패가 이름에 보여야 한다.

- **구성요소:** `[대상·동작] + [조건] + [기대]`.
- **기본 패턴(프로젝트 미지정 시):** `should_<기대>_when_<조건>` 또는 `<동작>_<조건>_<기대>`.
- **프로젝트별 패턴은 `project-conventions`에서** 지정(예: 한글 서술형 / BDD). 그 패턴을 따른다.

```
나쁜 예: test_create1(), 생성테스트(), testThing()
좋은 예(영문): should_reject_when_accessing_others_resource()
좋은 예(한글 서술형): 남의_리소스에_접근하면_거부된다()
좋은 예(한글 서술형): 한계값을_초과하면_생성이_거부된다()
```

## 테스트 구조 패턴

### AAA (Arrange-Act-Assert)
```python
def test_order_total_with_discount():
    # Arrange
    order = Order(items=[Item("A", 10000), Item("B", 20000)])
    discount = PercentageDiscount(10)

    # Act
    total = order.calculate_total(discount)

    # Assert
    assert total == 27000
```

### Given-When-Then (BDD)
```python
def test_vip_customer_gets_extra_discount():
    # Given: VIP 고객이 5만원 이상 주문했을 때
    customer = Customer(tier="VIP")
    order = Order(customer, total=60000)

    # When: 할인을 적용하면
    discounted = pricing_service.apply_discount(order)

    # Then: 15% 할인이 적용된다
    assert discounted == 51000
```

### Builder 패턴 (테스트 데이터)
```python
class OrderBuilder:
    def __init__(self):
        self._customer = default_customer()
        self._items = []
    def with_vip_customer(self): ...
    def with_item(self, name, price): ...
    def build(self) -> Order: ...

# 사용
order = OrderBuilder().with_vip_customer().with_item("A", 10000).build()
```

## 테스트 피라미드 비율 가이드

```
        ╱ E2E ╲          5~10%   느리지만 현실적
       ╱───────╲
      ╱ 통합    ╲        20~30%  서비스 간 연동
     ╱───────────╲
    ╱  단위 테스트  ╲      60~70%  빠르고 격리됨
   ╱───────────────╲
```

| 레이어 | 실행 시간 | 범위 | 실패 원인 |
|--------|----------|------|----------|
| 단위 | < 10ms | 함수/클래스 | 로직 오류 |
| 통합 | < 5s | 모듈 간 | 인터페이스 불일치 |
| E2E | < 60s | 전체 흐름 | 환경, 타이밍 |

## 리스크 기반 테스트 우선순위

```
리스크 = 장애 확률 × 비즈니스 영향

       높은 영향
          │
  P1 테스트 │ P0 테스트
  (반드시)  │ (최우선)
  ─────────┼─────────→ 높은 확률
  P3 테스트 │ P2 테스트
  (시간 있으면)│ (권장)
          │
       낮은 영향
```
