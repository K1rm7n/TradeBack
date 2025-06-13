package com.tradeback.controller;


import com.tradeback.model.Indicator;
import com.tradeback.service.IndicatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/indicators")
public class IndicatorController {

    @Autowired
    private IndicatorService indicatorService;

    // Получение Simple Moving Average (SMA) для символа

    @PostMapping("/calculate")
    public Double calculateIndicator(@RequestBody Indicator indicator) {
        System.out.println(indicator);
        return indicatorService.calculateIndicator(indicator);
    }

//    @GetMapping("/sma/{symbol}/{period}")
//    public double getSMA(@PathVariable String symbol, @PathVariable int period) {
//        return indicatorService.calculateSMA(symbol, period);
//    }
//
//    // Получение Exponential Moving Average (EMA) для символа
//    @GetMapping("/ema/{symbol}/{period}")
//    public double getEMA(@PathVariable String symbol, @PathVariable int period) {
//        return indicatorService.calculateEMA(symbol, period);
//    }
//
//    // Получение Relative Strength Index (RSI) для символа
//    @GetMapping("/rsi/{symbol}/{period}")
//    public double getRSI(@PathVariable String symbol, @PathVariable int period) {
//        return indicatorService.calculateRSI(symbol, period);
//    }

    // Генерация сигналов на основе индикаторов для символа
//    @PostMapping("/signals/{symbol}")
//    public void generateTradingSignals(@PathVariable String symbol) {
//        indicatorService.generateTradingSignals(symbol);
//    }
}