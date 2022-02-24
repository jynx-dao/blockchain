package com.jynx.pro.service.cache;

import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.cache.UserCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UserCacheService {

    @Autowired
    private UserCacheRepository userCacheRepository;

    public User getById(
            final UUID id
    ) {
        return userCacheRepository.findById(id).orElseThrow(() -> new JynxProException(ErrorCode.USER_NOT_FOUND));
    }
}
