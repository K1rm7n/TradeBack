package com.tradeback.service;

import com.tradeback.model.Indicator;
import com.tradeback.model.MarketData;
import com.tradeback.repository.MarketDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class IndicatorService {

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${api.alpha-vantage.key}")
    private String apiKey;

    private static final String BASE_URL = "https://www.alphavantage.co/query";

    /**
     * Универсальный метод для расчета любого технического индикатора
     */
    public double calculateIndicator(Indicator indicator) {
        try {
            String url = buildIndicatorUrl(indicator);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (!isValidApiResponse(response)) {
                return 0.0;
            }

            return extractIndicatorValue(response, indicator);

        } catch (Exception e) {
            System.err.println("Error calculating indicator: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    /**
     * Расчет комплексных индикаторов (MACD, Bollinger Bands, Stochastic)
     */
    public Indicator calculateComplexIndicator(Indicator indicator) {
        try {
            String url = buildIndicatorUrl(indicator);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (!isValidApiResponse(response)) {
                return indicator;
            }

            return extractComplexIndicatorValues(response, indicator);

        } catch (Exception e) {
            System.err.println("Error calculating complex indicator: " + e.getMessage());
            e.printStackTrace();
            return indicator;
        }
    }

    /**
     * Построение URL для API запроса в зависимости от типа индикатора
     */
    private String buildIndicatorUrl(Indicator indicator) {
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append("?function=").append(indicator.getType().name());
        url.append("&symbol=").append(indicator.getSymbol());
        url.append("&interval=").append(indicator.getInterval());

        // Добавляем параметры в зависимости от типа индикатора
        switch (indicator.getType()) {
            case MACD:
                url.append("&fastperiod=12&slowperiod=26&signalperiod=9");
                url.append("&series_type=close");
                break;

            case MACDEXT:
                url.append("&fastperiod=12&slowperiod=26&signalperiod=9");
                url.append("&fastmatype=0&slowmatype=0&signalmatype=0");
                url.append("&series_type=close");
                break;

            case BBANDS:
                url.append("&time_period=").append(indicator.getPeriod());
                url.append("&series_type=close");
                url.append("&nbdevup=2&nbdevdn=2&matype=0");
                break;

            case STOCH:
                url.append("&fastkperiod=5&slowkperiod=3&slowdperiod=3");
                url.append("&slowkmatype=0&slowdmatype=0");
                break;

            case STOCHF:
                url.append("&fastkperiod=5&fastdperiod=3");
                url.append("&fastdmatype=0");
                break;

            case STOCHRSI:
                url.append("&time_period=").append(indicator.getPeriod());
                url.append("&series_type=close");
                url.append("&fastkperiod=5&fastdperiod=3&fastdmatype=0");
                break;

            case SAR:
                url.append("&acceleration=0.02&maximum=0.20");
                break;

            case AROON:
                url.append("&time_period=").append(indicator.getPeriod());
                break;

            case ADXR:
                url.append("&time_period=").append(indicator.getPeriod());
                break;

            case VWAP:
                // VWAP only works with intraday intervals
                if (!indicator.getInterval().contains("min")) {
                    throw new IllegalArgumentException("VWAP requires intraday interval (1min, 5min, etc.)");
                }
                break;

            // Большинство остальных индикаторов используют стандартные параметры
            default:
                if (needsTimePeriod(indicator.getType())) {
                    url.append("&time_period=").append(indicator.getPeriod());
                }
                if (needsSeriesType(indicator.getType())) {
                    url.append("&series_type=close");
                }
                break;
        }

        url.append("&apikey=").append(apiKey);
        return url.toString();
    }

    /**
     * Проверяет, нужен ли параметр time_period для данного индикатора
     */
    private boolean needsTimePeriod(Indicator.IndicatorType type) {
        return type != Indicator.IndicatorType.MACD &&
                type != Indicator.IndicatorType.MACDEXT &&
                type != Indicator.IndicatorType.STOCH &&
                type != Indicator.IndicatorType.STOCHF &&
                type != Indicator.IndicatorType.SAR &&
                type != Indicator.IndicatorType.VWAP &&
                type != Indicator.IndicatorType.AROON &&
                type != Indicator.IndicatorType.OBV &&
                type != Indicator.IndicatorType.AD;
    }

    /**
     * Проверяет, нужен ли параметр series_type для данного индикатора
     */
    private boolean needsSeriesType(Indicator.IndicatorType type) {
        // Большинство индикаторов используют series_type, кроме объемных и некоторых других
        return type != Indicator.IndicatorType.OBV &&
                type != Indicator.IndicatorType.AD &&
                type != Indicator.IndicatorType.ADOSC &&
                type != Indicator.IndicatorType.VWAP &&
                type != Indicator.IndicatorType.STOCH &&
                type != Indicator.IndicatorType.STOCHF &&
                type != Indicator.IndicatorType.SAR;
    }

    /**
     * Извлекает значение простого индикатора из ответа API
     */
    private double extractIndicatorValue(Map<String, Object> response, Indicator indicator) {
        String timeSeriesKey = "Technical Analysis: " + indicator.getType().name();
        Map<String, Object> timeSeries = (Map<String, Object>) response.get(timeSeriesKey);

        if (timeSeries == null) {
            System.err.println("No time series data found with key: " + timeSeriesKey);
            return 0.0;
        }

        // Получаем последнее доступное значение
        String latestDate = getLatestDate(timeSeries.keySet());
        if (latestDate == null) {
            return 0.0;
        }

        Map<String, String> indicatorData = (Map<String, String>) timeSeries.get(latestDate);
        String valueKey = getValueKey(indicator.getType());
        String valueStr = indicatorData.get(valueKey);

        if (valueStr != null) {
            double value = Double.parseDouble(valueStr);
            indicator.setValue(value);
            indicator.setCalculatedAt(LocalDateTime.now());
            return value;
        }

        return 0.0;
    }

    /**
     * Извлекает значения комплексного индикатора из ответа API
     */
    private Indicator extractComplexIndicatorValues(Map<String, Object> response, Indicator indicator) {
        String timeSeriesKey = "Technical Analysis: " + indicator.getType().name();
        Map<String, Object> timeSeries = (Map<String, Object>) response.get(timeSeriesKey);

        if (timeSeries == null) {
            return indicator;
        }

        String latestDate = getLatestDate(timeSeries.keySet());
        if (latestDate == null) {
            return indicator;
        }

        Map<String, String> indicatorData = (Map<String, String>) timeSeries.get(latestDate);

        switch (indicator.getType()) {
            case MACD:
            case MACDEXT:
                extractMACDValues(indicatorData, indicator);
                break;
            case BBANDS:
                extractBollingerBandsValues(indicatorData, indicator);
                break;
            case STOCH:
            case STOCHF:
                extractStochasticValues(indicatorData, indicator);
                break;
            case AROON:
                extractAroonValues(indicatorData, indicator);
                break;
            default:
                // Для неизвестных комплексных индикаторов пытаемся извлечь первое значение
                String firstKey = indicatorData.keySet().iterator().next();
                if (firstKey != null) {
                    indicator.setValue(Double.parseDouble(indicatorData.get(firstKey)));
                }
                break;
        }

        indicator.setCalculatedAt(LocalDateTime.now());
        return indicator;
    }

    /**
     * Извлечение значений MACD
     */
    private void extractMACDValues(Map<String, String> data, Indicator indicator) {
        String macdValue = data.get("MACD");
        String signalValue = data.get("MACD_Signal");
        String histogramValue = data.get("MACD_Hist");

        if (macdValue != null) indicator.setValue(Double.parseDouble(macdValue));
        if (signalValue != null) indicator.setSecondaryValue(Double.parseDouble(signalValue));
        if (histogramValue != null) indicator.setTertiaryValue(Double.parseDouble(histogramValue));
    }

    /**
     * Извлечение значений Bollinger Bands
     */
    private void extractBollingerBandsValues(Map<String, String> data, Indicator indicator) {
        String middleBand = data.get("Real Middle Band");
        String upperBand = data.get("Real Upper Band");
        String lowerBand = data.get("Real Lower Band");

        if (middleBand != null) indicator.setValue(Double.parseDouble(middleBand));
        if (upperBand != null) indicator.setSecondaryValue(Double.parseDouble(upperBand));
        if (lowerBand != null) indicator.setTertiaryValue(Double.parseDouble(lowerBand));
    }

    /**
     * Извлечение значений Stochastic
     */
    private void extractStochasticValues(Map<String, String> data, Indicator indicator) {
        String kValue = data.get("SlowK") != null ? data.get("SlowK") : data.get("FastK");
        String dValue = data.get("SlowD") != null ? data.get("SlowD") : data.get("FastD");

        if (kValue != null) indicator.setValue(Double.parseDouble(kValue));
        if (dValue != null) indicator.setSecondaryValue(Double.parseDouble(dValue));
    }

    /**
     * Извлечение значений Aroon
     */
    private void extractAroonValues(Map<String, String> data, Indicator indicator) {
        String aroonUp = data.get("Aroon Up");
        String aroonDown = data.get("Aroon Down");

        if (aroonUp != null) indicator.setValue(Double.parseDouble(aroonUp));
        if (aroonDown != null) indicator.setSecondaryValue(Double.parseDouble(aroonDown));
    }

    /**
     * Получает ключ для извлечения значения в зависимости от типа индикатора
     */
    private String getValueKey(Indicator.IndicatorType type) {
        switch (type) {
            case RSI:
                return "RSI";
            case SMA:
                return "SMA";
            case EMA:
                return "EMA";
            case WMA:
                return "WMA";
            case WILLR:
                return "WILLR";
            case CCI:
                return "CCI";
            case ATR:
                return "ATR";
            case ADX:
                return "ADX";
            case MFI:
                return "MFI";
            case OBV:
                return "OBV";
            case AD:
                return "Chaikin A/D";
            default:
                return type.name();
        }
    }

    /**
     * Получает последнюю доступную дату из набора дат
     */
    private String getLatestDate(java.util.Set<String> dates) {
        return dates.stream()
                .sorted((d1, d2) -> d2.compareTo(d1)) // Сортировка в убывающем порядке
                .findFirst()
                .orElse(null);
    }

    /**
     * Проверяет валидность ответа от API
     */
    private boolean isValidApiResponse(Map<String, Object> response) {
        if (response == null) {
            System.err.println("No response from Alpha Vantage API");
            return false;
        }

        if (response.containsKey("Error Message")) {
            System.err.println("Alpha Vantage API Error: " + response.get("Error Message"));
            return false;
        }

        if (response.containsKey("Note")) {
            System.err.println("Alpha Vantage API Note: " + response.get("Note"));
            return false;
        }

        return true;
    }
}