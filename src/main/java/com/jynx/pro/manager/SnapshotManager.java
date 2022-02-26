package com.jynx.pro.manager;

import com.jynx.pro.repository.AccountRepository;
import com.jynx.pro.repository.AssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SnapshotManager {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AssetRepository assetRepository;

    public void generate() {
        // TODO - generate snapshot files
    }

    public void load() {
        // TODO - load data from snapshot files
    }
}