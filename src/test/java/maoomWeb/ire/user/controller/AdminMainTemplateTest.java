package maoomWeb.ire.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@SpringBootTest
class AdminMainTemplateTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    void rendersAdminMainTemplate() throws Exception {
        Context context = new Context();
        context.setVariables(Map.of(
                "currentUserId", "admin",
                "currentUserName", "admin"));

        String html = templateEngine.process("admin/adminMain", context);
        String css = new String(new ClassPathResource("static/user/css/AdminMain.css")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(html)
                .contains("Maoom 관리자")
                .contains("/api/user/profile-image?userId=admin")
                .contains("onclick=\"openAccountDialog()\"")
                .contains("id=\"accountDialog\"")
                .contains("data-admin-view=\"users\"")
                .contains("data-admin-view=\"color-check\"")
                .contains("data-admin-view=\"ber-asis-tobe\"")
                .contains("data-admin-view=\"qsg-db\"")
                .contains("data-admin-view=\"project-text-db\"")
                .contains("data-admin-view=\"project-note-db\"")
                .contains("data-admin-view=\"project-logs\"")
                .contains("data-admin-view=\"schematron-history\"")
                .contains("id=\"userTable\"")
                .contains("id=\"colorCheckTable\"")
                .contains("id=\"berAsisTobeTable\"")
                .contains("id=\"berSentenceImportToggle\"")
                .contains("As-is/To-be 신규 반영")
                .contains("id=\"qsgDbTable\"")
                .contains("QSG DB")
                .contains("id=\"projectTextDbTable\"")
                .contains("TEXT DB")
                .contains("id=\"projectNoteDbTable\"")
                .contains("NOTE DB")
                .contains("id=\"projectNoteNoteCount\"")
                .contains("id=\"projectNoteTipCount\"")
                .contains("data-admin-view=\"replace-dark-symbol\"")
                .contains("id=\"replaceDarkSymbolCreateToggle\"")
                .contains("id=\"replaceDarkSymbolCreateForm\"")
                .contains("id=\"replaceDarkSymbolCreateFrom\"")
                .contains("id=\"replaceDarkSymbolCreateTo\"")
                .contains("id=\"projectLogTable\"")
                .contains("<i class=\"fas fa-history\"></i>")
                .contains("id=\"schematronHistoryTable\"")
                .contains("<th>DITAMAP 파일명</th>")
                .contains("colspan=\"8\" class=\"admin-empty-cell\"")
                .doesNotContain("onclick=\"location.href='/main'\"");
        assertThat(css)
                .contains("#schematronHistoryTable th:nth-child(1)")
                .contains("width:5% !important;")
                .contains("#schematronHistoryTable th:nth-child(4)")
                .contains("width:22% !important;")
                .contains("#schematronHistoryTable th:nth-child(7)")
                .contains("width:28% !important;")
                .contains("#schematronHistoryTable th:nth-child(8)")
                .contains("width:11% !important;")
                .contains(".replace-symbol-create-form")
                .contains("grid-template-columns:260px 180px auto;")
                .contains("justify-content:start;");
    }
}
