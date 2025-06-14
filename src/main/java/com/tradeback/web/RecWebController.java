package com.tradeback.web;

import com.tradeback.config.ApplicationConstants;
import com.tradeback.dto.IndicatorRequest;
import com.tradeback.model.MarketData;
import com.tradeback.model.Signal;
import com.tradeback.model.UserHistory;
import com.tradeback.repository.MarketDataRepository;
import com.tradeback.service.MarketDataService;
import com.tradeback.service.MarketHoursService;
import com.tradeback.service.SignalService;
import com.tradeback.service.UserHistoryService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RecWebController {

    private final MarketDataService marketDataService;
    private final SignalService signalService;
    private final UserHistoryService userHistoryService;
    private final MarketHoursService marketHoursService;
    private final MarketDataRepository marketDataRepository;

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

        // Добавляем символы в модель
        try {
            model.addAttribute("symbols", marketDataService.getListOfSymbols());
        } catch (Exception e) {
            model.addAttribute("symbols", List.of());
            log.error("Error loading symbols: {}", e.getMessage());
        }

        // ✅ НОВОЕ: Проверяем статус рынка
        MarketHoursService.MarketStatus marketStatus = marketHoursService.getMarketStatus();
        model.addAttribute("marketStatus", marketStatus);
        model.addAttribute("apiRecommendation", marketHoursService.getApiRecommendation());

        log.info("Market Status: {} - {}", marketStatus.getStatus(), marketStatus.getMessage());

        if (bindingResult.hasErrors()) {
            try {
                model.addAttribute("popularSymbols", marketDataService.getPopularSymbols());
            } catch (Exception e) {
                model.addAttribute("popularSymbols", List.of());
            }
            model.addAttribute("title", "Welcome to TradeBack");
            return "index";
        }

        try {
            log.info("Processing indicator request for symbol: {}, interval: {}",
                    indicator.getSymbol(), indicator.getInterval());

            // ✅ НОВОЕ: Предупреждение о статусе рынка
            if (!marketStatus.getIsOpen()) {
                String warningMessage = "Note: " + marketStatus.getMessage() +
                        ". Analysis will use data from the last trading day (" +
                        marketHoursService.getLastTradingDay() + ").";
                model.addAttribute("marketWarning", warningMessage);
                log.info("Market is closed: {}", warningMessage);
            }

            // ✅ ИСПРАВЛЕНО: Попробуем использовать данные последнего торгового дня
            String effectiveInterval = indicator.getInterval();

            // Для выходных/праздников используем daily данные вместо intraday
            if (!marketStatus.getIsOpen() &&
                    (effectiveInterval.contains("min") || effectiveInterval.contains("1min"))) {
                effectiveInterval = "daily";
                log.info("Market closed, switching from {} to daily interval", indicator.getInterval());
                model.addAttribute("intervalAdjustment",
                        "Interval adjusted from " + indicator.getInterval() + " to daily due to market closure");
            }

            // Генерируем сигналы с эффективным интервалом
            IndicatorRequest adjustedRequest = new IndicatorRequest();
            // Копируем все параметры
            adjustedRequest.setSymbol(indicator.getSymbol());
            adjustedRequest.setInterval(effectiveInterval);
            adjustedRequest.setFirstIndicatorType(indicator.getFirstIndicatorType());
            adjustedRequest.setFirstPeriod(indicator.getFirstPeriod());
            adjustedRequest.setSecondIndicatorType(indicator.getSecondIndicatorType());
            adjustedRequest.setSecondPeriod(indicator.getSecondPeriod());
            adjustedRequest.setThirdIndicatorType(indicator.getThirdIndicatorType());
            adjustedRequest.setThirdPeriod(indicator.getThirdPeriod());

            Map<String, Object> result = signalService.generateSignals(adjustedRequest);
            Signal generatedSignal = (Signal) result.get("signal");

            if (generatedSignal == null) {
                throw new RuntimeException("Failed to generate signal");
            }

            log.info("Generated signal: {} for {}", generatedSignal.getTypeAsString(), indicator.getSymbol());

            // Сохраняем запрос в историю
            try {
                userHistoryService.saveRequest(
                        currentUser.toString(),
                        indicator, // Сохраняем оригинальный запрос
                        generatedSignal.getDescription()
                );
            } catch (Exception e) {
                log.error("Failed to save to history: {}", e.getMessage());
            }

            // Добавляем данные для отображения результатов
            model.addAttribute("signal", generatedSignal);
            model.addAttribute("firstIndicatorValue", result.get("firstIndicatorValue"));
            model.addAttribute("secondIndicatorValue", result.get("secondIndicatorValue"));
            model.addAttribute("thirdIndicatorValue", result.get("thirdIndicatorValue"));
            model.addAttribute("currentSymbol", indicator.getSymbol());

            // ✅ УЛУЧШЕНО: Получаем рыночные данные с приоритетом API для выходных
            try {
                LocalDate endDate = marketHoursService.isTradingDay(LocalDate.now()) ?
                        LocalDate.now() : marketHoursService.getLastTradingDay();
                LocalDate startDate = endDate.minusDays(30);

                log.info("Fetching market data for {} from {} to {}",
                        indicator.getSymbol(), startDate, endDate);

                List<MarketData> marketData = new ArrayList<>();

                // Если рынок закрыт, сразу идем к API за свежими данными
                if (!marketStatus.getIsOpen()) {
                    log.info("Market is closed, fetching last trading day data from API for: {}", indicator.getSymbol());

                    try {
                        // ✅ НОВОЕ: Проверяем валидность символа
                        if (marketDataService.isSymbolValid(indicator.getSymbol())) {
                            log.info("Symbol {} is valid, fetching data...", indicator.getSymbol());

                            // Получаем данные последнего торгового дня
                            List<MarketData> apiData = marketDataService.getLastTradingDayData(indicator.getSymbol());

                            if (!apiData.isEmpty()) {
                                marketData = apiData;
                                log.info("Retrieved {} data points from API for last trading day", apiData.size());

                                // Сохраняем в базу для будущего использования
                                try {
                                    marketDataRepository.saveAll(apiData);
                                    log.info("Saved {} market data points to database", apiData.size());
                                } catch (Exception saveError) {
                                    log.warn("Failed to save market data: {}", saveError.getMessage());
                                }
                            } else {
                                log.warn("No data returned from API for symbol: {}", indicator.getSymbol());
                            }
                        } else {
                            log.error("Symbol {} is not valid or not supported by Alpha Vantage", indicator.getSymbol());
                            model.addAttribute("apiError",
                                    "Symbol '" + indicator.getSymbol() + "' is not valid or not supported. " +
                                            "Please try popular symbols like AAPL, MSFT, GOOGL, TSLA, AMZN, META, NVDA.");
                        }

                    } catch (Exception apiError) {
                        log.error("API request failed for {}: {}", indicator.getSymbol(), apiError.getMessage());

                        if (apiError.getMessage().contains("API call frequency")) {
                            model.addAttribute("apiLimitWarning",
                                    "Alpha Vantage API rate limit reached (5 calls/minute for free tier). Please wait a minute and try again.");
                        } else if (apiError.getMessage().contains("Invalid API call")) {
                            model.addAttribute("apiError",
                                    "Invalid API call for symbol '" + indicator.getSymbol() + "'. Please check the symbol spelling.");
                        } else {
                            model.addAttribute("apiError",
                                    "Market data service error: " + apiError.getMessage());
                        }
                    }
                } else {
                    // Рынок открыт - сначала проверяем базу данных
                    marketData = marketDataService.getMarketDataByDateRange(
                            indicator.getSymbol(), startDate, endDate);

                    // Если данных мало, дополняем из API
                    if (marketData.size() < 5) {
                        log.info("Limited data in database ({}), fetching from API...", marketData.size());
                        try {
                            List<MarketData> apiData = marketDataService.getLastTradingDayData(indicator.getSymbol());
                            if (!apiData.isEmpty()) {
                                marketData.addAll(apiData);
                                log.info("Added {} data points from API", apiData.size());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to get additional data from API: {}", e.getMessage());
                        }
                    }
                }

                if (!marketData.isEmpty()) {
                    // Ограничиваем и сортируем данные
                    List<MarketData> limitedData = marketData.stream()
                            .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                            .limit(10)
                            .collect(Collectors.toList());

                    model.addAttribute("marketData", limitedData);
                    log.info("Added {} market data points to model, latest: {}",
                            limitedData.size(),
                            limitedData.isEmpty() ? "none" : limitedData.get(0).getDate());

                    // Добавляем информацию о последнем торговом дне
                    if (!marketStatus.getIsOpen()) {
                        model.addAttribute("lastTradingDayInfo",
                                "Showing data from last trading day: " +
                                        limitedData.get(0).getDate().toLocalDate());
                    }

                } else {
                    model.addAttribute("marketData", List.of());
                    String errorMsg;

                    if (marketStatus.getIsOpen()) {
                        errorMsg = "No market data available for '" + indicator.getSymbol() +
                                "'. The symbol may not exist. Try popular symbols: AAPL, MSFT, GOOGL, TSLA.";
                    } else {
                        errorMsg = "No market data available for '" + indicator.getSymbol() +
                                "'. Symbol may not be valid or supported. Try popular symbols: AAPL, MSFT, GOOGL, TSLA.";
                    }

                    model.addAttribute("marketDataError", errorMsg);
                    log.warn("No market data found for symbol: {}", indicator.getSymbol());
                }

            } catch (Exception e) {
                log.error("Error fetching market data for {}: {}", indicator.getSymbol(), e.getMessage(), e);
                model.addAttribute("marketData", List.of());
                model.addAttribute("marketDataError",
                        "Could not load market data: " + e.getMessage() +
                                ". Please try a popular symbol like AAPL, MSFT, or GOOGL.");
            }

            model.addAttribute("indicator", indicator);
            model.addAttribute("title", "Analysis Results");
            return "indicators";

        } catch (Exception e) {
            log.error("Error generating analysis for {}: {}", indicator.getSymbol(), e.getMessage(), e);

            // Улучшенное сообщение об ошибке с учетом статуса рынка
            String errorMessage = "Error generating analysis: " + e.getMessage();
            if (!marketStatus.getIsOpen()) {
                errorMessage += " Note: Markets are currently closed, which may affect data availability.";
            }

            model.addAttribute("error", errorMessage);
            try {
                model.addAttribute("popularSymbols", marketDataService.getPopularSymbols());
            } catch (Exception ex) {
                model.addAttribute("popularSymbols", List.of());
            }
            model.addAttribute("title", "Welcome to TradeBack");
            model.addAttribute("indicator", indicator);
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