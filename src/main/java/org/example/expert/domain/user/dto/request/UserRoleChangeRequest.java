package org.example.expert.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleChangeRequest {

    @NotNull(message = "역할은 반드시 입력해야합니다.")
    private String role;
}
