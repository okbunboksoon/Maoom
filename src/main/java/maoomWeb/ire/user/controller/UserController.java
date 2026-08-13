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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
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
 *
 * <p>유저 메인 기능 카드는 {@code templates/user/userMain.html}의
 * {@code launcherMenus} 배열에서 관리한다. 새 기능을 추가하거나 URL을 바꿀 때는
 * 이 컨트롤러의 화면 라우팅, userMain.html의 open...Popup() 함수, 실제 실행 API
 * 컨트롤러를 함께 맞춰야 한다.</p>
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
	
    /**
     * 로그인 후 기능 선택 화면을 표시한다.
     *
     * <p>화면 위치는 {@code templates/user/userMain.html}이다.
     * 여기서 PDF 리뷰, 견적, BER, QSG, 정제, 다국어 변환, DITAMAP Builder,
     * 제품사양서 비교 팝업을 연다.</p>
     */
    @GetMapping("/main")
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

    /** 관리자 메인 화면을 표시한다. */
    @GetMapping("/admin")
    public String adminRoot() {
        return "redirect:/admin/main";
    }

    @GetMapping("/admin/main")
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

    /**
     * PDF 리뷰 카드가 여는 Google Drive PDF 선택 화면.
     *
     * <p>화면 위치: {@code templates/user/pdf/pdfList.html}.
     * PDF 선택 후 뷰어는 {@link #pdfView(String, String, Model)}로 이동하고,
     * 댓글/첨부/내보내기 처리는 CommentController와 PdfController 쪽 API가 담당한다.</p>
     */
    @GetMapping("/pdf/list")
    public String pdfList() {
        return "user/pdf/pdfList";
    }

    /**
     * 정제 카드가 여는 DITA/XML 정제 팝업 화면.
     *
     * <p>화면 위치: {@code templates/user/revision/revisionPopup.html}.
     * 실제 실행 API는 RevisionController, 업무 처리는 RevisionPipelineService가 담당한다.</p>
     */
    @GetMapping("/revision/list")
    public String pdfList2() {
        return "user/revision/revisionPopup";
    }

    /**
     * 다국어 변환 카드가 여는 입력 경로 선택 팝업 화면.
     *
     * <p>화면 위치: {@code templates/user/multilingual/multilingualPopup.html}.
     * 실제 실행 API는 MultilingualController, 업무 처리는 MultilingualConversionService가 담당한다.</p>
     */
    @GetMapping("/multilingual/list")
    public String multilingual() {
        return "user/multilingual/multilingualPopup";
    }

    /**
     * QSG 카드가 여는 언어와 입력 경로 선택 팝업 화면.
     *
     * <p>화면 위치: {@code templates/user/multilingual/qsgPopup.html}.
     * 실제 실행 API는 QsgController, 업무 처리는 QsgApplyService가 담당한다.
     * 관리자 QSG DB 화면은 같은 {@code QSG_DB.xml}을 조회/업로드/내보내기한다.</p>
     */
    @GetMapping("/qsg/list")
    public String qsg() {
        return "user/multilingual/qsgPopup";
    }
    
    @GetMapping("/page/calc")
    public String pdfList3() {
        return "user/pdf/pdfList";
    }
    
    /**
     * PDF 리뷰에서 선택한 파일을 PDF 뷰어 화면으로 연다.
     *
     * <p>화면 위치: {@code templates/user/pdf/pdfview.html}.
     * fileId/folderId는 Google Drive 파일과 폴더를 찾는 핵심 키다.
     * 댓글 좌표, 하이라이트, 웹소켓 협업 상태는 pdfview.html의 JS와
     * CommentController/PdfCollaborationHandler 쪽을 함께 봐야 한다.</p>
     */
    @GetMapping("/pdf/view")
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

    /**
     * 견적 카드가 여는 팝업 화면.
     *
     * <p>화면 위치: {@code templates/user/pdf/colorCheck.html}.
     * 화면에 실제 저장 위치를 표시할 수 있도록 서버 PC의 바탕화면/temp 전체 경로를
     * Thymeleaf 모델에 함께 넣는다. PDF 분석, 최종 엑셀 생성, DB 반영은
     * {@link ColorCheckController}의 API와 ColorCheckWorkflowService가 처리한다.</p>
     */
    @GetMapping("/pdf/color-check")
    public String colorCheck(Model model) {
        model.addAttribute(
                "colorCheckOutputPath",
                colorCheckOutputPathService.getOutputDirectory().toString());
        return "user/pdf/colorCheck";
    }

    /**
     * BER 반영 카드가 여는 팝업 화면.
     *
     * <p>화면 위치: {@code templates/user/ber/ber.html}.
     * 실행 API는 BerController, 업무 처리는 BerApplyService가 담당한다.
     * 관리자 BER DB 화면과 XML 생성 흐름은 BerAsisTobeAdminService,
     * BerAsisTobeXmlService를 함께 확인한다.</p>
     */
    @GetMapping("/pdf/ber")
    public String ber(Model model) {
        model.addAttribute(
                "colorCheckOutputPath",
                colorCheckOutputPathService.getOutputDirectory().toString());
        return "user/ber/ber";
    }

    /**
     * 제품사양서 비교 카드가 여는 팝업 화면.
     *
     * <p>화면 위치: {@code templates/user/productSpecComparison/productSpecComparison.html}.
     * 실제 비교 API는 ProductSpecComparisonController, 업무 처리는
     * ProductSpecComparisonService가 담당한다.</p>
     */
    @GetMapping("/pdf/product-spec-comparison")
    public String productSpecComparison() {
        return "user/productSpecComparison/productSpecComparison";
    }

    /** 도안의뢰서 작성 카드가 여는 입력 팝업 화면을 표시한다. */
    @GetMapping("/pdf/artwork-request")
    public String artworkRequest() {
        return "user/artworkRequest/artworkRequest";
    }

    /**
     * 법규 Ditamap Builder 카드가 여는 작업 경로 입력 화면.
     *
     * <p>화면 위치: {@code templates/user/ditamapBuilder/ditamapBuilder.html}.
     * 실제 트리 생성/비교/편집 API는 DitamapBuilderController와
     * DitamapBuilderService가 담당한다.</p>
     */
    @GetMapping("/ditamap-builder")
    public String ditamapBuilder() {
        return "user/ditamapBuilder/ditamapBuilder";
    }

    /** Index 추출 카드가 여는 DITA Index 검토 엑셀 생성 팝업 화면을 표시한다. */
    @GetMapping("/index-extract")
    public String indexExtract() {
        return "user/indexExtract/index";
    }

    @GetMapping("/ditamap-builder/view")
    /** DITAMAP Builder 트리 조회 결과 화면을 표시한다. */
    public String ditamapBuilderView() {
        return "user/ditamapBuilder/ditamapBuilderView";
    }

    @GetMapping("/ditamap-builder/diff")
    /** 1안 테스트용: 법규 마스터와 실제 매뉴얼을 비교하는 DITAMAP Builder 화면을 표시한다. */
    public String ditamapBuilderDiff() {
        return "user/ditamapBuilder/ditamapBuilderDiff";
    }

    @GetMapping("/ditamap-builder/realtime")
    /** 2안 테스트용: DB 대상 파일명을 기준으로 실시간 반영하는 DITAMAP Builder 화면을 표시한다. */
    public String ditamapBuilderRealtime() {
        return "user/ditamapBuilder/ditamapBuilderRealtime";
    }

    @GetMapping("/ditamap-builder/legal-editor")
    /** 선택한 기준 topic을 법규용 DITAMAP 구조에 배치하는 팝업 화면을 표시한다. */
    public String ditamapLegalEditor() {
        return "user/ditamapBuilder/ditamapLegalEditor";
    }

    @GetMapping("/ditamap-builder-test")
    /** 법규 DITAMAP Builder 변경 검증용 시작 화면을 표시한다. */
    public String ditamapBuilderTest() {
        return "user/ditamapBuilderTest/ditamapBuilderTest";
    }

    @GetMapping("/ditamap-builder-test/diff")
    /** 법규 DITAMAP Builder 변경 검증용 비교 화면을 표시한다. */
    public String ditamapBuilderTestDiff() {
        return "user/ditamapBuilderTest/ditamapBuilderTestDiff";
    }

    @GetMapping("/ditamap-builder-test/legal-editor")
    /** 법규 DITAMAP Builder 변경 검증용 편집 화면을 표시한다. */
    public String ditamapLegalTestEditor() {
        return "user/ditamapBuilderTest/ditamapLegalTestEditor";
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
    public ResponseEntity<Map<String,String>> updateAccount(
            @RequestBody UserAccountUpdateDto dto,
            Authentication authentication) {

        try{
            User updatedUser =
                    userService.updateAccount(
                            currentUserService.getUserId(authentication),
                            dto);

            return ResponseEntity.ok(Map.of(
                    "userId", updatedUser.getUserId(),
                    "userName", updatedUser.getUserName()));
        }catch(ResponseStatusException e){
            String message = e.getReason();

            if(message == null || message.isBlank()){
                message = "계정 정보를 수정하지 못했습니다.";
            }

            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(Map.of("message", message));
        }
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




