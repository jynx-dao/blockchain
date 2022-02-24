package com.jynx.pro.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CacheService {

    @Autowired
    private AccountCacheService accountCacheService;

    public void update() {
        accountCacheService.update();
    }
}