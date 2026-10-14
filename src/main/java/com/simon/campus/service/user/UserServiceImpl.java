package com.simon.campus.service.user;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.common.BizException;
import com.simon.campus.common.JwtUtil;
import com.simon.campus.mapper.UserMapper;
import com.simon.campus.model.dto.LoginRequest;
import com.simon.campus.model.dto.RegisterRequest;
import com.simon.campus.model.entity.User;
import com.simon.campus.model.enums.UserRole;
import com.simon.campus.model.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse register(RegisterRequest request) {
        // 校验用户名和邮箱唯一性
        long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, request.getUsername())
            .or().eq(User::getEmail, request.getEmail())
        );
        if (count > 0) {
            throw new BizException(400, "用户名或邮箱已被注册");
        }

        // Public registration must never accept a client-selected privileged role.
        String role = UserRole.STUDENT.name();

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(SecureUtil.sha256(request.getPassword()));
        user.setRole(role);
        user.setNickname(StringUtils.hasText(request.getNickname())
            ? request.getNickname() : request.getUsername());
        user.setStatus(1);
        userMapper.insert(user);

        return buildResponse(user, jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsernameOrEmail(request.getUsername());
        if (user == null || !SecureUtil.sha256(request.getPassword()).equals(user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BizException(403, "账号已被禁用，请联系管理员");
        }

        return buildResponse(user, jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(404, "用户不存在");
        if (!SecureUtil.sha256(oldPassword).equals(user.getPassword())) {
            throw new BizException(400, "原密码错误");
        }
        if (SecureUtil.sha256(newPassword).equals(user.getPassword())) {
            throw new BizException(400, "新密码不能与原密码相同");
        }
        user.setPassword(SecureUtil.sha256(newPassword));
        userMapper.updateById(user);
    }

    private LoginResponse buildResponse(User user, String token) {
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        return resp;
    }
}
