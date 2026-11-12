package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.dto.LoginDTO;
import com.gjq.entity.User;
import com.gjq.service.UserService;
import com.gjq.service.support.RequestAuthorizationService;
import com.gjq.utils.JwtUtil;
import com.gjq.vo.LoginResponseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RequestAuthorizationService requestAuthorizationService;

    @InjectMocks
    private UserController userController;

    @Test
    void loginReturnsJwtPayloadWhenCredentialsAreValid() {
        LoginDTO request = new LoginDTO();
        request.setUsername("alice");
        request.setPassword("secret");

        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setRole("admin");

        when(userService.login("alice", "secret")).thenReturn(user);
        when(jwtUtil.generateToken(7L, "alice")).thenReturn("jwt-token");

        Result<LoginResponseVO> result = userController.login(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getToken()).isEqualTo("jwt-token");
        assertThat(result.getData().getTokenType()).isEqualTo("Bearer");
        assertThat(result.getData().getUser()).isNotNull();
        assertThat(result.getData().getUser().getId()).isEqualTo(7L);
        assertThat(result.getData().getUser().getUsername()).isEqualTo("alice");
        assertThat(result.getData().getUser().getRole()).isEqualTo("admin");
    }

    @Test
    void loginReturnsErrorWhenCredentialsAreInvalid() {
        LoginDTO request = new LoginDTO();
        request.setUsername("alice");
        request.setPassword("wrong");

        when(userService.login("alice", "wrong")).thenReturn(null);

        Result<LoginResponseVO> result = userController.login(request);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getData()).isNull();
        assertThat(result.getMessage()).isEqualTo("用户名或密码错误");
    }
}
