package com.tradeback.web;

import com.tradeback.config.ApplicationConstants;
import com.tradeback.dto.IndicatorRequest;
import com.tradeback.model.MarketData;
import com.tradeback.model.Signal;
import com.tradeback.model.UserHistory;
import com.tradeback.service.MarketDataService;
import com.tradeback.service.SignalService;
import com.tradeback.service.UserHistoryService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class RecWebController {

    private final MarketDataService marketDataService;
    private final SignalService signalService;
    private final UserHistoryService userHistoryService;

    @GetMapping("/")
    public String indexPage(Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser != null) {
            model.addAttribute("user", currentUser);

            // Используем новый метод для получения ограниченного количества записей
            List<UserHistory> recentHistory = userHistoryService.getRecentUserHistory(currentUser.toString(), 5);
            model.addAttribute("recentHistory", recentHistory);
        }

        // ИСПРАВЛЕНО: добавляем и все символы для dropdown, и популярные отдельно
        model.addAttribute("symbols", marketDataService.getListOfSymbols()); // Все символы для select
        model.addAttribute("popularSymbols", marketDataService.getPopularSymbols()); // Популярные для отображения
        model.addAttribute("title", "Welcome to TradeBack");
        model.addAttribute("indicator", new IndicatorRequest());
        return "index";
    }

    @PostMapping("/indicators")
    public String applyIndicator(@Valid @ModelAttribute IndicatorRequest indicator,
                                 BindingResult bindingResult,
                                 Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("symbols", marketDataService.getListOfSymbols());

        if (bindingResult.hasErrors()) {
            // ИСПРАВЛЕНО: добавляем популярные символы при ошибке валидации
            model.addAttribute("popularSymbols", marketDataService.getPopularSymbols());
            model.addAttribute("title", "Welcome to TradeBack");
            return "index";
        }

        try {
            Map<String, Object> result = signalService.generateSignals(indicator);
            Signal generatedSignal = (Signal) result.get("signal");

            userHistoryService.saveRequest(
                    currentUser.toString(),
                    indicator,
                    generatedSignal.getDescription()
            );

            // Add data to model for display
            model.addAttribute("signal", generatedSignal);
            model.addAttribute("firstIndicatorValue", result.get("firstIndicatorValue"));
            model.addAttribute("secondIndicatorValue", result.get("secondIndicatorValue"));
            model.addAttribute("thirdIndicatorValue", result.get("thirdIndicatorValue"));
            model.addAttribute("currentSymbol", indicator.getSymbol());

            // Get market data for display
            List<MarketData> marketData = marketDataService.getStockData(indicator.getSymbol());
            model.addAttribute("marketData", marketData);
            model.addAttribute("indicator", indicator);

            return "indicators";
        } catch (Exception e) {
            model.addAttribute("error", "Error generating analysis: " + e.getMessage());
            // ИСПРАВЛЕНО: добавляем популярные символы при ошибке
            model.addAttribute("popularSymbols", marketDataService.getPopularSymbols());
            model.addAttribute("title", "Welcome to TradeBack");
            return "index";
        }
    }

    @GetMapping("/history")
    public String viewHistory(Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser != null) {
            model.addAttribute("user", currentUser);
            model.addAttribute("history", userHistoryService.getUserHistory(currentUser.toString()));
            model.addAttribute("title", "Request History");
            return "history";
        }

        return "redirect:/login";
    }

    @GetMapping("/history/{id}")
    public String viewHistoryDetail(@PathVariable Long id, Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser != null) {
            model.addAttribute("user", currentUser);

            userHistoryService.getHistoryById(id).ifPresent(history -> {
                model.addAttribute("historyItem", history);

                IndicatorRequest indicator = new IndicatorRequest();
                indicator.setSymbol(history.getSymbol());
                indicator.setInterval(history.getInterval());
                indicator.setFirstIndicatorType(history.getFirstIndicatorType());
                indicator.setFirstPeriod(history.getFirstPeriod());
                indicator.setSecondIndicatorType(history.getSecondIndicatorType());
                indicator.setSecondPeriod(history.getSecondPeriod());
                indicator.setThirdIndicatorType(history.getThirdIndicatorType());
                indicator.setThirdPeriod(history.getThirdPeriod());

                model.addAttribute("indicator", indicator);
            });

            model.addAttribute("title", "History Detail");
            return "history-detail";
        }

        return "redirect:/login";
    }

    @PostMapping("/history/delete/{id}")
    public String deleteHistory(@PathVariable Long id, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser != null) {
            userHistoryService.deleteHistory(id);
        }

        return "redirect:/history";
    }
}