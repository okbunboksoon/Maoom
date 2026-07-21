package maoomWeb.ire.user.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import maoomWeb.ire.user.service.ColorCheckOutputPathService;
import maoomWeb.ire.user.service.UserService;
import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.dto.UserAccountUpdateDto;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.UserProfileImageService;

/**
 * 로그인 처리, 주요 화면 이동과 사용자 계정·프로필 API를 담당한다.
 *
 * <p>화면 이동 요청은 Thymeleaf 템플릿 이름을 반환하고, {@code /api/user/*}
 * 요청은 UserService 또는 UserProfileImageService에 업무 처리를 위임한다.
 * 로그인 성공 시에는 Spring Security의 Authentication과 세션 Context를 직접 만든다.</p>
 */
@Controller
public class UserController {

    private final UserService userService;
	private final CurrentUserService currentUserService;
	private final UserProfileImageService userProfileImageService;
    private final ColorCheckOutputPathService colorCheckOutputPathService;
	
    public UserController(
            UserService userService,
            CurrentUserService currentUserService,
            UserProfileImageService userProfileImageService,
            ColorCheckOutputPathService colorCheckOutputPathService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.userProfileImageService = userProfileImageService;
        this.colorCheckOutputPathService = colorCheckOutputPathService;
    }
	
    /** 이미 로그인한 사용자는 메인으로 보내고, 그 외에는 기본 로그인 화면을 표시한다. */
	@GetMapping("/")
	public String login(Authentication authentication) {

		if(authentication != null
				&& authentication.isAuthenticated()
				&& !"anonymousUser".equals(
						authentication.getPrincipal())) {
			return "redirect:/main";
		}

	    return "index";
	}
	
    /**
     * 로그인 폼의 ID/비밀번호를 검증하고 성공 시 Spring Security 세션을 생성한다.
     * 실패 메시지는 RedirectAttributes에 담아 다시 index.html에서 표시한다.
     */
	@PostMapping("/login")
	public String login(@RequestParam String username,
	                    @RequestParam String password,
	                    RedirectAttributes reAttr,
	                    HttpServletRequest request) {
		
		Map<String,Object> checkResult=userService.checkLogin(username,password);	
		
		if((boolean) checkResult.get("result")) {			

			User user =
					(User) checkResult.get("userInfo");

			String role =
					user.getUserRole();

			if(role == null || role.isBlank()){
				role = "USER";
			}

			if(!role.startsWith("ROLE_")){
				role = "ROLE_" + role;
			}

			Authentication authentication =
					new UsernamePasswordAuthenticationToken(
							user.getUserId(),
							null,
							Collections.singletonList(
									new SimpleGrantedAuthority(role)));

			SecurityContext context =
					SecurityContextHolder.createEmptyContext();

			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);

			request.getSession(true).setAttribute(
					HttpSessionSecurityContextRepository
					.SPRING_SECURITY_CONTEXT_KEY,
					context);

		    return "redirect:/main";
		}

