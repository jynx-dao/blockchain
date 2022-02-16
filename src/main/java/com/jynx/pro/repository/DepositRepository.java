package com.jynx.pro.repository;

import com.jynx.pro.entity.Deposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {
}