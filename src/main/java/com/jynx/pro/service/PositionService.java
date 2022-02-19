package com.jynx.pro.service;

import com.jynx.pro.repository.PositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PositionService {

    @Autowired
    private PositionRepository positionRepository;

    public void update() {
        // TODO - update position
    }
}