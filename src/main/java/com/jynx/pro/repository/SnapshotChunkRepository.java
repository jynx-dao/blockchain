package com.jynx.pro.repository;

import com.jynx.pro.entity.SnapshotChunk;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotChunkRepository extends EntityRepository<SnapshotChunk> {
    @Override
    public Class<SnapshotChunk> getType() {
        return SnapshotChunk.class;
    }
}