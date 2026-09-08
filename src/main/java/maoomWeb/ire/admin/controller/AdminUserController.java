package maoomWeb.ire.admin.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import maoomWeb.ire.admin.service.AdminUserCreateRequest;
import maoomWeb.ire.admin.service.AdminUserDto;
import maoomWeb.ire.admin.service.AdminUserService;
import maoomWeb.ire.admin.service.AdminUserUpdateRequest;

/** 관리자 화면의 사용자 DB 조회, 등록, 수정, 삭제 API. */
@Controller
public class AdminUserController {

    private final AdminUserService userService;

    public AdminUserController(AdminUserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    @ResponseBody
    public List<AdminUserDto> getUsers() {
        return userService.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/users")
    @ResponseBody
    public AdminUserDto createUser(
            @RequestBody AdminUserCreateRequest request) {
        return userService.createUser(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/users/{userId}")
    @ResponseBody
    public AdminUserDto updateUser(
            @PathVariable String userId,
            @RequestBody AdminUserUpdateRequest request) {
        return userService.updateUser(userId, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/users/{userId}")
    @ResponseBody
    public void deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
    }
}
