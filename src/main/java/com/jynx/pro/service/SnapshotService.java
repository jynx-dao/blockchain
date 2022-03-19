package com.jynx.pro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.*;
import com.jynx.pro.utils.UUIDUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class SnapshotService {

    @Autowired
    private ConfigService configService;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AuctionTriggerRepository auctionTriggerRepository;
    @Autowired
    private BlockValidatorRepository blockValidatorRepository;
    @Autowired
    private BridgeUpdateRepository bridgeUpdateRepository;
    @Autowired
    private BridgeUpdateSignatureRepository bridgeUpdateSignatureRepository;
    @Autowired
    private ConfigRepository configRepository;
    @Autowired
    private DelegationRepository delegationRepository;
    @Autowired
    private DepositRepository depositRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private MarketRepository marketRepository;
    @Autowired
    private OracleRepository oracleRepository;
    @Autowired
    private OrderHistoryRepository orderHistoryRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PendingAuctionTriggerRepository pendingAuctionTriggerRepository;
    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private ProposalRepository proposalRepository;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private StakeRepository stakeRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ValidatorRepository validatorRepository;
    @Autowired
    private VoteRepository voteRepository;
    @Autowired
    private WithdrawalBatchRepository withdrawalBatchRepository;
    @Autowired
    private WithdrawalBatchSignatureRepository withdrawalBatchSignatureRepository;
    @Autowired
    private WithdrawalRepository withdrawalRepository;
    @Autowired
    private SnapshotRepository snapshotRepository;
    @Autowired
    private SnapshotChunkRepository snapshotChunkRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UUIDUtils uuidUtils;

    @Data
    @Accessors(chain = true)
    private static class EntityConfig<T> {
        private EntityRepository<T> repository;
        private Class<T> type;
    }

    private static final List<EntityConfig<?>> entityConfig = new ArrayList<>();

    @PostConstruct
    private void initializeConfig() {
        entityConfig.add(new EntityConfig<Config>()
                .setRepository(configRepository)
                .setType(Config.class));
        entityConfig.add(new EntityConfig<User>()
                .setRepository(userRepository)
                .setType(User.class));
        entityConfig.add(new EntityConfig<Asset>()
                .setRepository(assetRepository)
                .setType(Asset.class));
        entityConfig.add(new EntityConfig<Account>()
                .setRepository(accountRepository)
                .setType(Account.class));
        entityConfig.add(new EntityConfig<Oracle>()
                .setRepository(oracleRepository)
                .setType(Oracle.class));
        entityConfig.add(new EntityConfig<Market>()
                .setRepository(marketRepository)
                .setType(Market.class));
        entityConfig.add(new EntityConfig<AuctionTrigger>()
                .setRepository(auctionTriggerRepository)
                .setType(AuctionTrigger.class));
        entityConfig.add(new EntityConfig<Validator>()
                .setRepository(validatorRepository)
                .setType(Validator.class));
        entityConfig.add(new EntityConfig<BlockValidator>()
                .setRepository(blockValidatorRepository)
                .setType(BlockValidator.class));
        entityConfig.add(new EntityConfig<BridgeUpdate>()
                .setRepository(bridgeUpdateRepository)
                .setType(BridgeUpdate.class));
        entityConfig.add(new EntityConfig<BridgeUpdateSignature>()
                .setRepository(bridgeUpdateSignatureRepository)
                .setType(BridgeUpdateSignature.class));
        entityConfig.add(new EntityConfig<Stake>()
                .setRepository(stakeRepository)
                .setType(Stake.class));
        entityConfig.add(new EntityConfig<Delegation>()
                .setRepository(delegationRepository)
                .setType(Delegation.class));
        entityConfig.add(new EntityConfig<Event>()
                .setRepository(eventRepository)
                .setType(Event.class));
        entityConfig.add(new EntityConfig<Deposit>()
                .setRepository(depositRepository)
                .setType(Deposit.class));
        entityConfig.add(new EntityConfig<Order>()
                .setRepository(orderRepository)
                .setType(Order.class));
        entityConfig.add(new EntityConfig<Trade>()
                .setRepository(tradeRepository)
                .setType(Trade.class));
        entityConfig.add(new EntityConfig<OrderHistory>()
                .setRepository(orderHistoryRepository)
                .setType(OrderHistory.class));
        entityConfig.add(new EntityConfig<PendingAuctionTrigger>()
                .setRepository(pendingAuctionTriggerRepository)
                .setType(PendingAuctionTrigger.class));
        entityConfig.add(new EntityConfig<Position>()
                .setRepository(positionRepository)
                .setType(Position.class));
        entityConfig.add(new EntityConfig<Proposal>()
                .setRepository(proposalRepository)
                .setType(Proposal.class));
        entityConfig.add(new EntityConfig<Settlement>()
                .setRepository(settlementRepository)
                .setType(Settlement.class));
        entityConfig.add(new EntityConfig<Transaction>()
                .setRepository(transactionRepository)
                .setType(Transaction.class));
        entityConfig.add(new EntityConfig<Vote>()
                .setRepository(voteRepository)
                .setType(Vote.class));
        entityConfig.add(new EntityConfig<WithdrawalBatch>()
                .setRepository(withdrawalBatchRepository)
                .setType(WithdrawalBatch.class));
        entityConfig.add(new EntityConfig<WithdrawalBatchSignature>()
                .setRepository(withdrawalBatchSignatureRepository)
                .setType(WithdrawalBatchSignature.class));
        entityConfig.add(new EntityConfig<Withdrawal>()
                .setRepository(withdrawalRepository)
                .setType(Withdrawal.class));
    }

    /**
     * Capture the latest state snapshot
     *
     * @param blockHeight the current block height
     */
    public void capture(
            final long blockHeight
    ) {
        int hashChain = 0;
        Snapshot snapshot = new Snapshot()
                .setId(uuidUtils.next())
                .setBlockHeight(blockHeight)
                .setFormat(1);
        snapshot = snapshotRepository.save(snapshot);
        hashChain = saveEntity(Account.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Asset.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(AuctionTrigger.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(BlockValidator.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(BridgeUpdate.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(BridgeUpdateSignature.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Config.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Delegation.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Deposit.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Event.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Market.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Oracle.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Order.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(OrderHistory.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(PendingAuctionTrigger.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Position.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Proposal.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Settlement.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Stake.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Trade.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Transaction.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(User.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Validator.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Vote.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(Withdrawal.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(WithdrawalBatch.class, hashChain, blockHeight, snapshot);
        hashChain = saveEntity(WithdrawalBatchSignature.class, hashChain, blockHeight, snapshot);
        String hash = DigestUtils.sha3_256Hex(BigInteger.valueOf(hashChain).toByteArray());
        snapshot.setHash(hash);
        snapshotRepository.save(snapshot);
        saveHash(hash, blockHeight);
    }

    /**
     * Save all items from an entity to a JSON file
     *
     * @param type {@link Class<T>}
     * @param hashChain used to assert consistency of state
     * @param blockHeight the current block height
     * @param snapshot {@link Snapshot}
     * @param <T> the entity type
     */
    private <T> int saveEntity(
            final Class<T> type,
            int hashChain,
            final long blockHeight,
            final Snapshot snapshot
    ) {
        try {
            List<T> items = readOnlyRepository.getAllByEntity(type);
            List<List<T>> chunks = Lists.partition(items, configService.getStatic().getSnapshotChunkRows());
            int idx = 0;
            List<SnapshotChunk> snapshotChunks = new ArrayList<>();
            for(List<T> chunk : chunks) {
                String json = objectMapper.writeValueAsString(chunk);
                String fileName = type.getCanonicalName();
                File userDirectory = FileUtils.getUserDirectory();
                String baseDir = String.format("%s/.jynx/snapshots/height_%s", userDirectory.toPath(), blockHeight);
                Files.createDirectories(Paths.get(baseDir));
                FileWriter fw = new FileWriter(String.format("%s/%s.%s.json", baseDir, fileName, idx), true);
                fw.append(json);
                fw.close();
                int chunkHashCode = chunk.hashCode();
                String chunkHash = DigestUtils.sha3_256Hex(BigInteger.valueOf(chunkHashCode).toByteArray());
                SnapshotChunk snapshotChunk = new SnapshotChunk()
                        .setSnapshot(snapshot)
                        .setId(uuidUtils.next())
                        .setChunkIndex(idx)
                        .setFileName(fileName)
                        .setHash(chunkHash);
                snapshotChunks.add(snapshotChunk);
                snapshot.setTotalChunks(snapshot.getTotalChunks() + 1);
                hashChain = List.of(hashChain, chunkHashCode).hashCode();
                idx++;
            }
            snapshotChunkRepository.saveAll(snapshotChunks);
            return hashChain;
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            return hashChain;
        }
    }

    /**
     * Save the hash, used to verify consistency of state
     *
     * @param hash the state hash
     * @param blockHeight the current block height
     */
    private void saveHash(
            final String hash,
            final long blockHeight
    ) {
        try {
            File userDirectory = FileUtils.getUserDirectory();
            String baseDir = String.format("%s/.jynx/snapshots/height_%s", userDirectory.toPath(), blockHeight);
            Files.createDirectories(Paths.get(baseDir));
            FileWriter fw = new FileWriter(String.format("%s/hash.json", baseDir), true);
            fw.append(hash);
            fw.close();
        } catch(Exception e) {
            log.error(e.getMessage(), e);
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

    /**
     * Load state from snapshot
     */
    public void load(
            final long blockHeight
    ) {
        File userDirectory = FileUtils.getUserDirectory();
        String baseDir = String.format("%s/.jynx/snapshots/height_%s", userDirectory.toPath(), blockHeight);
        boolean exists = Files.exists(Paths.get(baseDir));
        if(exists) {
            int[] hashChain = {0};
            List<EntityConfig<?>> invertedEntityConfig = new ArrayList<>(entityConfig);
            Collections.reverse(invertedEntityConfig);
            invertedEntityConfig.forEach(c -> c.getRepository().deleteAll());
            entityConfig.forEach(c -> hashChain[0] = loadEntity(c, blockHeight, hashChain[0]));
            String hash = DigestUtils.sha3_256Hex(BigInteger.valueOf(hashChain[0]).toByteArray());
            verifyHash(hash, blockHeight);
        }
    }

    /**
     * Verify that the hash matches
     *
     * @param hash the hash
     * @param blockHeight the current block height
     */
    private void verifyHash(
            final String hash,
            final long blockHeight
    ) {
        try {
            File hashFile = new File(String.format("%s/hash.json", getBaseDir(blockHeight)));
            String hashFromFile = FileUtils.readFileToString(hashFile, StandardCharsets.UTF_8);
            if(!hash.equals(hashFromFile)) {
                throw new JynxProException(ErrorCode.SNAPSHOT_HASH_MISMATCH);
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.SNAPSHOT_HASH_MISMATCH);
        }
    }

    /**
     * Load entity from snapshot file
     *
     * @param config {@link EntityConfig<T>}
     * @param blockHeight the current block height
     * @param hashChain used to verify state consistency
     * @param <T> the entity type
     */
    private <T> int loadEntity(
            final EntityConfig<T> config,
            final long blockHeight,
            final int hashChain
    ) {
        try {
            File file = new File(String.format("%s/%s.json", getBaseDir(blockHeight),
                    config.getType().getCanonicalName()));
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            List<T> items = objectMapper.readValue(content, new TypeReference<>() {});
            config.getRepository().saveAll(items);
            return List.of(hashChain, items.hashCode()).hashCode();
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get base snapshot directory
     *
     * @param blockHeight the current block height
     *
     * @return the directory
     */
    public String getBaseDir(
            final long blockHeight
    ) {
        File userDirectory = FileUtils.getUserDirectory();
        return String.format("%s/.jynx/snapshots/height_%s", userDirectory.toPath(), blockHeight);
    }
}