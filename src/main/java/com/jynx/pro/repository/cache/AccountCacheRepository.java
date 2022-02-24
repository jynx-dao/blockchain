package com.jynx.pro.repository.cache;

import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.AccountCache;
import com.jynx.pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountCacheRepository extends JpaRepository<AccountCache, UUID> {
    List<Account> findByUser(User user);
}