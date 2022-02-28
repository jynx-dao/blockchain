package com.jynx.pro.controller;

import com.jynx.pro.entity.Vote;
import com.jynx.pro.request.CastVoteRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/proposal")
public class ProposalController extends AbstractController {

    @PostMapping("/vote")
    public ResponseEntity<Vote> vote(
            @RequestBody CastVoteRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.castVote(request).getItem());
    }
}