package com.jynx.pro.service;

import com.jynx.pro.constant.BridgeUpdateType;
import com.jynx.pro.entity.BridgeUpdate;
import com.jynx.pro.entity.BridgeUpdateSignature;
import com.jynx.pro.entity.Validator;
import com.jynx.pro.repository.BridgeUpdateRepository;
import com.jynx.pro.repository.BridgeUpdateSignatureRepository;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.repository.ValidatorRepository;
import com.jynx.pro.request.BulkSignBridgeUpdateRequest;
import com.jynx.pro.request.ExecuteBridgeUpdatesRequest;
import com.jynx.pro.request.SignBridgeUpdateRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BridgeUpdateService {

    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private BridgeUpdateRepository bridgeUpdateRepository;
    @Autowired
    private BridgeUpdateSignatureRepository bridgeUpdateSignatureRepository;
    @Autowired
    private ValidatorRepository validatorRepository;
    @Autowired
    private UUIDUtils uuidUtils;
    @Autowired
    private ConfigService configService;

    /**
     * Get unprocessed bridge updates
     *
     * @return {@link List<BridgeUpdate>}
     */
    public List<BridgeUpdate> getUnprocessedBridgeUpdates() {
        return readOnlyRepository.getAllByEntity(BridgeUpdate.class).stream()
                .filter(w -> !w.getComplete()).collect(Collectors.toList());
    }

    /**
     * Save signatures for a {@link BridgeUpdate}
     *
     * @param request {@link BulkSignBridgeUpdateRequest}
     *
     * @return {@link List<BridgeUpdate>}
     */
    public List<BridgeUpdate> saveBridgeUpdateSignatures(
            final BulkSignBridgeUpdateRequest request
    ) {
        List<BridgeUpdate> updates = new ArrayList<>();
        for(SignBridgeUpdateRequest signature : request.getSignatures()) {
            Optional<BridgeUpdate> bridgeUpdateOptional = bridgeUpdateRepository
                    .findById(signature.getBridgeUpdateId());
            Optional<Validator> validatorOptional = validatorRepository.findById(signature.getValidatorId());
            if(bridgeUpdateOptional.isPresent() && validatorOptional.isPresent()) {
                Validator validator = validatorOptional.get();
                BridgeUpdate update = bridgeUpdateOptional.get();
                Optional<BridgeUpdateSignature> signatureOptional = bridgeUpdateSignatureRepository
                        .findByBridgeUpdateIdAndValidatorId(update.getId(), validator.getId());
                if(signatureOptional.isEmpty()) {
                    BridgeUpdateSignature updateSignature = new BridgeUpdateSignature()
                            .setSignature(signature.getSignature())
                            .setBridgeUpdate(update)
                            .setId(uuidUtils.next())
                            .setValidator(validator);
                    bridgeUpdateSignatureRepository.save(updateSignature);
                }
            }
        }
        return updates;
    }

    /**
     * Sign incomplete {@link BridgeUpdate}s
     *
     * @param publicKey validator's public key
     *
     * @return {@link BulkSignBridgeUpdateRequest}
     */
    public BulkSignBridgeUpdateRequest signUpdates(
            final String publicKey
    ) {
        BulkSignBridgeUpdateRequest request = new BulkSignBridgeUpdateRequest();
        List<BridgeUpdate> bridgeUpdates = readOnlyRepository.getAllByEntity(BridgeUpdate.class)
                .stream().filter(u -> !u.getComplete()).collect(Collectors.toList());
        Optional<Validator> validatorOptional = readOnlyRepository.getValidatorByPublicKey(publicKey);
        if(validatorOptional.isPresent()) {
            Validator validator = validatorOptional.get();
            for (BridgeUpdate bridgeUpdate : bridgeUpdates) {
                Optional<BridgeUpdateSignature> signatureOptional = readOnlyRepository
                        .getSignatureByBridgeUpdateIdAndValidatorId(bridgeUpdate.getId(), validator.getId());
                if(signatureOptional.isEmpty()) {
                    byte[] signature = new byte[]{0};
                    if(bridgeUpdate.getType().equals(BridgeUpdateType.ADD_ASSET)) {
                        try {
                            signature = ethereumService.getSignatureForAddAsset(
                                    bridgeUpdate.getAsset().getAddress(), new BigInteger(bridgeUpdate.getNonce()));
                        } catch(Exception e) {
                            log.error(e.getMessage(), e);
                            continue;
                        }
                    } else if(bridgeUpdate.getType().equals(BridgeUpdateType.REMOVE_ASSET)) {
                        try {
                            signature = ethereumService.getSignatureForRemoveAsset(
                                    bridgeUpdate.getAsset().getAddress(), new BigInteger(bridgeUpdate.getNonce()));
                        } catch(Exception e) {
                            log.error(e.getMessage(), e);
                            continue;
                        }
                    }
                    request.getSignatures().add(new SignBridgeUpdateRequest()
                            .setBridgeUpdateId(bridgeUpdate.getId())
                            .setValidatorId(validator.getId())
                            .setSignature(Hex.encodeHexString(signature)));
                }
            }
        }
        return request;
    }

    /**
     * Execute bridge updates
     *
     * @param request {@link ExecuteBridgeUpdatesRequest}
     *
     * @return {@link List<BridgeUpdate>}
     */
    public List<BridgeUpdate> executeBridgeUpdates(
            final ExecuteBridgeUpdatesRequest request
    ) {
        List<BridgeUpdate> updates = bridgeUpdateRepository.findAll();
        for(UUID id : request.getUpdateIds()) {
            updates.stream().filter(u -> u.getId().equals(id)).findFirst().ifPresent(u -> u.setComplete(true));
        }
        bridgeUpdateRepository.saveAll(updates);
        return updates;
    }

    /**
     * Processed signed bridge updates
     *
     * @param updates {@link List<BridgeUpdate>}
     */
    public void processSignedUpdates(
            final List<BridgeUpdate> updates
    ) {
        for(BridgeUpdate update : updates) {
            List<BridgeUpdateSignature> updateSignatures = readOnlyRepository
                    .getSignatureByBridgeUpdateId(update.getId());
            List<Validator> validators = readOnlyRepository.getAllByEntity(Validator.class);
            double threshold = validators.size() * configService.getStatic()
                    .getValidatorSigningThreshold().doubleValue();
            if(updateSignatures.size() >= threshold) {
                BigInteger nonce = new BigInteger(update.getNonce());
                ByteArrayOutputStream signatureStream = new ByteArrayOutputStream();
                for(BridgeUpdateSignature updateSignature : updateSignatures) {
                    try {
                        signatureStream.write(Hex.decodeHex(updateSignature.getSignature()));
                    } catch(Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
                byte[] signature = signatureStream.toByteArray();
                try {
                    if(!ethereumService.isNonceUsed(nonce.toString())) {
                        if (update.getType().equals(BridgeUpdateType.ADD_ASSET)) {
                            ethereumService.addAsset(update.getAsset().getAddress(), nonce, signature);
                        } else if (update.getType().equals(BridgeUpdateType.REMOVE_ASSET)) {
                            ethereumService.removeAsset(update.getAsset().getAddress(), nonce, signature);
                        }
                    }
                } catch(Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
}