	    reAttr.addFlashAttribute(
	    		"msg",
	    		"아이디 또는 비밀번호가 틀립니다.");
	    return "redirect:/";
	}
	
    @GetMapping("/main")
    /** 로그인 후 기능 선택 화면을 표시한다. */
    public String main(
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
        return "user/userMain";
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

        if(guide == null){
            return "redirect:/guide";
        }

        addCurrentUserModel(authentication, model);
        model.addAttribute("guide", guide);
        return "user/guide/guideDetail";
    }

    @GetMapping("/admin/main")
    /** 관리자 메인 화면을 표시한다. */
    public String adminMain(
            Authentication authentication,
            Model model) {

        if(!currentUserService.isAdministrator(authentication)){
            return "redirect:/main";
        }

        model.addAttribute(
                "currentUserName",
                currentUserService.getUserName(authentication));
        model.addAttribute(
                "currentUserId",
                currentUserService.getUserId(authentication));
        return "admin/adminMain";
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
                        "DITA"));
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
        return guide;
    }
    
    @GetMapping("/pdf/list")
    /** Google Drive의 PDF 선택 화면을 표시한다. */
    public String pdfList() {
        return "user/pdf/pdfList";
    }

    @GetMapping("/revision/list")
    /** DITA 정제 옵션과 실행 결과를 제공하는 팝업 화면을 표시한다. */
    public String pdfList2() {
        return "user/revision/revisionPopup";
    }

    @GetMapping("/multilingual/list")
    /** 다국어 변환 입력 경로 선택 팝업 화면을 표시한다. */
    public String multilingual() {
        return "user/multilingual/multilingualPopup";
    }

    @GetMapping("/qsg/list")
    /** QSG 언어와 입력 경로 선택 팝업 화면을 표시한다. */
    public String qsg() {
        return "user/multilingual/qsgPopup";
    }
    
    @GetMapping("/page/calc")
    public String pdfList3() {
        return "user/pdf/pdfList";
    }
    
    @GetMapping("/pdf/view")
    /** PDF 뷰어에 필요한 Drive 파일 및 폴더 ID를 모델에 전달한다. */
    public String pdfView(@RequestParam String fileId,
                          @RequestParam String folderId,
                          Model model) {

        model.addAttribute("fileId", fileId);
        model.addAttribute("folderId", folderId);

        return "user/pdf/pdfview";
    }

    @GetMapping("/pdf/upload")
    public String pdfUpload() {
        return "user/pdf/pdfUpload";
    }

    @GetMapping("/pdf/color-check")
    /**
     * 컬러체크 팝업 화면을 연다.
     *
     * <p>화면에 실제 저장 위치를 표시할 수 있도록 서버 PC의
     * 바탕화면/temp 전체 경로를 Thymeleaf 모델에 함께 넣는다.
     * PDF 분석이나 DB 작업은 이 메서드가 아니라
     * {@link ColorCheckController}의 API가 처리한다.</p>
     */
    public String colorCheck(Model model) {
        model.addAttribute(
                "colorCheckOutputPath",
                colorCheckOutputPathService.getOutputDirectory().toString());
        return "user/pdf/colorCheck";
    }

    @GetMapping("/pdf/ber")
    /** BER 반영 팝업 화면을 연다. */
    public String ber(Model model) {
        model.addAttribute(
                "colorCheckOutputPath",
                colorCheckOutputPathService.getOutputDirectory().toString());
        return "user/pdf/ber";
    }
    @GetMapping("/ditamap-builder")
    /** DITAMAP Builder 작업 경로 입력 화면을 표시한다. */
    public String ditamapBuilder() {
        return "user/ditamapBuilder";
    }

    @GetMapping("/ditamap-builder/view")
    /** DITAMAP Builder 트리 조회 결과 화면을 표시한다. */
    public String ditamapBuilderView() {
        return "user/ditamapBuilderView";
    }

    @GetMapping("/ditamap-builder/diff")
    /** 1안 테스트용: 법규 마스터와 실제 매뉴얼을 비교하는 DITAMAP Builder 화면을 표시한다. */
    public String ditamapBuilderDiff() {
        return "user/ditamapBuilderDiff";
    }

    @GetMapping("/ditamap-builder/realtime")
    /** 2안 테스트용: DB 대상 파일명을 기준으로 실시간 반영하는 DITAMAP Builder 화면을 표시한다. */
    public String ditamapBuilderRealtime() {
        return "user/ditamapBuilderRealtime";
    }

    @GetMapping("/ditamap-builder/legal-editor")
    /** 선택한 기준 topic을 법규용 DITAMAP 구조에 배치하는 팝업 화면을 표시한다. */
    public String ditamapLegalEditor() {
        return "user/ditamapLegalEditor";
    }

    @GetMapping("/api/user/me")
    @ResponseBody
    /** 현재 로그인 사용자의 ID와 표시 이름을 반환한다. */
    public Map<String,String> getCurrentUser(
            Authentication authentication) {

        Map<String,String> user = new LinkedHashMap<>();

        user.put(
                "userId",
                currentUserService.getUserId(authentication));
        user.put(
                "userName",
                currentUserService.getUserName(authentication));

        return user;
    }

    @GetMapping("/api/user/mention-list")
    @ResponseBody
    /** 댓글 입력창의 멘션 자동완성 대상 목록을 반환한다. */
    public List<User> getMentionUsers() {
        return userService.getMentionUsers();
    }

    @PostMapping("/api/user/account")
    @ResponseBody
    /** 로그인 사용자의 이름과 선택적으로 비밀번호를 변경한다. */
    public Map<String,String> updateAccount(
            @RequestBody UserAccountUpdateDto dto,
            Authentication authentication) {

        User updatedUser =
                userService.updateAccount(
                        currentUserService.getUserId(authentication),
                        dto);

        return Map.of(
                "userId", updatedUser.getUserId(),
                "userName", updatedUser.getUserName());
    }

    @PostMapping("/api/user/profile-image")
    @ResponseBody
    /** 로그인 사용자의 새 프로필 이미지 파일을 저장하고 계정에 연결한다. */
    public Map<String,String> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        userProfileImageService.save(
                currentUserService.getUserId(authentication),
                file);
        return Map.of("result", "success");
    }

    @DeleteMapping("/api/user/profile-image")
    @ResponseBody
    /** 로그인 사용자의 프로필 이미지 DB 연결과 실제 저장 파일을 삭제한다. */
    public Map<String,String> deleteProfileImage(
            Authentication authentication) {

        userProfileImageService.delete(
                currentUserService.getUserId(authentication));
        return Map.of("result", "success");
    }

    @GetMapping("/api/user/profile-image")
    @ResponseBody
    /**
     * 지정 사용자의 프로필 이미지를 브라우저에 반환한다.
     * 댓글 작성자 아바타와 메인 계정 메뉴가 같은 API를 사용한다.
     */
    public ResponseEntity<Resource> getProfileImage(
            @RequestParam String userId) {

        UserProfileImageService.ProfileImage image =
                userProfileImageService.load(userId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if(image.contentType() != null
                && !image.contentType().isBlank()){
            mediaType = MediaType.parseMediaType(
                    image.contentType());
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(mediaType)
                .body(image.resource());
    }
}




