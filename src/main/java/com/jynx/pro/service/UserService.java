package com.jynx.pro.service;

import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.UserRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UUIDUtils uuidUtils;

    public User get(
            final UUID id
    ) {
        return userRepository.findById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.USER_NOT_FOUND));
    }

    public User getAndCreate(
            final String publicKey
    ) {
        User user = userRepository.findByPublicKey(publicKey)
                .orElse(new User()
                        .setId(uuidUtils.next())
                        .setPublicKey(publicKey)
                        .setReputationScore(1L)
                        .setUsername(publicKey));
        return userRepository.save(user);
    }
}