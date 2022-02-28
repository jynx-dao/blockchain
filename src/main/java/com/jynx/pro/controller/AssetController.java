package com.jynx.pro.controller;

import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Market;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.AmendMarketRequest;
import com.jynx.pro.request.SingleItemRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/asset")
public class AssetController extends AbstractController {

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
