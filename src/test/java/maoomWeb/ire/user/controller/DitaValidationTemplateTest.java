package maoomWeb.ire.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class DitaValidationTemplateTest {
    @Test
    void togglesExecutionModeButtonsFromTemplateSetting() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        Context hiddenContext = new Context();
        hiddenContext.setVariable("executionModeVisible", false);
        Context visibleContext = new Context();
        visibleContext.setVariable("executionModeVisible", true);

        String hidden = engine.process("user/ditaValidation/input", hiddenContext);
        String visible = engine.process("user/ditaValidation/input", visibleContext);

        String hiddenModeTag = "class=\\\"execution-mode-options\\\"\\s+aria-label=\\\"[^\\\"]+\\\" hidden=\\\"hidden\\\"";
        assertThat(hidden).containsPattern(hiddenModeTag);
        assertThat(visible).contains("class=\"execution-mode-options\"")
                .doesNotContainPattern(hiddenModeTag);
    }

    @Test
    void rendersReportListWithoutThymeleafInlineExpressionCollision() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        String rendered = engine.process("user/ditaValidation/reportList", new Context());

        assertThat(rendered)
                .contains("<h1>리포트 확인</h1>")
                .contains("const metadata=")
                .contains("dita-report-meta-item");
    }

    @Test
    void selectsLocalPathModeBeforeOpeningTheExistingCheckScreen() throws Exception {
        String html = new ClassPathResource("templates/user/ditaValidation/input.html")
                .getContentAsString(StandardCharsets.UTF_8);
        String userMain = new String(new ClassPathResource("templates/user/userMain.html")
                .getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
        String css = new String(new ClassPathResource("static/user/css/RevisionPopup.css")
                .getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1).replace("\r\n", "\n");

        assertThat(html)
                .contains("class=\"ber-popup-body dita-validation-input-body\"")
                .contains("<strong>작업 선택</strong>")
                .contains("id=\"inputPath\"")
                .contains("id=\"localPathMode\"")
                .contains("id=\"gomsMode\"")
                .contains("class=\"execution-mode-options\"")
                .contains("th:hidden=\"${!executionModeVisible}\"")
                .contains("id=\"gomsMode\" name=\"executionMode\" type=\"radio\" value=\"GOMS\" checked")
                .contains("name=\"executionMode\"")
                .contains("로컬경로 실행")
                .contains("GOMS 실행")
                .contains("showCheckWindowWaiting(checkWindow,'입력 경로를 확인하고 있습니다.')")
                .contains("showCheckWindowWaiting(checkWindow,'GOMS EXPORT를 실행하고 있습니다.')")
                .contains("input.disabled=true;executionModes.forEach(mode=>mode.disabled=true)")
                .contains("function showCheckWindowWaiting(checkWindow,text)")
                .contains("width=540,height=205")
                .contains("const popupName='ditaValidationCheckPopup_'+Date.now()")
                .contains("window.open('',popupName")
                .contains("event.data!=='dita-validation-ready'")
                .contains("setTimeout(()=>{checkWindow.focus();window.close()},100)")
                .doesNotContain("window.open('about:blank','ditaValidationCheckPopup'")
                .contains("scrollbars=no")
                .contains("checkWindow.resizeTo(1000,1200)")
                .contains("checkWindow.document.body.style.margin='0'")
                .contains("checkWindow.document.documentElement.style.overflow='hidden'")
                .contains("font-size:12px;white-space:nowrap")
                .contains("font-size:8px;line-height:1.4")
                .contains("처리 대상에 따라 시간이 걸릴 수 있습니다.")
                .contains("/api/dita-validation/goms/export")
                .contains("selectedMode.value==='GOMS'")
                .contains("resolvedPath=(await exportResponse.json()).inputPath")
                .contains("'&executionMode='+encodeURIComponent(selectedMode.value)")
                .contains("input[name=\"executionMode\"]:checked")
                .contains("실행 방식을 선택해 주세요.")
                .contains("function fitPopupToContent()")
                .contains("page.getBoundingClientRect().bottom")
                .contains("heightDelta=targetInnerHeight-window.innerHeight")
                .contains("window.resizeBy(0,heightDelta)")
                .contains("message.textContent=text;fitPopupToContent()")
                .contains("입력 경로를 입력해 주세요.")
                .contains("실행 방식을 선택해 주세요.")
                .contains("검사 화면 팝업이 차단되었습니다.")
                .contains("입력 경로를 확인하지 못했습니다.")
                .doesNotContain("fetch('/api/dita-validation/maps'")
                .contains("/dita-validation/check?inputPath=")
                .contains("id=\"continueBtn\"")
                .contains("<div class=\"dita-report-entry\" aria-label=\"리포트 확인\">")
                .doesNotContain("<section class=\"revision-panel dita-report-entry\"")
                .contains("<strong>리포트 확인</strong>")
                .contains("id=\"reportListBtn\"")
                .contains("/dita-validation/reports")
                .contains("const previousWidth=1168,width=Math.min(1188,screen.availWidth-24)")
                .contains("screen.availWidth-Math.min(previousWidth,width)")
                .contains("window.addEventListener('load',fitPopupToContent)")
                .contains(">실행</button>");
        assertThat(html.indexOf("id=\"localPathMode\"")).isLessThan(html.indexOf("id=\"inputPath\""));
        String checkHtml = new ClassPathResource("templates/user/ditaValidation/check.html")
                .getContentAsString(StandardCharsets.UTF_8);
        String checkCss = new ClassPathResource("static/user/css/DitaValidationCheck.css")
                .getContentAsString(StandardCharsets.UTF_8);
        String checkJs = new ClassPathResource("static/user/js/DitaValidationCheck.js")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(checkHtml)
                .contains("/user/css/DitaValidationCheck.css")
                .contains("/user/js/DitaValidationCheck.js")
                .doesNotContain("<style>")
                .doesNotContain("<script>\n");
        assertThat(checkJs).contains("post('/api/dita-validation/maps',{inputPath})");
        assertThat(checkJs)
                .contains("window.addEventListener('load',focusCheckWindowAfterLoad)")
                .contains("window.opener.postMessage('dita-validation-ready',location.origin)")
                .contains("if(!document.hasFocus())startTitleBlink()")
                .contains("'● 검사 화면이 준비되었습니다'")
                .contains("window.addEventListener('focus',stopTitleBlink)")
                .contains("document.title=checkWindowTitle");
        assertThat(checkCss)
                .contains(".validation-grid>label:not([for]){display:flex;align-items:center;gap:6px;min-height:36px}")
                .contains(".schematron-options{display:flex;align-items:center;min-width:0;min-height:36px}");
        assertThat(userMain).contains("'ditaValidationPopup',\n        popupWindowOptions(680, 378, 5)");
        assertThat(userMain)
                .contains("data-dita-validation-allowed=${ditaValidationAllowed}")
                .contains("ditaValidationOnly:true")
                .contains("menu.ditaValidationOnly && !isDitaValidationAllowed()");
        assertThat(css)
                .contains(".dita-validation-input-body .revision-page{\n    padding-bottom:24px;\n    display:flex;\n    flex-direction:column;\n    gap:0;")
                .contains(".execution-mode-options[hidden]{\n    display:none;")
                .contains(".dita-report-entry{\n    margin-top:18px;\n    padding-top:18px;\n    border-top:1px solid #e2e8f0;")
                .contains(".dita-report-entry-action{\n    display:flex;\n    justify-content:flex-end;\n    margin-top:14px;\n    padding-bottom:14px;\n    border-bottom:1px solid #e2e8f0;");
        String reportList = new ClassPathResource("templates/user/ditaValidation/reportList.html")
                .getContentAsString(StandardCharsets.UTF_8);
        String reportCss = new ClassPathResource("static/user/css/DitaReportList.css")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(reportList)
                .contains("<h1>리포트 확인</h1>")
                .contains("id=\"refreshBtn\"")
                .contains("/api/dita-validation/goms/reports")
                .contains("['검사파일',report.validatedFileCount+'건']")
                .contains("[null,scopeLabel(report.scopeType)]")
                .contains("['검사 완료 시간',report.finishedAt]")
                .contains("['QA 테스터',report.testerName||'-']")
                .contains("['반영률',report.appliedCount+' / '+report.issueCount]")
                .doesNotContain("[[")
                .contains("function reportStatus(report)")
                .contains("수정 가능")
                .contains("수정 중")
                .contains("수정 완료")
                .contains("오류 없음")
                .contains("읽기 전용");
        assertThat(reportList)
                .contains("function scopeLabel(scopeType)")
                .contains("'범위 전체'")
                .contains("'선택한 항목'")
                .contains("'선택항목 및 자손'")
                .doesNotContain("['검사 범위',scopeLabel(report.scopeType)]");
        assertThat(reportCss)
                .contains("html,body{margin:0;min-width:1188px;height:100%}")
                .contains(".dita-report-page{display:flex;flex-direction:column;width:1140px")
                .contains(".dita-report-folder::before")
                .contains("border-left:1px solid #dfe4ea")
                .contains(".dita-report-summary")
                .contains(".dita-report-meta-item")
                .contains(".dita-report-file-name{flex:0 0 auto;min-width:0;max-width:420px")
                .contains(".dita-report-badge{flex:0 0 auto;margin-left:0")
                .doesNotContain("content:\"|\"")
                .contains(".dita-report-badge.editable")
                .contains(".dita-report-badge.in-progress")
                .contains(".dita-report-badge.complete,.dita-report-badge.no-issues");
    }

    @Test
    void startsAndCancelsAnIndependentBackgroundJob() throws Exception {
        String html = new ClassPathResource("templates/user/ditaValidation/check.html")
                .getContentAsString(StandardCharsets.UTF_8);
        String javascript = new ClassPathResource("static/user/js/DitaValidationCheck.js")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("id=\"cancelBtn\"");
        assertThat(javascript)
                .contains("/api/dita-validation/jobs/'+encodeURIComponent(currentProgressToken)+'/cancel")
                .contains("/api/dita-validation/jobs/'+encodeURIComponent(jobId)")
                .contains("const detail=progress.message?progress.message:progress.completed+' / '+progress.total+'개 검사 완료'+current+updated")
                .contains("show('running','Schematron 검사 중',detail)")
                .contains("const accepted=await post('/api/dita-validation/run'")
                .contains("executionMode=query.get('executionMode')||'LOCAL_PATH'")
                .contains("progressToken,executionMode")
                .doesNotContain("navigator.sendBeacon")
                .doesNotContain("pagehide")
                .contains("choice.available?'이전 버전 사용 중':'사용 불가'")
                .contains("button.textContent='상세 보기'")
                .contains("className='schematron-detail'")
                .contains("DOCS_PUB_CHECK_v*")
                .contains("schematronType=choice&&choice.available?choice.type:''")
                .doesNotContain("radio.type='radio'")
                .doesNotContain("name='schematronType'");
    }
}
