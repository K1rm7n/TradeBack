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
            // Calculate indicators
            Indicator firstIndicator = createIndicator(indicatorRequest.getSymbol(),
                    indicatorRequest.getFirstIndicatorType(), indicatorRequest.getFirstPeriod(),
                    indicatorRequest.getInterval());
            double firstIndicatorValue = indicatorService.calculateIndicator(firstIndicator);

            Indicator secondIndicator = createIndicator(indicatorRequest.getSymbol(),
                    indicatorRequest.getSecondIndicatorType(), indicatorRequest.getSecondPeriod(),
                    indicatorRequest.getInterval());
            double secondIndicatorValue = indicatorService.calculateIndicator(secondIndicator);

            Indicator thirdIndicator = createIndicator(indicatorRequest.getSymbol(),
                    indicatorRequest.getThirdIndicatorType(), indicatorRequest.getThirdPeriod(),
                    indicatorRequest.getInterval());
            double thirdIndicatorValue = indicatorService.calculateIndicator(thirdIndicator);

            double currentPrice = indicatorService.getCurrentPrice(indicatorRequest.getSymbol());
            if (currentPrice == 0.0) {
                // Если текущая цена недоступна, используем значение первого индикатора как приближение
                currentPrice = firstIndicatorValue;
            }

            // Get AI advice с учетом периодов (0 для индикаторов без периода)
            String advice = groqChatService.getTradingAdvice(
                    indicatorRequest.getSymbol(), currentPrice,
                    indicatorRequest.getFirstIndicatorType(), firstIndicatorValue,
                    getEffectivePeriod(indicatorRequest.getFirstIndicatorType(), indicatorRequest.getFirstPeriod()),
                    indicatorRequest.getSecondIndicatorType(), secondIndicatorValue,
                    getEffectivePeriod(indicatorRequest.getSecondIndicatorType(), indicatorRequest.getSecondPeriod()),
                    indicatorRequest.getThirdIndicatorType(), thirdIndicatorValue,
                    getEffectivePeriod(indicatorRequest.getThirdIndicatorType(), indicatorRequest.getThirdPeriod())
            );

            // Create and save signal with enum support
            Signal signal = createSignal(indicatorRequest.getSymbol(), advice, currentPrice);
            signalRepository.save(signal);

            // Prepare result
            result.put("signal", signal);
            result.put("firstIndicator", firstIndicator);
            result.put("firstIndicatorValue", firstIndicatorValue);
            result.put("secondIndicator", secondIndicator);
            result.put("secondIndicatorValue", secondIndicatorValue);
            result.put("thirdIndicator", thirdIndicator);
            result.put("thirdIndicatorValue", thirdIndicatorValue);
            result.put("currentPrice", currentPrice);

        } catch (Exception e) {
            log.error("Error generating signals: {}", e.getMessage());
            Signal fallbackSignal = createFallbackSignal(indicatorRequest.getSymbol());
            result.put("signal", fallbackSignal);
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

    private Signal createFallbackSignal(String symbol) {
        Signal signal = new Signal();
        signal.setSymbol(symbol);
        signal.setType("HOLD");
        signal.setDescription("HOLD: Technical analysis temporarily unavailable. Please try again later.");
        signal.setPrice(0.0);
        signal.setDate(LocalDateTime.now());
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