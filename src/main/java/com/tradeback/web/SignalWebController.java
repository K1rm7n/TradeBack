package com.tradeback.web;

import com.tradeback.config.ApplicationConstants;
import com.tradeback.model.Signal;
import com.tradeback.service.MarketDataService;
import com.tradeback.service.SignalService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/signals")
@RequiredArgsConstructor
public class SignalWebController {

    private final SignalService signalService;
    private final MarketDataService marketDataService;

    @GetMapping
    public String listSignals(Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("symbols", marketDataService.getListOfSymbols());
        model.addAttribute("title", "Trading Signals");
        return "signals/list";
    }

    @GetMapping("/symbol/{symbol}")
    public String getSignalsBySymbol(@PathVariable String symbol, Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            model.addAttribute("user", currentUser);
            model.addAttribute("signals", signalService.getSignalsBySymbol(symbol));
            model.addAttribute("symbol", symbol);
            model.addAttribute("title", "Signals for " + symbol);
            return "signals/symbol";
        } catch (Exception e) {
            log.error("Error retrieving signals for {}: {}", symbol, e.getMessage());
            model.addAttribute("error", "Error retrieving signals for " + symbol + ": " + e.getMessage());
            model.addAttribute("user", currentUser);
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
            return "signals/list";
        }
    }

    @GetMapping("/range/{symbol}")
    public String getSignalsByDateRange(
            @PathVariable String symbol,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model, HttpSession session) {

        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            if (startDate.isAfter(endDate)) {
                model.addAttribute("error", "Start date cannot be after end date");
                model.addAttribute("user", currentUser);
                model.addAttribute("symbols", marketDataService.getListOfSymbols());
                return "signals/list";
            }

            if (startDate.isBefore(LocalDate.now().minusYears(1))) {
                startDate = LocalDate.now().minusYears(1);
                model.addAttribute("warning", "Start date adjusted to maximum range (1 year)");
            }

            model.addAttribute("user", currentUser);
            model.addAttribute("signals", signalService.getSignalsByDateRange(symbol, startDate, endDate));
            model.addAttribute("symbol", symbol);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("title", "Signals for " + symbol + " (" + startDate + " to " + endDate + ")");
            return "signals/range";

        } catch (DateTimeParseException e) {
            log.error("Invalid date format for {}: {}", symbol, e.getMessage());
            model.addAttribute("error", "Invalid date format. Please use YYYY-MM-DD format.");
            model.addAttribute("user", currentUser);
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
            return "signals/list";
        } catch (Exception e) {
            log.error("Error in getSignalsByDateRange for {}: {}", symbol, e.getMessage());
            model.addAttribute("error", "Error retrieving signals: " + e.getMessage());
            model.addAttribute("user", currentUser);
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
            return "signals/list";
        }
    }

    @PostMapping("/range")
    public String searchSignalsByDateRange(
            @RequestParam("symbol") String symbol,
            @RequestParam("startDate") String startDateStr,
            @RequestParam("endDate") String endDateStr,
            Model model, HttpSession session) {

        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);

            if (symbol == null || symbol.trim().isEmpty()) {
                model.addAttribute("error", "Please select a symbol");
                model.addAttribute("user", currentUser);
                model.addAttribute("symbols", marketDataService.getListOfSymbols());
                return "signals/list";
            }

            if (startDate.isAfter(endDate)) {
                model.addAttribute("error", "Start date cannot be after end date");
                model.addAttribute("user", currentUser);
                model.addAttribute("symbols", marketDataService.getListOfSymbols());
                return "signals/list";
            }

            return "redirect:/signals/range/" + symbol + "?startDate=" + startDate + "&endDate=" + endDate;

        } catch (DateTimeParseException e) {
            log.error("Error parsing dates: {}", e.getMessage());
            model.addAttribute("error", "Invalid date format. Please select valid dates.");
            model.addAttribute("user", currentUser);
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
            return "signals/list";
        } catch (Exception e) {
            log.error("Error in searchSignalsByDateRange: {}", e.getMessage());
            model.addAttribute("error", "Error processing request: " + e.getMessage());
            model.addAttribute("user", currentUser);
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
            return "signals/list";
        }
    }

    @GetMapping("/{id}")
    public String viewSignal(@PathVariable Long id, Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            model.addAttribute("user", currentUser);

            Optional<Signal> signalOpt = signalService.findById(id);
            if (signalOpt.isPresent()) {
                Signal signal = signalOpt.get();
                model.addAttribute("signal", signal);
                model.addAttribute("title", "Signal Details - " + signal.getSymbol());
                return "signals/view";
            } else {
                model.addAttribute("error", "Signal not found");
                return "redirect:/signals";
            }
        } catch (Exception e) {
            log.error("Error retrieving signal {}: {}", id, e.getMessage());
            model.addAttribute("error", "Error retrieving signal: " + e.getMessage());
            return "redirect:/signals";
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteSignal(@PathVariable Long id, HttpSession session, Model model) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            signalService.deleteById(id);
            log.info("Signal {} deleted by user {}", id, currentUser);
            return "redirect:/signals";
        } catch (Exception e) {
            log.error("Error deleting signal {}: {}", id, e.getMessage());
            model.addAttribute("error", "Error deleting signal: " + e.getMessage());
            return "redirect:/signals";
        }
    }
}