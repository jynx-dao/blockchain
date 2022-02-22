package com.jynx.pro.repository;

import com.jynx.pro.constant.WithdrawalStatus;
import com.jynx.pro.entity.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {
    List<Withdrawal> findByStatus(WithdrawalStatus status);
}