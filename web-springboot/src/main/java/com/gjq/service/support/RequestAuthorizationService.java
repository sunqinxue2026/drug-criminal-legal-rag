package com.gjq.service.support;

import com.gjq.common.exception.BusinessException;
import com.gjq.entity.User;
import com.gjq.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class RequestAuthorizationService {

    private static final String USER_ID_ATTR = "userId";
    private static final String ADMIN_ROLE = "admin";

    private final UserService userService;

    public RequestAuthorizationService(UserService userService) {
        this.userService = userService;
    }

    public User requireCurrentUser(HttpServletRequest request) {
        Object rawUserId = request.getAttribute(USER_ID_ATTR);
        if (!(rawUserId instanceof Number number)) {
            throw new BusinessException(401, "未授权，请先登录");
        }

        Long userId = number.longValue();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(401, "当前登录用户不存在或已失效");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(403, "当前账号已被禁用");
        }
        return user;
    }

    public User requireAdmin(HttpServletRequest request) {
        User currentUser = requireCurrentUser(request);
        if (!isAdmin(currentUser)) {
            throw new BusinessException(403, "当前操作需要管理员权限");
        }
        return currentUser;
    }

    public User requireUserAccess(HttpServletRequest request, Long targetUserId) {
        User currentUser = requireCurrentUser(request);
        if (canAccessUser(currentUser, targetUserId)) {
            return currentUser;
        }
        throw new BusinessException(403, "无权访问该用户的数据");
    }

    public Long resolveAccessibleUserId(HttpServletRequest request, Long requestedUserId) {
        User currentUser = requireCurrentUser(request);
        if (requestedUserId == null || !isAdmin(currentUser)) {
            return currentUser.getId();
        }
        return requestedUserId;
    }

    public boolean canAccessUser(User currentUser, Long targetUserId) {
        return isAdmin(currentUser) || Objects.equals(currentUser.getId(), targetUserId);
    }

    public boolean isAdmin(User user) {
        return user != null && ADMIN_ROLE.equalsIgnoreCase(user.getRole());
    }
}
