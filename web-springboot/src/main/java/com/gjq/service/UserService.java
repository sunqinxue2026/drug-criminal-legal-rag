package com.gjq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.dto.RegisterDTO;
import com.gjq.entity.User;

public interface UserService extends IService<User> {
    User login(String username, String password);
    User register(RegisterDTO registerDTO);
    User getUserInfo(Long userId);
    User createUser(User user);
    User updateUser(User user);
    boolean updateStatus(Long userId, Integer status);
}
