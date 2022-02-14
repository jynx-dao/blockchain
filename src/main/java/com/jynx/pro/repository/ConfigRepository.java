package com.jynx.pro.repository;

import com.jynx.pro.entity.Config;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConfigRepository extends JpaRepository<Config, UUID> {
}