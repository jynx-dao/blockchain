package com.jynx.pro.controller;

import com.jynx.pro.model.BlockchainStatistics;
import com.jynx.pro.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/blockchain")
public class BlockchainController {

    @Autowired
    private ConfigService configService;

    @GetMapping("/statistics")
    public ResponseEntity<BlockchainStatistics> getStatistics() {
        return ResponseEntity.ok(new BlockchainStatistics().setTimestamp(configService.getTimestamp()));
    }
}