package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.dto.LoginDTO;
import com.gjq.dto.RegisterDTO;
import com.gjq.entity.User;
import com.gjq.service.UserService;
import com.gjq.service.support.RequestAuthorizationService;
import com.gjq.utils.JwtUtil;
import com.gjq.vo.LoginResponseVO;
import com.gjq.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RequestAuthorizationService requestAuthorizationService;

    public UserController(UserService userService,
                          JwtUtil jwtUtil,
                          RequestAuthorizationService requestAuthorizationService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @PostMapping("/login")
    public Result<LoginResponseVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        User user = userService.login(loginDTO.getUsername(), loginDTO.getPassword());
        if (user == null) {
            return Result.error("用户名或密码错误");
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        LoginResponseVO response = new LoginResponseVO();
        response.setToken(jwtUtil.generateToken(user.getId(), user.getUsername()));
        response.setTokenType("Bearer");
        response.setUser(userVO);
        return Result.success(response);
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        User user = userService.register(registerDTO);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return Result.success(userVO);
    }

    @GetMapping("/info/{id}")
    public Result<UserVO> getUserInfo(@PathVariable Long id, HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, id);
        User user = userService.getUserInfo(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return Result.success(userVO);
    }

    @GetMapping("/list")
    public Result<List<UserVO>> getUserList(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        List<UserVO> users = userService.list().stream().map(user -> {
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            return userVO;
        }).collect(Collectors.toList());
        return Result.success(users);
    }

    @PostMapping
    public Result<UserVO> createUser(@RequestBody User user, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        User created = userService.createUser(user);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(created, userVO);
        return Result.success(userVO);
    }

    @PutMapping("/{id}")
    public Result<UserVO> updateUserById(@PathVariable Long id,
                                         @RequestBody User user,
                                         HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        user.setId(id);
        User updated = userService.updateUser(user);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(updated, userVO);
        return Result.success(userVO);
    }

    @PutMapping("/{id}/status")
    public Result<String> updateUserStatus(@PathVariable Long id,
                                           @RequestBody User user,
                                           HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        userService.updateStatus(id, user.getStatus());
        return Result.success("状态更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        userService.removeById(id);
        return Result.success("删除成功");
    }

    @PutMapping("/update")
    public Result<String> updateUser(@RequestBody User user, HttpServletRequest request) {
        if (user.getId() == null) {
            user.setId(requestAuthorizationService.requireCurrentUser(request).getId());
        }
        User currentUser = requestAuthorizationService.requireUserAccess(request, user.getId());
        if (!requestAuthorizationService.isAdmin(currentUser)) {
            user.setRole(null);
            user.setStatus(null);
            user.setPassword(null);
            user.setUsername(null);
        }
        userService.updateUser(user);
        return Result.success("更新成功");
    }
}
