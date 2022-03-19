package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_snapshot_chunk")
@Accessors(chain = true)
public class SnapshotChunk {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private Snapshot snapshot;
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "hash", nullable = false)
    private String hash;
}