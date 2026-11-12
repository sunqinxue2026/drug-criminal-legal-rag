package com.gjq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.common.exception.BusinessException;
import com.gjq.dto.RegisterDTO;
import com.gjq.entity.User;
import com.gjq.mapper.UserMapper;
import com.gjq.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User login(String username, String password) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = this.getOne(wrapper);
        if (user == null) {
            return null;
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(403, "当前账号已被禁用");
        }
        if (user.getPassword() != null && user.getPassword().equals(password)) {
            return user;
        }
        return passwordEncoder.matches(password, user.getPassword()) ? user : null;
    }

    @Override
    public User register(RegisterDTO registerDTO) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, registerDTO.getUsername());
        if (this.getOne(wrapper) != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRealName(registerDTO.getRealName());
        user.setPhone(registerDTO.getPhone());
        user.setEmail(registerDTO.getEmail());
        user.setRole("user");
        user.setStatus(1);
        this.save(user);
        user.setPassword(null);
        return user;
    }

    @Override
    public User getUserInfo(Long userId) {
        return this.getById(userId);
    }

    @Override
    public User createUser(User user) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, user.getUsername());
        if (this.getOne(wrapper) != null) {
            throw new BusinessException("用户名已存在");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole("user");
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        this.save(user);
        user.setPassword(null);
        return user;
    }

    @Override
    public User updateUser(User user) {
        User existing = this.getById(user.getId());
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }

        existing.setRealName(user.getRealName());
        existing.setPhone(user.getPhone());
        existing.setEmail(user.getEmail());
        existing.setAvatar(user.getAvatar());
        if (user.getRole() != null && !user.getRole().trim().isEmpty()) {
            existing.setRole(user.getRole());
        }
        if (user.getStatus() != null) {
            existing.setStatus(user.getStatus());
        }
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        this.updateById(existing);
        existing.setPassword(null);
        return existing;
    }

    @Override
    public boolean updateStatus(Long userId, Integer status) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(status);
        return this.updateById(user);
    }
}
