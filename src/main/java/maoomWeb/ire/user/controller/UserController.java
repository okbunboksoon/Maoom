package maoomWeb.ire.user.controller;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;

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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import maoomWeb.ire.user.service.UserService;
import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.dto.UserAccountUpdateDto;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.UserProfileImageService;



@Controller
/**
 * 로그인 처리와 주요 사용자 화면 이동, 사용자 정보 API를 담당한다.
 */
public class UserController {
	
	private final UserService userService;
	private final CurrentUserService currentUserService;
	private final UserProfileImageService userProfileImageService;
	
    public UserController(
            UserService userService,
            CurrentUserService currentUserService,
            UserProfileImageService userProfileImageService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.userProfileImageService = userProfileImageService;
    }
	
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
	
	@PostMapping("/login")
	public String login(@RequestParam String username,
	                    @RequestParam String password,
	                    RedirectAttributes reAttr,
	                    HttpServletRequest request) {

		System.out.println(username);
		System.out.println(password);
		
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
        return "user/userMain";
    }
    
    @GetMapping("/pdf/list")
    /** Google Drive의 PDF 선택 화면을 표시한다. */
    public String pdfList() {
        return "user/pdf/pdfList";
    }

    @GetMapping("/revision/list")
    public String pdfList2() {
        return "user/revision/revisionPopup";
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

        return "user/pdf/pdfView";
    }

    @GetMapping("/pdf/upload")
    public String pdfUpload() {
        return "user/pdf/pdfUpload";
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
    public Map<String,String> deleteProfileImage(
            Authentication authentication) {

        userProfileImageService.delete(
                currentUserService.getUserId(authentication));
        return Map.of("result", "success");
    }

    @GetMapping("/api/user/profile-image")
    @ResponseBody
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
