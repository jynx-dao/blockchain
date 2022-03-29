package com.jynx.pro.repository;

import com.jynx.pro.entity.Snapshot;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotRepository extends EntityRepository<Snapshot> {
    @Override
    public Class<Snapshot> getType() {
        return Snapshot.class;
    }
}