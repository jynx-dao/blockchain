package com.jynx.pro.service;

import com.jynx.pro.entity.User;
import com.jynx.pro.repository.UserRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UUIDUtils uuidUtils;

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