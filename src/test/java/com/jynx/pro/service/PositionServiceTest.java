package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.entity.Position;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class PositionServiceTest {

    private final PositionService positionService = new PositionService();

    @Test
    public void testFlipWinningSell() {
        Position position = new Position()
                .setSide(MarketSide.SELL)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(101);
        BigDecimal gain = BigDecimal.valueOf(0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.multiply(BigDecimal.valueOf(-1)).doubleValue());
    }

    @Test
    public void testFlipWinningBuy() {
        Position position = new Position()
                .setSide(MarketSide.BUY)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(101);
        BigDecimal gain = BigDecimal.valueOf(-0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.multiply(BigDecimal.valueOf(-1)).doubleValue());
    }

    @Test
    public void testFlipLosingSell() {
        Position position = new Position()
                .setSide(MarketSide.SELL)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(99);
        BigDecimal gain = BigDecimal.valueOf(-0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.multiply(BigDecimal.valueOf(-1)).doubleValue());
    }

    @Test
    public void testFlipLosingBuy() {
        Position position = new Position()
                .setSide(MarketSide.BUY)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(99);
        BigDecimal gain = BigDecimal.valueOf(0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.multiply(BigDecimal.valueOf(-1)).doubleValue());
    }

    @Test
    public void testDoNotFlipWinningSell() {
        Position position = new Position()
                .setSide(MarketSide.SELL)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(101);
        BigDecimal gain = BigDecimal.valueOf(-0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.doubleValue());
    }

    @Test
    public void testDoNotFlipWinningBuy() {
        Position position = new Position()
                .setSide(MarketSide.BUY)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(101);
        BigDecimal gain = BigDecimal.valueOf(0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.doubleValue());
    }

    @Test
    public void testDoNotFlipLosingSell() {
        Position position = new Position()
                .setSide(MarketSide.SELL)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(99);
        BigDecimal gain = BigDecimal.valueOf(0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.doubleValue());
    }

    @Test
    public void testDoNotFlipLosingBuy() {
        Position position = new Position()
                .setSide(MarketSide.BUY)
                .setAverageEntryPrice(BigDecimal.valueOf(100));
        BigDecimal price = BigDecimal.valueOf(99);
        BigDecimal gain = BigDecimal.valueOf(-0.01);
        BigDecimal updated = positionService.flipGain(position, gain, price);
        Assertions.assertEquals(updated.doubleValue(), gain.doubleValue());
    }
}