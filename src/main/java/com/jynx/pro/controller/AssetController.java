package com.jynx.pro.controller;

import com.jynx.pro.entity.Asset;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.SingleItemRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/asset")
public class AssetController extends AbstractController {

    @GetMapping("/all")
    public ResponseEntity<List<Asset>> get() {
        return ResponseEntity.ok(readOnlyRepository.getAssets());
    }

    @PostMapping
    public ResponseEntity<Asset> add(
            @RequestBody AddAssetRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.addAsset(request).getItem());
    }

    @PostMapping("/suspend")
    public ResponseEntity<Asset> suspend(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.suspendAsset(request).getItem());
    }

    @PostMapping("/unsuspend")
    public ResponseEntity<Asset> unsuspend(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.unsuspendAsset(request).getItem());
    }
}
