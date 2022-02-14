package com.jynx.pro.repository;

import com.jynx.pro.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {
}