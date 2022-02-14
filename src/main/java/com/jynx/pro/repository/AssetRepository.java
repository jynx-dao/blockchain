package com.jynx.pro.repository;

import com.jynx.pro.constant.AssetType;
import com.jynx.pro.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByAddressAndType(String address, AssetType type);
}