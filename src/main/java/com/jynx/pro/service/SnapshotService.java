package com.jynx.pro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jynx.pro.entity.*;
import com.jynx.pro.repository.ReadOnlyRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
public class SnapshotService {

    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Capture the latest state snapshot
     *
     * @param blockHeight the current block height
     */
    public void capture(
            final long blockHeight
    ) {
        int hashChain = 0;
        hashChain = saveEntity(Account.class, hashChain, blockHeight);
        hashChain = saveEntity(Asset.class, hashChain, blockHeight);
        hashChain = saveEntity(AuctionTrigger.class, hashChain, blockHeight);
        hashChain = saveEntity(BlockValidator.class, hashChain, blockHeight);
        hashChain = saveEntity(BridgeUpdate.class, hashChain, blockHeight);
        hashChain = saveEntity(BridgeUpdateSignature.class, hashChain, blockHeight);
        hashChain = saveEntity(Config.class, hashChain, blockHeight);
        hashChain = saveEntity(Delegation.class, hashChain, blockHeight);
        hashChain = saveEntity(Deposit.class, hashChain, blockHeight);
        hashChain = saveEntity(Event.class, hashChain, blockHeight);
        hashChain = saveEntity(Market.class, hashChain, blockHeight);
        hashChain = saveEntity(Oracle.class, hashChain, blockHeight);
        hashChain = saveEntity(Order.class, hashChain, blockHeight);
        hashChain = saveEntity(OrderHistory.class, hashChain, blockHeight);
        hashChain = saveEntity(PendingAuctionTrigger.class, hashChain, blockHeight);
        hashChain = saveEntity(Position.class, hashChain, blockHeight);
        hashChain = saveEntity(Proposal.class, hashChain, blockHeight);
        hashChain = saveEntity(Settlement.class, hashChain, blockHeight);
        hashChain = saveEntity(Stake.class, hashChain, blockHeight);
        hashChain = saveEntity(Trade.class, hashChain, blockHeight);
        hashChain = saveEntity(Transaction.class, hashChain, blockHeight);
        hashChain = saveEntity(User.class, hashChain, blockHeight);
        hashChain = saveEntity(Validator.class, hashChain, blockHeight);
        hashChain = saveEntity(Vote.class, hashChain, blockHeight);
        hashChain = saveEntity(Withdrawal.class, hashChain, blockHeight);
        hashChain = saveEntity(WithdrawalBatch.class, hashChain, blockHeight);
        hashChain = saveEntity(WithdrawalBatchSignature.class, hashChain, blockHeight);
        String hash = DigestUtils.sha3_256Hex(BigInteger.valueOf(hashChain).toByteArray());
        // TODO - save the hashChain
        log.info("{}", hash);
    }

    /**
     * Save all items from an entity to a JSON file
     *
     * @param type {@link Class<T>}
     * @param hashChain used to assert consistency of state
     * @param blockHeight the current block height
     * @param <T> the entity type
     */
    private <T> int saveEntity(
            final Class<T> type,
            final int hashChain,
            final long blockHeight
    ) {
        try {
            List<T> items = readOnlyRepository.getAllByEntity(type);
            String json = objectMapper.writeValueAsString(items);
            String fileName = type.getCanonicalName();
            File userDirectory = FileUtils.getUserDirectory();
            String baseDir = String.format("%s/.jynx/snapshots/height_%s", userDirectory.toPath(), blockHeight);
            Files.createDirectories(Paths.get(baseDir));
            FileWriter fw = new FileWriter(String.format("%s/%s.json", baseDir, fileName), true);
            fw.append(json);
            fw.close();
            return List.of(hashChain, items.hashCode()).hashCode();
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            return hashChain;
        }
    }

    /**
     * Delete old snapshots when chain is restarted
     */
    public void initializeSnapshots() {
        try {
            File userDirectory = FileUtils.getUserDirectory();
            String baseDir = String.format("%s/.jynx/snapshots", userDirectory.toPath());
            FileUtils.deleteDirectory(new File(baseDir));
            Files.createDirectories(Paths.get(baseDir));
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}