package com.jynx.pro.repository;

import com.jynx.pro.entity.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {
}