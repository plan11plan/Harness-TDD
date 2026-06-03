package jobterview.server.question.domain;

import jobterview.server.common.error.ApiException;
import jobterview.server.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * QuestionService 도메인 단위 테스트 — 생성 규칙(내용 불변식·부모 검증·기본 공개범위). fake/mock repository.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionService 단위 — 질문 생성 규칙")
class QuestionServiceTest {

    @Mock
    QuestionRepository questionRepository;
    @InjectMocks
    QuestionService questionService;

    @Test
    @DisplayName("루트 질문을 생성하면 내용·카테고리가 담겨 저장된다")
    void 루트_질문을_생성하면_저장된다() {
        given(questionRepository.save(any(Question.class))).willAnswer(inv -> inv.getArgument(0));

        Question q = questionService.create(1L, null, 10L, "프로세스와 스레드 차이는?", null, Visibility.PRIVATE);

        assertThat(q.isRoot()).isTrue();
        assertThat(q.getCategoryId()).isEqualTo(10L);
        assertThat(q.getContent()).isEqualTo("프로세스와 스레드 차이는?");
    }

    @Test
    @DisplayName("부모가 있으면 꼬리질문으로 저장된다")
    void 부모가_있으면_꼬리질문으로_저장된다() {
        Question parent = Question.create(1L, null, 10L, "부모", null, Visibility.PRIVATE);
        given(questionRepository.findActiveByIdAndOwnerId(5L, 1L)).willReturn(Optional.of(parent));
        given(questionRepository.save(any(Question.class))).willAnswer(inv -> inv.getArgument(0));

        Question q = questionService.create(1L, 5L, 10L, "꼬리질문", null, Visibility.PRIVATE);

        assertThat(q.getParentId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("존재하지 않는 부모면 PARENT_NOT_FOUND")
    void 존재하지_않는_부모면_PARENT_NOT_FOUND() {
        given(questionRepository.findActiveByIdAndOwnerId(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.create(1L, 99L, 10L, "꼬리", null, Visibility.PRIVATE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.PARENT_NOT_FOUND);
    }

    @Test
    @DisplayName("내용이 비어있으면 생성을 거부한다(VALIDATION_ERROR)")
    void 내용이_비어있으면_생성을_거부한다() {
        assertThatThrownBy(() -> questionService.create(1L, null, 10L, "   ", null, Visibility.PRIVATE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("내용이 2000자를 초과하면 생성을 거부한다")
    void 내용이_2000자를_초과하면_생성을_거부한다() {
        String tooLong = "가".repeat(2001);

        assertThatThrownBy(() -> questionService.create(1L, null, 10L, tooLong, null, Visibility.PRIVATE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("visibility가 null이면 PRIVATE 기본값으로 저장된다")
    void visibility가_null이면_PRIVATE_기본() {
        given(questionRepository.save(any(Question.class))).willAnswer(inv -> inv.getArgument(0));

        Question q = questionService.create(1L, null, 10L, "내용", null, null);

        assertThat(q.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    @DisplayName("getOwned: 없으면 QUESTION_NOT_FOUND")
    void getOwned_없으면_QUESTION_NOT_FOUND() {
        given(questionRepository.findActiveByIdAndOwnerId(7L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.getOwned(1L, 7L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.QUESTION_NOT_FOUND);
    }
}
