package com.jynx.pro.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

@Data
@Entity
@Table(name = "jynx_snapshot")
@Accessors(chain = true)
public class Snapshot {
    @Id
    private UUID id;
    @Column(name = "block_height", nullable = false)
    private Long blockHeight;
    @Column(name = "format", nullable = false)
    private Integer format;
    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks = 0;
    @Column(name = "hash")
    private String hash;
}