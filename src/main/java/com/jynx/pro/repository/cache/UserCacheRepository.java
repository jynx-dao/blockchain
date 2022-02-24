package com.jynx.pro.repository.cache;

import com.jynx.pro.entity.User;
import com.jynx.pro.entity.UserCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCacheRepository extends JpaRepository<UserCache, UUID> {
}