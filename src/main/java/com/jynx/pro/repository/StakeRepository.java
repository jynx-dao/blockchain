package com.jynx.pro.repository;

import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StakeRepository extends JpaRepository<Stake, UUID> {
    Optional<Stake> findByUser(User user);
}