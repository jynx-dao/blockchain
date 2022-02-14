package com.jynx.pro.repository;

import com.jynx.pro.entity.Oracle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OracleRepository extends JpaRepository<Oracle, UUID> {
}