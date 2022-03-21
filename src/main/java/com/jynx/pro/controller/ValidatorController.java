package com.jynx.pro.controller;

import com.jynx.pro.entity.Delegation;
import com.jynx.pro.entity.Validator;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.request.UpdateDelegationRequest;
import com.jynx.pro.request.ValidatorApplicationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/validator")
public class ValidatorController extends AbstractController {

    @GetMapping("/{id}")
    public ResponseEntity<Validator> getById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getValidatorById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.VALIDATOR_NOT_FOUND)));
    }

    @GetMapping
    public ResponseEntity<List<Validator>> getAll() {
        return ResponseEntity.ok(readOnlyRepository.getAllByEntity(Validator.class));
    }

    @PostMapping("/apply")
    public ResponseEntity<Validator> apply(
            @RequestBody ValidatorApplicationRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.validatorApplication(request).getItem());
    }

    @PostMapping("/resign")
    public ResponseEntity<Validator> resign(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.validatorResignation(request).getItem());
    }

    @PostMapping("/delegate")
    public ResponseEntity<Delegation> delegate(
            @RequestBody UpdateDelegationRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.addDelegation(request).getItem());
    }

    @PostMapping("/undelegate")
    public ResponseEntity<Delegation> undelegate(
            @RequestBody UpdateDelegationRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.removeDelegation(request).getItem());
    }
}