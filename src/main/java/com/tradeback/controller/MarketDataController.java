package com.tradeback.controller;

import com.tradeback.model.Listing;
import com.tradeback.model.MarketData;
import com.tradeback.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/marketdata")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/{symbol}")
    public ResponseEntity<Iterable<MarketData>> getAllMarketData(@PathVariable String symbol) {
        try {
            Iterable<MarketData> data = marketDataService.getAllMarketData(symbol);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error getting market data for {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{symbol}/{date}")
    public ResponseEntity<MarketData> getMarketDataByDate(@PathVariable String symbol,
                                                          @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            MarketData data = marketDataService.getMarketDataBySymbolAndDate(symbol, date);
            if (data != null) {
                return ResponseEntity.ok(data);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting market data for {} on {}: {}", symbol, date, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{symbol}/range")
    public ResponseEntity<List<MarketData>> getMarketDataByDateRange(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            List<MarketData> data = marketDataService.getMarketDataByDateRange(symbol, startDate, endDate);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error getting market data range for {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/symbols/all")
    public ResponseEntity<List<Listing>> getMarketDataSymbols() {
        try {
            List<Listing> symbols = marketDataService.getListOfSymbols();
            return ResponseEntity.ok(symbols);
        } catch (Exception e) {
            log.error("Error getting symbols list: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{symbol}/save")
    public ResponseEntity<Map<String, String>> saveMarketData(@PathVariable String symbol) {
        try {
            marketDataService.saveStockData(symbol);
            return ResponseEntity.ok(Map.of("message", "Market data saved successfully for " + symbol));
        } catch (Exception e) {
            log.error("Error saving market data for {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to save market data: " + e.getMessage()));
        }
    }

    @GetMapping("/{symbol}/intraday")
    public ResponseEntity<List<MarketData>> getIntradayData(@PathVariable String symbol) {
        try {
            List<MarketData> data = marketDataService.getStockData(symbol);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error getting intraday data for {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}