package com.atguigu.study.controller;

import com.atguigu.study.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 股票查询接口
 * GET /stock/quote?code=sh600519 实时行情
 * GET /stock/history?code=sh600519&days=10 历史K线
 * GET /stock/presets 预设股票列表
 */
@RestController
@RequestMapping("/stock")
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);

    private final StockService stockService;

    @Autowired
    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * 实时行情
     * http://localhost:8019/stock/quote?code=sh600519
     */
    @GetMapping("/quote")
    public Map<String, Object> quote(
            @RequestParam(name = "code", defaultValue = "sh600519") String code) {
        log.info("查询实时行情: {}", code);
        return stockService.getRealtimeQuote(code);
    }

    /**
     * 历史K线（交易记录）
     * http://localhost:8019/stock/history?code=sh600519&days=10
     */
    @GetMapping("/history")
    public Map<String, Object> history(
            @RequestParam(name = "code", defaultValue = "sh600519") String code,
            @RequestParam(name = "days", defaultValue = "10") int days) {
        log.info("查询历史K线: {}, {}天", code, days);
        return stockService.getTradeHistory(code, days);
    }

    /**
     * 预设常用股票列表
     * http://localhost:8019/stock/presets
     */
    @GetMapping("/presets")
    public Map<String, String> presets() {
        return stockService.getPresetStocks();
    }
}
