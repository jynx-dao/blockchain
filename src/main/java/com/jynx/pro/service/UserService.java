package com.jynx.pro.service;

import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User get(
            final UUID id
    ) {
        return userRepository.findById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.USER_NOT_FOUND));
    }
}