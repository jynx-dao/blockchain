package com.jynx.pro.repository;

import com.jynx.pro.entity.Snapshot;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotRepository extends EntityRepository<Snapshot> {
    // TODO - this needs to use a separate database transaction from the main blockchain thread
    @Override
    public Class<Snapshot> getType() {
        return Snapshot.class;
    }
}