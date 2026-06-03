---
name: refactoring-catalog
description: "리팩터링 카탈로그(컴팩트). Martin Fowler 기반 코드 스멜→리팩터 매핑, SOLID 위반 판별, 복잡도·중복 측정 기준. refactorer가 초록 유지 개선 시, auditor가 '리팩터가 진짜 개선인지' 판정 시 참조. '리팩터링', '코드 스멜', 'SOLID', '복잡도', '중복'에 사용."
---

# Refactoring Catalog — 리팩터 카탈로그

초록을 유지하며 *동작 변경 없이* 구조만 개선. 새 동작·분기는 리팩터가 아니라 **새 RED 사이클**.

## 코드 스멜 → 처리
| 스멜 | 신호 | 리팩터 |
|------|------|--------|
| 중복 코드 | 같은 로직 2+곳 | Extract Method/Class, Pull Up |
| 긴 메서드 | 한 메서드 다책임 | Extract Method, Decompose Conditional |
| 빈혈 객체 | 로직이 절차 코드에만, 데이터 객체는 비어있음 | Move Method → 데이터 객체로 로직 이동 |
| 기능 산재 | 한 변경에 여러 클래스 수정 | Move Method/Field, Inline |
| 원시 집착 | 의미를 원시타입으로 | Replace Primitive with Value Object |
| 분기 복잡 | 깊은 if/switch | Replace Conditional with Polymorphism, Guard Clauses |

## SOLID 위반 판별 (빠른 체크)
- **S**: 클래스가 "그리고"로 설명되면 책임 분리. **O**: 새 케이스마다 기존 코드 수정 → 확장점 부재. **L**: 하위가 상위 계약 위반. **I**: 안 쓰는 메서드 강제 구현. **D**: 구현에 직접 의존(인터페이스+주입으로).

## 복잡도·중복 기준 (auditor 판정용)
- 순환 복잡도(메서드) > 10 → 분해 검토. 중복 블록 > 3회 → 추출. 메서드 길이 > ~30줄 → 검토.
- **개선 증거:** 리팩터 전/후 복잡도·중복이 *줄었는가*(단순 이동은 개선 아님 — auditor가 NITS).

## 원칙
과도한 추상화 금지(지금 코드가 요구하는 만큼만). 빨강 되면 즉시 revert. 동작 변경이 필요하면 "후속 RED 제안"으로 기록.
