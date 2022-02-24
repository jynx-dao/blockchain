package com.jynx.pro.service.cache;

import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.AccountCache;
import com.jynx.pro.repository.AccountRepository;
import com.jynx.pro.repository.cache.AccountCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountCacheService {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AccountCacheRepository accountCacheRepository;
    @Autowired
    private UserCacheService userCacheService;

    public List<Account> getByUserId(
            final UUID userId
    ) {
        return accountCacheRepository.findByUser(userCacheService.getById(userId));
    }

    public void update() {
        accountCacheRepository.deleteAll();
        accountCacheRepository.saveAll(accountRepository.findAll()
                .stream().map(a -> (AccountCache)a).collect(Collectors.toList()));
    }
}
