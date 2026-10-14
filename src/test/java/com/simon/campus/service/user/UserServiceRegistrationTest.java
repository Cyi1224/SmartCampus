package com.simon.campus.service.user;

import com.simon.campus.common.JwtUtil;
import com.simon.campus.mapper.UserMapper;
import com.simon.campus.model.dto.RegisterRequest;
import com.simon.campus.model.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceRegistrationTest {
    @Test
    void publicRegistrationAlwaysCreatesStudent() {
        UserMapper users = mock(UserMapper.class);
        JwtUtil jwt = mock(JwtUtil.class);
        when(users.selectCount(any())).thenReturn(0L);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("sample");
        request.setEmail("sample@example.com");
        request.setPassword("sample-password");
        request.setRole("ADMIN");

        new UserServiceImpl(users, jwt).register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).insert(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("STUDENT");
    }
}
