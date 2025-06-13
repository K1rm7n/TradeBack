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

            // Используем новый метод для получения ограиченного количества записей
            List<UserHistory> recentHistory = userHistoryService.getRecentUserHistory(currentUser.toString(), 5);
            model.addAttribute("recentHistory", recentHistory);
        }

        try {
            // Добавляем и все символы для dropdown, и популярные отдельно
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
            model.addAttribute("popularSymbols", marketDataService.getPopularSymbols());
        } catch (Exception e) {
            // Если ошибка с получением символов, создаем пустые списки
            model.addAttribute("symbols", List.of());
            model.addAttribute("popularSymbols", List.of());
            model.addAttribute("error", "Error loading symbols: " + e.getMessage());
        }

        model.addAttribute("title", "Welcome to TradeBack");

        // ✅ ОБЯЗАТЕЛЬНО: создаем пустой объект для формы
        model.addAttribute("indicator", new IndicatorRequest());

        return "index";
    }

    @PostMapping("/indicators")
    public String applyIndicator(@Valid @ModelAttribute("indicator") IndicatorRequest indicator,
                                 BindingResult bindingResult,
                                 Model model, HttpSession session) {
        Object currentUser = session.getAttribute(ApplicationConstants.USER_SESSION_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", currentUser);

        // ✅ ВАЖНО: всегда добавляем символы в модель
        try {
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
        } catch (Exception e) {
            model.addAttribute("symbols", List.of());
        }

        if (bindingResult.hasErrors()) {
            // При ошибке валидации - возвращаем на главную с ошибками
            try {
                model.addAttribute("popularSymbols", marketDataService.getPopularSymbols());
            } catch (Exception e) {
                model.addAttribute("popularSymbols", List.of());
            }
            model.addAttribute("title", "Welcome to TradeBack");
            return "index";
        }

        try {
            // ✅ Генерируем сигналы
            Map<String, Object> result = signalService.generateSignals(indicator);
            Signal generatedSignal = (Signal) result.get("signal");

            // Сохраняем запрос в историю
            userHistoryService.saveRequest(
                    currentUser.toString(),
                    indicator,
                    generatedSignal.getDescription()
            );

            // Добавляем данные для отображения результатов
            model.addAttribute("signal", generatedSignal);
            model.addAttribute("firstIndicatorValue", result.get("firstIndicatorValue"));
            model.addAttribute("secondIndicatorValue", result.get("secondIndicatorValue"));
            model.addAttribute("thirdIndicatorValue", result.get("thirdIndicatorValue"));
            model.addAttribute("currentSymbol", indicator.getSymbol());

            // Получаем рыночные данные для отображения
            try {
                List<MarketData> marketData = marketDataService.getStockData(indicator.getSymbol());
                model.addAttribute("marketData", marketData);
            } catch (Exception e) {
                model.addAttribute("marketData", List.of());
                model.addAttribute("marketDataError", "Could not load market data: " + e.getMessage());
            }

            model.addAttribute("indicator", indicator);
            model.addAttribute("title", "Analysis Results");

            return "indicators";

        } catch (Exception e) {
            // При ошибке анализа - возвращаем на главную с ошибкой
            model.addAttribute("error", "Error generating analysis: " + e.getMessage());
            try {
                model.addAttribute("popularSymbols", marketDataService.getPopularSymbols());
            } catch (Exception ex) {
                model.addAttribute("popularSymbols", List.of());
            }
            model.addAttribute("title", "Welcome to TradeBack");
            model.addAttribute("indicator", indicator); // Сохраняем введенные данные
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