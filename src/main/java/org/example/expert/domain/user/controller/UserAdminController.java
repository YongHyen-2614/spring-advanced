package org.example.expert.domain.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.expert.domain.user.dto.request.UserRoleChangeRequest;
import org.example.expert.domain.user.service.UserAdminService;
import org.example.expert.logging.AdminApiLog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @PatchMapping("/admin/users/{userId}")
    @AdminApiLog
    public ResponseEntity<String> changeUserRole(@PathVariable long userId,
                                                 @Valid @RequestBody UserRoleChangeRequest request) {
        userAdminService.changeUserRole(userId, request);
        return ResponseEntity.ok("변경이 성공적으로 완료되었습니다.");
    }
}
