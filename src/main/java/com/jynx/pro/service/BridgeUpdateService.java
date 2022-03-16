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
import com.jynx.pro.request.SignBridgeUpdateRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                        .findSignatureByBridgeUpdateIdAndValidatorId(bridgeUpdate.getId(), validator.getId());
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
}