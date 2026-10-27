package maoomWeb.ire.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DitaValidationTemplateTest {
    @Test
    void startsAndCancelsAnIndependentBackgroundJob() throws Exception {
        String html = new ClassPathResource("templates/user/ditaValidation/check.html")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("id=\"cancelBtn\"")
                .contains("/api/dita-validation/jobs/'+encodeURIComponent(currentProgressToken)+'/cancel")
                .contains("/api/dita-validation/jobs/'+encodeURIComponent(jobId)")
                .contains("const accepted=await post('/api/dita-validation/run'")
                .doesNotContain("navigator.sendBeacon")
                .doesNotContain("pagehide")
                .contains("choice.available?'이전 버전 사용 중':'사용 불가'")
                .contains("button.textContent='상세 보기'")
                .contains("className='schematron-detail'")
                .contains("grid-template-columns:minmax(0,1fr) minmax(0,1fr)");
    }
}
