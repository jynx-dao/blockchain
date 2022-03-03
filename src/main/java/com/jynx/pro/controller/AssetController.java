package com.jynx.pro.controller;

import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.SingleItemRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/asset")
public class AssetController extends AbstractController {

    @GetMapping("/all")
    public ResponseEntity<List<Asset>> get() {
        return ResponseEntity.ok(readOnlyRepository.getAssets());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Asset> getById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getAssetById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.ASSET_NOT_FOUND)));
    }

    @PostMapping
    public ResponseEntity<Proposal> add(
            @RequestBody AddAssetRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.addAsset(request).getItem());
    }

    @PostMapping("/suspend")
    public ResponseEntity<Proposal> suspend(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.suspendAsset(request).getItem());
    }

    @PostMapping("/unsuspend")
    public ResponseEntity<Proposal> unsuspend(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.unsuspendAsset(request).getItem());
    }
}
