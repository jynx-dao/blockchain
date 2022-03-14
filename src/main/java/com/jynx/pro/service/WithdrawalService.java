package com.jynx.pro.service;

import com.jynx.pro.constant.WithdrawalStatus;
import com.jynx.pro.entity.*;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.BatchValidatorRequest;
import com.jynx.pro.request.BulkSignWithdrawalRequest;
import com.jynx.pro.request.DebitWithdrawalsRequest;
import com.jynx.pro.request.SignWithdrawalBatchRequest;
import com.jynx.pro.utils.PriceUtils;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WithdrawalService {

    @Autowired
    private WithdrawalRepository withdrawalRepository;
    @Autowired
    private WithdrawalBatchSignatureRepository withdrawalBatchSignatureRepository;
    @Autowired
    private WithdrawalBatchRepository withdrawalBatchRepository;
    @Autowired
    private ValidatorRepository validatorRepository;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private UUIDUtils uuidUtils;
    @Autowired
    private PriceUtils priceUtils;

    /**
     * Create a new {@link WithdrawalBatch}
     */
    public List<Withdrawal> batchWithdrawals(
            final BatchValidatorRequest request
    ) {
        log.debug(request.toString());
        Optional<WithdrawalBatch> batchOptional = withdrawalBatchRepository.findAll().stream()
                .max(Comparator.comparing(WithdrawalBatch::getCreated));
        long timeThreshold = configService.getTimestamp() - (60 * 60 * 4); // TODO - use config variable for withdrawal batch frequency
        long timeThresholdWithdrawal = configService.getTimestamp() - 60; // TODO - use config variable for withdrawal delay
        List<Withdrawal> withdrawals = new ArrayList<>();
        if(batchOptional.isEmpty() || batchOptional.get().getCreated() < timeThreshold) {
            withdrawals = withdrawalRepository.findByStatus(WithdrawalStatus.PENDING).stream()
                    .filter(w -> w.getWithdrawalBatch() == null)
                    .filter(w -> w.getCreated() < timeThresholdWithdrawal)
                    .collect(Collectors.toList());
            if(withdrawals.size() > 0) {
                WithdrawalBatch withdrawalBatch = new WithdrawalBatch()
                        .setCreated(configService.getTimestamp())
                        .setProcessed(false)
                        .setId(uuidUtils.next())
                        .setNonce(ethereumService.getNonce().toString());
                withdrawalBatch = withdrawalBatchRepository.save(withdrawalBatch);
                for (Withdrawal withdrawal : withdrawals) {
                    withdrawal.setWithdrawalBatch(withdrawalBatch);
                }
                withdrawalRepository.saveAll(withdrawals);
            }
        }
        return withdrawals;
    }

    /**
     * Save a signature for a {@link WithdrawalBatch}
     *
     * @param request {@link BulkSignWithdrawalRequest}
     *
     * @return {@link List<WithdrawalBatch>}
     */
    public List<WithdrawalBatch> saveWithdrawalBatchSignatures(
            final BulkSignWithdrawalRequest request
    ) {
        List<WithdrawalBatch> batches = new ArrayList<>();
        for(SignWithdrawalBatchRequest signature : request.getSignatures()) {
            Optional<WithdrawalBatch> withdrawalBatchOptional = withdrawalBatchRepository
                    .findById(signature.getWithdrawalBatchId());
            Optional<Validator> validatorOptional = validatorRepository.findById(signature.getValidatorId());
            if(withdrawalBatchOptional.isPresent() && validatorOptional.isPresent()) {
                Validator validator = validatorOptional.get();
                WithdrawalBatch batch = withdrawalBatchOptional.get();
                Optional<WithdrawalBatchSignature> signatureOptional = withdrawalBatchSignatureRepository
                        .findByWithdrawalBatchIdAndValidatorId(batch.getId(), validator.getId());
                if(signatureOptional.isEmpty()) {
                    WithdrawalBatchSignature batchSignature = new WithdrawalBatchSignature()
                            .setSignature(signature.getSignature())
                            .setWithdrawalBatch(batch)
                            .setId(uuidUtils.next())
                            .setValidator(validator);
                    withdrawalBatchSignatureRepository.save(batchSignature);
                }
            }
        }
        return batches;
    }

    /**
     * Sign withdrawal batches
     *
     * @param publicKey the validator's public key
     */
    public BulkSignWithdrawalRequest signBatches(
            final String publicKey
    ) {
        List<WithdrawalBatch> pendingBatches = readOnlyRepository.getAllByEntity(WithdrawalBatch.class).stream()
                .filter(w -> !w.getProcessed()).collect(Collectors.toList());
        Optional<Validator> validatorOptional = readOnlyRepository.getValidatorByPublicKey(publicKey);
        BulkSignWithdrawalRequest request = new BulkSignWithdrawalRequest();
        if(validatorOptional.isPresent()) {
            Validator validator = validatorOptional.get();
            for(WithdrawalBatch batch : pendingBatches) {
                List<Withdrawal> withdrawals = readOnlyRepository.getWithdrawalsByWithdrawalBatchId(batch.getId());
                if(withdrawals.size() > 0) {
                    Optional<WithdrawalBatchSignature> withdrawalSignatureOptional = readOnlyRepository
                            .getSignatureByWithdrawalBatchIdAndValidatorId(batch.getId(), validator.getId());
                    List<String> destinations = withdrawals.stream().map(Withdrawal::getDestination)
                            .collect(Collectors.toList());
                    List<BigInteger> amounts = withdrawals.stream().map(Withdrawal::getAmount).map(priceUtils::toBigInteger)
                            .collect(Collectors.toList());
                    List<String> assets = withdrawals.stream().map(Withdrawal::getAsset).map(Asset::getAddress)
                            .collect(Collectors.toList());
                    if(withdrawalSignatureOptional.isEmpty()) {
                        byte[] signature;
                        try {
                            signature = ethereumService.getSignatureForWithdrawal(
                                    destinations, amounts, assets, new BigInteger(batch.getNonce()));
                        } catch(Exception e) {
                            log.error(e.getMessage(), e);
                            continue;
                        }
                        request.getSignatures().add(new SignWithdrawalBatchRequest()
                                .setWithdrawalBatchId(batch.getId())
                                .setValidatorId(validator.getId())
                                .setSignature(Hex.encodeHexString(signature)));
                    }
                }
            }
        }
        return request;
    }

    /**
     * Get unprocessed withdrawal batches
     *
     * @return {@link List<WithdrawalBatch>}
     */
    public List<WithdrawalBatch> getUnprocessedWithdrawalBatches() {
        return readOnlyRepository.getAllByEntity(WithdrawalBatch.class).stream()
                .filter(w -> !w.getProcessed()).collect(Collectors.toList());
    }

    /**
     * Withdraw signed batches
     */
    public void withdrawSignedBatches(
            final List<WithdrawalBatch> batches
    ) {
        for(WithdrawalBatch batch : batches) {
            List<WithdrawalBatchSignature> batchSignatures = readOnlyRepository
                    .getSignatureByWithdrawalBatchId(batch.getId());
            List<Validator> validators = readOnlyRepository.getAllByEntity(Validator.class);
            double threshold = validators.size() * 0.667; // TODO - use config variable for signature threshold [??]
            if(batchSignatures.size() >= threshold) {
                List<Withdrawal> withdrawals = readOnlyRepository.getWithdrawalsByWithdrawalBatchId(batch.getId());
                List<String> destinations = withdrawals.stream().map(Withdrawal::getDestination)
                        .collect(Collectors.toList());
                List<BigInteger> amounts = withdrawals.stream().map(Withdrawal::getAmount).map(priceUtils::toBigInteger)
                        .collect(Collectors.toList());
                List<String> assets = withdrawals.stream().map(Withdrawal::getAsset).map(Asset::getAddress)
                        .collect(Collectors.toList());
                BigInteger nonce = new BigInteger(batch.getNonce());
                ByteArrayOutputStream signatureStream = new ByteArrayOutputStream();
                for(WithdrawalBatchSignature batchSignature : batchSignatures) {
                    try {
                        signatureStream.write(Hex.decodeHex(batchSignature.getSignature()));
                    } catch(Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
                byte[] signature = signatureStream.toByteArray();
                ethereumService.withdrawAssets(destinations, amounts, assets, nonce, signature);
            }
        }
    }

    /**
     * Update status of {@link Withdrawal} to debit
     *
     * @param request {@link DebitWithdrawalsRequest}
     *
     * @return {@link List<Withdrawal>}
     */
    public List<Withdrawal> debitWithdrawals(
            final DebitWithdrawalsRequest request
    ) {
        List<Withdrawal> withdrawals = withdrawalRepository.findAll().stream()
                .filter(w -> w.getStatus().equals(WithdrawalStatus.PENDING)).collect(Collectors.toList());
        List<Withdrawal> updatedWithdrawals = new ArrayList<>();
        for(UUID id : request.getBatchIds()) {
            Optional<WithdrawalBatch> withdrawalBatchOptional = withdrawalBatchRepository.findById(id);
            if(withdrawalBatchOptional.isPresent()) {
                withdrawalBatchOptional.get().setProcessed(true);
                withdrawalBatchRepository.save(withdrawalBatchOptional.get());
                List<Withdrawal> batchWithdrawals = withdrawals.stream()
                        .filter(w -> w.getWithdrawalBatch().getId().equals(id))
                        .collect(Collectors.toList());
                for (Withdrawal withdrawal : batchWithdrawals) {
                    withdrawal.setStatus(WithdrawalStatus.DEBITED);
                    updatedWithdrawals.add(withdrawal);
                }
            }
        }
        withdrawalRepository.saveAll(updatedWithdrawals);
        return updatedWithdrawals;
    }
}