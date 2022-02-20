package com.jynx.pro.repository;

import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Transaction;
import com.jynx.pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByUserAndAsset(User makerUser, Asset settlementAsset);
}