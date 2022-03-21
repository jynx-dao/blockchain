package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.entity.Position;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;

@EnabledIfEnvironmentVariable(named = "POSITION_SERVICE_TEST", matches = "true")
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

    @Test
    public void testIsDistressedBuy() {
        boolean isDistressed = positionService.isDistressed(new Position()
                .setSide(MarketSide.BUY)
                .setLiquidationPrice(BigDecimal.valueOf(1.01)), BigDecimal.valueOf(1));
        Assertions.assertTrue(isDistressed);
    }

    @Test
    public void testIsDistressedSell() {
        boolean isDistressed = positionService.isDistressed(new Position()
                .setSide(MarketSide.SELL)
                .setLiquidationPrice(BigDecimal.valueOf(0.99)), BigDecimal.valueOf(1));
        Assertions.assertTrue(isDistressed);
    }

    @Test
    public void testIsNotDistressedBuy() {
        boolean isDistressed = positionService.isDistressed(new Position()
                .setSide(MarketSide.BUY)
                .setLiquidationPrice(BigDecimal.valueOf(0.99)), BigDecimal.valueOf(1));
        Assertions.assertFalse(isDistressed);
    }

    @Test
    public void testIsNotDistressedSell() {
        boolean isDistressed = positionService.isDistressed(new Position()
                .setSide(MarketSide.SELL)
                .setLiquidationPrice(BigDecimal.valueOf(1.01)), BigDecimal.valueOf(1));
        Assertions.assertFalse(isDistressed);
    }
}