package com.jynx.pro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.SnapshotContent;
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
import java.util.*;
import java.util.stream.Collectors;

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
     * Get the latest N snapshots
     *
     * @param limit N snapshots
     *
     * @return {@link List<Snapshot>}
     */
    public List<Snapshot> getLatestSnapshots(
            final long limit
    ) {
        return readOnlyRepository.getAllByEntity(Snapshot.class).stream()
                .sorted(Comparator.comparing(Snapshot::getBlockHeight).reversed())
                .limit(limit)
                .collect(Collectors.toList());
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
        initializeSnapshotDirectoryAtHeight(blockHeight);
        snapshot = snapshotRepository.save(snapshot);
        for(EntityConfig<?> config : entityConfig) {
            hashChain = saveEntity(config.getType(), hashChain, blockHeight, snapshot);
        }
        String hash = DigestUtils.sha3_256Hex(BigInteger.valueOf(hashChain).toByteArray());
        snapshot.setHash(hash);
        snapshotRepository.save(snapshot);
        saveHash(hash, blockHeight);
    }

    /**
     * Initialize the snapshot directory at specified block height
     *
     * @param blockHeight the block height
     */
    private void initializeSnapshotDirectoryAtHeight(
            final long blockHeight
    ) {
        try {
            File userDirectory = FileUtils.getUserDirectory();
            String baseDir = String.format("%s/.jynx/snapshots/height_%s", userDirectory.toPath(), blockHeight);
            FileUtils.deleteDirectory(new File(baseDir));
            Files.createDirectories(Paths.get(baseDir));
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
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
            List<SnapshotChunk> snapshotChunks = new ArrayList<>();
            for(List<T> chunk : chunks) {
                int idx = snapshot.getTotalChunks();
                String fileName = type.getCanonicalName();
                SnapshotContent<T> content = new SnapshotContent<T>()
                        .setEntityName(fileName)
                        .setData(chunk);
                String json = objectMapper.writeValueAsString(content);
                FileWriter fw = new FileWriter(String.format("%s/%s.%s.json", getBaseDir(blockHeight), idx, fileName), true);
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
                snapshot.setTotalChunks(snapshot.getTotalChunks() + 1);
                snapshotChunks.add(snapshotChunk);
                hashChain = List.of(hashChain, chunkHashCode).hashCode();
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
     * Empty the state
     */
    public void clearState() {
        List<EntityConfig<?>> invertedEntityConfig = new ArrayList<>(entityConfig);
        Collections.reverse(invertedEntityConfig);
        invertedEntityConfig.forEach(c -> c.getRepository().deleteAll());
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
    private String getBaseDir(
            final long blockHeight
    ) {
        File userDirectory = FileUtils.getUserDirectory();
        return String.format("%s/.jynx/snapshots/height_%s", userDirectory.toPath(), blockHeight);
    }

    /**
     * Get the raw data for a snapshot chunk at given block height and chunk index
     *
     * @param blockHeight the block height
     * @param chunkIndex the chunk index
     *
     * @return {@link Optional<String>}
     */
    public Optional<String> getChunkContent(
            final long blockHeight,
            final int chunkIndex
    ) {
        Optional<Snapshot> snapshotOptional = readOnlyRepository.getSnapshotByHeight(blockHeight);
        if(snapshotOptional.isPresent()) {
            Snapshot snapshot = snapshotOptional.get();
            Optional<SnapshotChunk> snapshotChunkOptional = readOnlyRepository
                    .getSnapshotChunksBySnapshotIdAndChunkIndex(snapshot.getId(), chunkIndex);
            if(snapshotChunkOptional.isPresent()) {
                SnapshotChunk snapshotChunk = snapshotChunkOptional.get();
                File chunkFile = new File(String.format("%s/%s.json",
                        getBaseDir(snapshot.getBlockHeight()), snapshotChunk.getFileName()));
                try {
                    String chunkContent = FileUtils.readFileToString(chunkFile, StandardCharsets.UTF_8);
                    return Optional.of(chunkContent);
                } catch(Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Save a snapshot chunk to the database
     *
     * @param content the snapshot chunk content
     */
    public <T> void saveChunk(
            final String content
    ) {
        try {
            SnapshotContent<T> snapshotContent = objectMapper.readValue(content, new TypeReference<>() {});
            Class<T> type = (Class<T>) Class.forName(snapshotContent.getEntityName());
            Gson gson = new Gson();
            List<T> data = snapshotContent.getData().stream()
                    .map(d -> gson.fromJson(gson.toJsonTree(d), type))
                    .collect(Collectors.toList());
            entityConfig.stream()
                    .filter(c -> c.getType().equals(type))
                    .findFirst()
                    .ifPresent(config -> ((EntityRepository<T>)config.getRepository()).saveAll(data));
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}