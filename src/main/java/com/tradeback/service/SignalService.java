package com.tradeback.service;

import com.tradeback.config.ApplicationConstants;
import com.tradeback.dto.IndicatorRequest;
import com.tradeback.model.Indicator;
import com.tradeback.model.Signal;
import com.tradeback.repository.SignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalService {

    private final SignalRepository signalRepository;
    private final IndicatorService indicatorService;
    private final GroqChatService groqChatService;

    // Список индикаторов, которые не используют период
    private static final List<String> NO_PERIOD_INDICATORS = Arrays.asList(
            "MACD", "STOCH", "SAR", "VWAP", "OBV"
    );

    public Map<String, Object> generateSignals(IndicatorRequest indicatorRequest) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Generating signals for: {}", indicatorRequest.getSymbol());

            // ✅ ИСПРАВЛЕНО: Calculate indicators с подробным логированием
            Indicator firstIndicator = createIndicator(indicatorRequest.getSymbol(),
                    indicatorRequest.getFirstIndicatorType(), indicatorRequest.getFirstPeriod(),
                    indicatorRequest.getInterval());

            log.info("Calculating first indicator: {} with period: {}",
                    firstIndicator.getType(), firstIndicator.getPeriod());
            double firstIndicatorValue = indicatorService.calculateIndicator(firstIndicator);
            log.info("First indicator value: {}", firstIndicatorValue);

            Indicator secondIndicator = createIndicator(indicatorRequest.getSymbol(),
                    indicatorRequest.getSecondIndicatorType(), indicatorRequest.getSecondPeriod(),
                    indicatorRequest.getInterval());

            log.info("Calculating second indicator: {} with period: {}",
                    secondIndicator.getType(), secondIndicator.getPeriod());
            double secondIndicatorValue = indicatorService.calculateIndicator(secondIndicator);
            log.info("Second indicator value: {}", secondIndicatorValue);

            Indicator thirdIndicator = createIndicator(indicatorRequest.getSymbol(),
                    indicatorRequest.getThirdIndicatorType(), indicatorRequest.getThirdPeriod(),
                    indicatorRequest.getInterval());

            log.info("Calculating third indicator: {} with period: {}",
                    thirdIndicator.getType(), thirdIndicator.getPeriod());
            double thirdIndicatorValue = indicatorService.calculateIndicator(thirdIndicator);
            log.info("Third indicator value: {}", thirdIndicatorValue);

            // ✅ ИСПРАВЛЕНО: Get current price с fallback
            double currentPrice = indicatorService.getCurrentPrice(indicatorRequest.getSymbol());
            log.info("Current price for {}: {}", indicatorRequest.getSymbol(), currentPrice);

            if (currentPrice == 0.0) {
                // Если текущая цена недоступна, используем значение первого индикатора как приближение
                if (firstIndicatorValue > 0) {
                    currentPrice = firstIndicatorValue;
                    log.info("Using first indicator value as current price: {}", currentPrice);
                } else if (secondIndicatorValue > 0) {
                    currentPrice = secondIndicatorValue;
                    log.info("Using second indicator value as current price: {}", currentPrice);
                } else {
                    currentPrice = 100.0; // Fallback значение
                    log.warn("No valid price found, using fallback: {}", currentPrice);
                }
            }

            // ✅ ИСПРАВЛЕНО: Check if we have valid indicator values
            if (firstIndicatorValue == 0.0 && secondIndicatorValue == 0.0 && thirdIndicatorValue == 0.0) {
                log.error("All indicator values are zero, API might be failing");
                throw new RuntimeException("Unable to calculate indicators - API may be unavailable or symbol not found");
            }

            // Get AI advice с учетом периодов (0 для индикаторов без периода)
            String advice;
            try {
                advice = groqChatService.getTradingAdvice(
                        indicatorRequest.getSymbol(), currentPrice,
                        indicatorRequest.getFirstIndicatorType(), firstIndicatorValue,
                        getEffectivePeriod(indicatorRequest.getFirstIndicatorType(), indicatorRequest.getFirstPeriod()),
                        indicatorRequest.getSecondIndicatorType(), secondIndicatorValue,
                        getEffectivePeriod(indicatorRequest.getSecondIndicatorType(), indicatorRequest.getSecondPeriod()),
                        indicatorRequest.getThirdIndicatorType(), thirdIndicatorValue,
                        getEffectivePeriod(indicatorRequest.getThirdIndicatorType(), indicatorRequest.getThirdPeriod())
                );
                log.info("Generated AI advice: {}", advice.substring(0, Math.min(100, advice.length())));
            } catch (Exception e) {
                log.error("Error getting AI advice: {}", e.getMessage());
                advice = "HOLD: Technical analysis completed successfully. " +
                        "First indicator (" + indicatorRequest.getFirstIndicatorType() + "): " + String.format("%.2f", firstIndicatorValue) + ". " +
                        "Second indicator (" + indicatorRequest.getSecondIndicatorType() + "): " + String.format("%.2f", secondIndicatorValue) + ". " +
                        "Third indicator (" + indicatorRequest.getThirdIndicatorType() + "): " + String.format("%.2f", thirdIndicatorValue) + ". " +
                        "Please review the indicators manually as AI service is temporarily unavailable.";
            }

            // Create and save signal with enum support
            Signal signal = createSignal(indicatorRequest.getSymbol(), advice, currentPrice);
            Signal savedSignal = signalRepository.save(signal);
            log.info("Saved signal with ID: {}", savedSignal.getId());

            // Prepare result
            result.put("signal", savedSignal);
            result.put("firstIndicator", firstIndicator);
            result.put("firstIndicatorValue", firstIndicatorValue);
            result.put("secondIndicator", secondIndicator);
            result.put("secondIndicatorValue", secondIndicatorValue);
            result.put("thirdIndicator", thirdIndicator);
            result.put("thirdIndicatorValue", thirdIndicatorValue);
            result.put("currentPrice", currentPrice);

            log.info("Successfully generated signals for: {}", indicatorRequest.getSymbol());

        } catch (Exception e) {
            log.error("Error generating signals for {}: {}", indicatorRequest.getSymbol(), e.getMessage(), e);

            // ✅ УЛУЧШЕНО: Создаем более информативный fallback сигнал
            Signal fallbackSignal = createDetailedFallbackSignal(indicatorRequest.getSymbol(), e.getMessage());
            result.put("signal", fallbackSignal);
            result.put("firstIndicatorValue", 0.0);
            result.put("secondIndicatorValue", 0.0);
            result.put("thirdIndicatorValue", 0.0);
            result.put("currentPrice", 0.0);
            result.put("error", "Unable to generate complete analysis: " + e.getMessage());
        }

        return result;
    }

    public List<Signal> getSignalsBySymbol(String symbol) {
        return signalRepository.findBySymbolOrderByDateAsc(symbol);
    }

    public List<Signal> getSignalsByDateRange(String symbol, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        return signalRepository.findBySymbolAndDateRange(symbol, startDateTime, endDateTime);
    }

    public Optional<Signal> findById(Long id) {
        return signalRepository.findById(id);
    }

    public void deleteById(Long id) {
        signalRepository.deleteById(id);
    }

    // Вспомогательные методы с поддержкой новых типов
    private Indicator createIndicator(String symbol, String type, int period, String interval) {
        Indicator indicator = new Indicator();
        indicator.setSymbol(symbol);

        // Конвертируем String в enum через метод в модели
        try {
            indicator.setType(Indicator.IndicatorType.valueOf(type.toUpperCase()));
        } catch (Exception e) {
            log.warn("Unknown indicator type: {}, defaulting to SMA", type);
            indicator.setType(Indicator.IndicatorType.SMA);
        }

        // Устанавливаем период с учетом типа индикатора
        if (NO_PERIOD_INDICATORS.contains(type.toUpperCase())) {
            indicator.setPeriod(0); // Для индикаторов без периода
        } else {
            indicator.setPeriod(period);
        }

        indicator.setInterval(interval);
        indicator.setCalculatedAt(LocalDateTime.now());
        return indicator;
    }

    private Signal createSignal(String symbol, String advice, double price) {
        Signal signal = new Signal();
        signal.setSymbol(symbol);

        // Используем метод setType, который принимает String и конвертирует в enum
        signal.setType(extractSignalTypeString(advice));
        signal.setDescription(advice);
        signal.setPrice(price); // Автоматически конвертируется в BigDecimal
        signal.setDate(LocalDateTime.now());
        return signal;
    }

    // ✅ УЛУЧШЕННЫЙ метод создания fallback сигнала
    private Signal createDetailedFallbackSignal(String symbol, String errorMessage) {
        Signal signal = new Signal();
        signal.setSymbol(symbol);
        signal.setType("HOLD");

        String description = "HOLD: Unable to complete technical analysis. ";
        if (errorMessage.contains("API")) {
            description += "Market data service is temporarily unavailable. ";
        } else if (errorMessage.contains("symbol")) {
            description += "Symbol may not be valid or supported. ";
        } else {
            description += "Technical issue encountered. ";
        }
        description += "Please try again later or contact support if the issue persists.";

        signal.setDescription(description);
        signal.setPrice(0.0);
        signal.setDate(LocalDateTime.now());

        log.info("Created fallback signal for {}: {}", symbol, description);
        return signal;
    }

    private String extractSignalTypeString(String advice) {
        if (advice == null) return "UNKNOWN";

        String upperAdvice = advice.toUpperCase();
        if (upperAdvice.startsWith("BUY:") || upperAdvice.contains("BUY:")) {
            return "BUY";
        } else if (upperAdvice.startsWith("SELL:") || upperAdvice.contains("SELL:")) {
            return "SELL";
        } else if (upperAdvice.startsWith("HOLD:") || upperAdvice.contains("HOLD:")) {
            return "HOLD";
        } else if (upperAdvice.contains("STRONG BUY")) {
            return "STRONG_BUY";
        } else if (upperAdvice.contains("STRONG SELL")) {
            return "STRONG_SELL";
        }
        return "HOLD";
    }

    /**
     * Возвращает эффективный период для индикатора
     * Для индикаторов без периода возвращает 0
     */
    private int getEffectivePeriod(String indicatorType, int period) {
        if (NO_PERIOD_INDICATORS.contains(indicatorType.toUpperCase())) {
            return 0;
        }
        return period;
    }
}