package jobterview.server.question.application;

import jobterview.server.category.domain.CategoryService;
import jobterview.server.common.error.ApiException;
import jobterview.server.common.error.ErrorCode;
import jobterview.server.question.application.dto.QuestionCommands;
import jobterview.server.question.application.dto.QuestionDetailResult;
import jobterview.server.question.application.dto.QuestionRefs.TagRefResult;
import jobterview.server.question.domain.Visibility;
import jobterview.server.question.infrastructure.QuestionJpaRepository;
import jobterview.server.support.IntegrationTest;
import jobterview.server.tag.infrastructure.TagJpaRepository;
import jobterview.server.user.application.AuthFacade;
import jobterview.server.user.application.dto.SignupCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QuestionFacade 통합 테스트 — Testcontainers MySQL. 카테고리 소유권 검증 + 태그 find-or-create 조합 +
 * 트랜잭션 + 고위험 동시성(태그 중복 방지). 테스트에 @Transactional 미부착.
 */
class QuestionFacadeIntegrationTest extends IntegrationTest {

    @Autowired
    QuestionFacade questionFacade;
    @Autowired
    AuthFacade authFacade;
    @Autowired
    CategoryService categoryService;
    @Autowired
    QuestionJpaRepository questionJpaRepository;
    @Autowired
    TagJpaRepository tagJpaRepository;

    private Long signup(String email) {
        return authFacade.signup(new SignupCommand(email, "password123", "지민")).userId();
    }

    private Long category(Long owner, String name) {
        return categoryService.create(owner, name, null).getId();
    }

    @Test
    @DisplayName("질문을 생성하면 DB에 저장되고 상세를 반환한다")
    void 질문을_생성하면_저장되고_상세를_반환한다() {
        Long owner = signup("a@b.com");
        Long catId = category(owner, "백엔드");

        QuestionDetailResult r = questionFacade.create(new QuestionCommands.Create(
                owner, null, "프로세스와 스레드 차이는?", null, catId, List.of(), null));

        assertThat(r.id()).isNotNull();
        assertThat(r.content()).isEqualTo("프로세스와 스레드 차이는?");
        assertThat(r.category().id()).isEqualTo(catId);
        assertThat(r.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(questionJpaRepository.findById(r.id())).isPresent();
    }

    @Test
    @DisplayName("남의 카테고리로 생성하면 CATEGORY_NOT_FOUND")
    void 남의_카테고리로_생성하면_404를_반환한다() {
        Long ownerA = signup("a@b.com");
        Long catId = category(ownerA, "OS");
        Long ownerB = signup("b@b.com");

        assertThatThrownBy(() -> questionFacade.create(new QuestionCommands.Create(
                ownerB, null, "침입", null, catId, List.of(), null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("tagNames를 주면 find-or-create 되어 연결된다(중복 이름은 1개)")
    void tagNames를_주면_findOrCreate되어_연결된다() {
        Long owner = signup("a@b.com");
        Long catId = category(owner, "백엔드");

        QuestionDetailResult r = questionFacade.create(new QuestionCommands.Create(
                owner, null, "태그 질문", null, catId, List.of("동시성", "CS", "동시성"), null));

        assertThat(r.tags()).extracting(TagRefResult::name).containsExactlyInAnyOrder("동시성", "CS");
        assertThat(tagJpaRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("동시에 같은 태그로 생성해도 활성 태그는 하나만 생성된다(고위험 동시성)")
    void 동시에_같은_태그로_생성해도_태그는_하나만_생성된다() throws InterruptedException {
        Long owner = signup("a@b.com");
        Long catId = category(owner, "백엔드");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    questionFacade.create(new QuestionCommands.Create(
                            owner, null, "동시성 질문", null, catId, List.of("동시성"), null));
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 활성 유니크 경합에서 진 스레드는 실패 가능 — 중복 태그는 DB가 차단(불변식)
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(tagJpaRepository.count()).isEqualTo(1L); // 활성 유니크 → 중복 활성 태그 없음
        assertThat(success.get()).isGreaterThanOrEqualTo(1);
    }
}
