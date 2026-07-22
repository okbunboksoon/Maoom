package maoomWeb.ire.user.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import maoomWeb.ire.user.service.CurrentUserService;

/**
 * 기능별 사용 가이드 목록과 상세 화면을 담당한다.
 *
 * <p>메인/로그인/사용자 계정 처리는 {@link UserController}에 남기고, 가이드
 * 화면 데이터와 라우팅은 이 컨트롤러로 분리한다. URL은 기존과 동일하게
 * {@code /guide}, {@code /guide/{guideKey}}를 유지한다.</p>
 */
@Controller
public class GuideController {

    private final CurrentUserService currentUserService;

    public GuideController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/guide")
    /** 기능별 사용 가이드 목록을 표시한다. */
    public String guideList(
            Authentication authentication,
            Model model) {

        addCurrentUserModel(authentication, model);
        model.addAttribute("guides", getGuides());
        return "user/guide/guideList";
    }

    @GetMapping("/guide/{guideKey}")
    /** 선택한 사용 가이드 상세 화면을 표시한다. */
    public String guideDetail(
            @PathVariable String guideKey,
            Authentication authentication,
            Model model) {

        Map<String,String> guide = getGuides()
                .stream()
                .filter(item -> guideKey.equals(item.get("key")))
                .findFirst()
                .orElse(null);

        if (guide == null) {
            return "redirect:/guide";
        }

        addCurrentUserModel(authentication, model);
        model.addAttribute("guide", guide);
        return "user/guide/guideDetail";
    }

    private void addCurrentUserModel(
            Authentication authentication,
            Model model) {

        model.addAttribute(
                "currentUserName",
                currentUserService.getUserName(authentication));
        model.addAttribute(
                "currentUserId",
                currentUserService.getUserId(authentication));
        model.addAttribute(
                "administrator",
                currentUserService.isAdministrator(authentication));
    }

    private List<Map<String,String>> getGuides() {

        return List.of(
                guide(
                        "pdf-review",
                        "PDF 리뷰 가이드",
                        "PDF 선택, 댓글 작성, 첨부파일 확인 흐름",
                        "PDF"),
                guide(
                        "color-check",
                        "견적 컬러체크 가이드",
                        "도안 컬러체크 엑셀 생성과 결과 확인",
                        "PDF"),
                guide(
                        "ditamap-builder",
                        "법규 Ditamap Builder 가이드",
                        "DITAMAP 경로 선택과 구조 생성 절차",
                        "DITA"),
                guide(
                        "ber",
                        "BER 반영 가이드",
                        "BER 엑셀 생성과 DB 반영 작업",
                        "DITA"),
                guide(
                        "revision",
                        "정제 가이드",
                        "DITA 정제 옵션 선택과 실행 결과 확인",
                        "DITA",
                        "https://app.notion.com/p/maoom/LAB-871f38ecae198272b282814336b4d31d"));
    }

    private Map<String,String> guide(
            String key,
            String title,
            String description,
            String category) {

        Map<String,String> guide = new LinkedHashMap<>();
        guide.put("key", key);
        guide.put("title", title);
        guide.put("description", description);
        guide.put("category", category);
        guide.put("url", "/guide/" + key);
        return guide;
    }

    private Map<String,String> guide(
            String key,
            String title,
            String description,
            String category,
            String externalUrl) {

        Map<String,String> guide = guide(
                key,
                title,
                description,
                category);
        guide.put("externalUrl", externalUrl);
        guide.put("url", externalUrl);
        return guide;
    }
}
