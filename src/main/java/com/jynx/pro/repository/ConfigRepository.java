package com.jynx.pro.repository;

import com.jynx.pro.entity.Config;
import org.springframework.stereotype.Repository;

@Repository
public class ConfigRepository extends EntityRepository<Config> {
    @Override
    public Class<Config> getType() {
        return Config.class;
    }
}