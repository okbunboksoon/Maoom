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
                .contains("id=\"userTable\"")
                .contains("/admin/users")
                .doesNotContain("onclick=\"location.href='/main'\"");
    }
}
