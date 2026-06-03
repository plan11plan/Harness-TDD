package jobterview.server.question.interfaces;

import jobterview.server.support.E2ETest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuestionController E2E — 실제 HTTP(RANDOM_PORT) + 인증. 201/400/401 + 남의 리소스 404.
 */
class QuestionE2ETest extends E2ETest {

    @Test
    @DisplayName("POST /questions 유효 요청 → 201 + Location + 상세")
    void 유효한_요청으로_생성하면_201과_상세를_반환한다() {
        AuthContext auth = signupNewUser();
        long catId = createCategory(auth.accessToken(), "백엔드");

        ResponseEntity<JsonNode> res = rest.exchange("/api/v1/questions", HttpMethod.POST,
                authJsonEntity(auth.accessToken(), """
                        {"content":"프로세스와 스레드 차이는?","categoryId":%d,"tagNames":["CS"]}"""
                        .formatted(catId)),
                JsonNode.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode b = res.getBody();
        assertThat(b.get("content").asText()).isEqualTo("프로세스와 스레드 차이는?");
        assertThat(b.get("category").get("id").asLong()).isEqualTo(catId);
        assertThat(b.get("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(b.get("tags").get(0).get("name").asText()).isEqualTo("CS");
        assertThat(res.getHeaders().getLocation().toString())
                .contains("/api/v1/questions/" + b.get("id").asLong());
    }

    @Test
    @DisplayName("POST /questions 빈 내용 → 400 VALIDATION_ERROR")
    void 내용이_비면_400을_반환한다() {
        AuthContext auth = signupNewUser();
        long catId = createCategory(auth.accessToken(), "백엔드");

        ResponseEntity<JsonNode> res = rest.exchange("/api/v1/questions", HttpMethod.POST,
                authJsonEntity(auth.accessToken(), """
                        {"content":"","categoryId":%d}""".formatted(catId)),
                JsonNode.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("POST /questions 인증 없음 → 401")
    void 인증_없이_생성하면_401을_반환한다() {
        ResponseEntity<JsonNode> res = rest.exchange("/api/v1/questions", HttpMethod.POST,
                jsonEntity("""
                        {"content":"비인증","categoryId":1}"""),
                JsonNode.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /questions 남의 카테고리 → 404 CATEGORY_NOT_FOUND")
    void 남의_카테고리로_생성하면_404를_반환한다() {
        AuthContext owner = signupNewUser();
        long catId = createCategory(owner.accessToken(), "OS");
        AuthContext intruder = signupNewUser();

        ResponseEntity<JsonNode> res = rest.exchange("/api/v1/questions", HttpMethod.POST,
                authJsonEntity(intruder.accessToken(), """
                        {"content":"침입","categoryId":%d}""".formatted(catId)),
                JsonNode.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().get("code").asText()).isEqualTo("CATEGORY_NOT_FOUND");
    }

    private long createCategory(String token, String name) {
        return rest.exchange("/api/v1/categories", HttpMethod.POST,
                authJsonEntity(token, "{\"name\":\"" + name + "\",\"parentId\":null}"),
                JsonNode.class).getBody().get("id").asLong();
    }
}
