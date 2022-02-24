package com.jynx.pro.repository;

import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.User;
import com.jynx.pro.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByUserAndAsset(User user, Asset asset);
    List<Account> findByAssetAndAvailableBalanceGreaterThan(Asset asset, BigDecimal availableBalance);
    List<Account> findByUser(User user);
}