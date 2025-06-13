package com.tradeback.controller;

import com.tradeback.model.Signal;
import com.tradeback.service.SignalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    @Autowired
    private SignalService signalService;

//    @PostMapping("/generate/{symbol}/{period}")
//    public String generateSignals(@PathVariable String symbol, @PathVariable int period) {
//        signalService.generateSignals(symbol, period);
//        return "Signals generated successfully!";
//    }

    @GetMapping("/{symbol}")
    public List<Signal> getSignals(@PathVariable String symbol) {
        return signalService.getSignalsBySymbol(symbol);
    }

    @GetMapping("/range/{symbol}")
    public List<Signal> getSignalsByDateRange(
            @PathVariable String symbol,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return signalService.getSignalsByDateRange(symbol, startDate, endDate);
    }
}



