package com.jynx.pro.repository;

import com.jynx.pro.entity.Delegation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DelegationRepository extends JpaRepository<Delegation, UUID> {
}