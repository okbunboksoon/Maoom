package maoomWeb.ire.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@SpringBootTest
class AdminMainTemplateTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    void rendersAdminMainTemplate() {
        Context context = new Context();
        context.setVariables(Map.of(
                "currentUserId", "admin",
                "currentUserName", "admin"));

        String html = templateEngine.process("admin/adminMain", context);

        assertThat(html)
                .contains("Maoom 관리자")
                .contains("/api/user/profile-image?userId=admin")
                .contains("onclick=\"openAccountDialog()\"")
                .contains("id=\"accountDialog\"")
                .contains("data-admin-view=\"users\"")
                .contains("data-admin-view=\"color-check\"")
                .contains("data-admin-view=\"ber-asis-tobe\"")
                .contains("data-admin-view=\"project-logs\"")
                .contains("id=\"userTable\"")
                .contains("id=\"colorCheckTable\"")
                .contains("id=\"berAsisTobeTable\"")
                .contains("id=\"berSentenceImportToggle\"")
                .contains("As-is/To-be 엑셀 반영")
                .contains("id=\"projectLogTable\"")
                .doesNotContain("onclick=\"location.href='/main'\"");
    }
}
