package com.jynx.pro.repository;

import com.jynx.pro.entity.Validator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ValidatorRepository extends JpaRepository<Validator, UUID> {
}