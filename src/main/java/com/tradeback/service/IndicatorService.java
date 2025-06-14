package com.tradeback.service;

import com.tradeback.model.Indicator;
import com.tradeback.model.MarketData;
import com.tradeback.repository.MarketDataRepository;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class IndicatorService {

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MarketDataService marketDataService;

    @Value("${api.alpha-vantage.key}")
    private String apiKey;

    private static final String BASE_URL = "https://www.alphavantage.co/query";

    /**
     * Универсальный метод для расчета любого технического индикатора
     * с поддержкой правильных Alpha Vantage интервалов
     */
    public double calculateIndicator(Indicator indicator) {
        try {
            // Валидируем комбинацию интервала и индикатора
            if (!marketDataService.validateIntervalIndicatorCombination(
                    indicator.getInterval(), indicator.getType().name())) {
                log.warn("Invalid combination: {} with interval {}",
                        indicator.getType(), indicator.getInterval());
                return 0.0;
            }

            String url = buildIndicatorUrl(indicator);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (!isValidApiResponse(response)) {
                return 0.0;
            }

            return extractIndicatorValue(response, indicator);

        } catch (Exception e) {
            log.error("Error calculating indicator: {}", e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    /**
     * Расчет комплексных индикаторов (MACD, Bollinger Bands, Stochastic)
     */
    public Indicator calculateComplexIndicator(Indicator indicator) {
        try {
            if (!marketDataService.validateIntervalIndicatorCombination(
                    indicator.getInterval(), indicator.getType().name())) {
                return indicator;
            }

            String url = buildIndicatorUrl(indicator);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (!isValidApiResponse(response)) {
                return indicator;
            }

            return extractComplexIndicatorValues(response, indicator);

        } catch (Exception e) {
            log.error("Error calculating complex indicator: {}", e.getMessage());
            e.printStackTrace();
            return indicator;
        }
    }

    /**
     * Построение URL для API запроса с учетом Alpha Vantage интервалов
     */
    private String buildIndicatorUrl(Indicator indicator) {
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append("?function=").append(indicator.getType().name());
        url.append("&symbol=").append(indicator.getSymbol());

        // Добавляем interval для технических индикаторов
        url.append("&interval=").append(convertIntervalForIndicators(indicator.getInterval()));

        // Добавляем параметры в зависимости от типа индикатора
        switch (indicator.getType()) {
            case MACD:
                url.append("&fastperiod=12&slowperiod=26&signalperiod=9");
                url.append("&series_type=close");
                break;

            case BBANDS:
                // Для Bollinger Bands используем период если он больше 0, иначе 20
                int bbandsPeriod = indicator.getPeriod() > 0 ? indicator.getPeriod() : 20;
                url.append("&time_period=").append(bbandsPeriod);
                url.append("&series_type=close");
                url.append("&nbdevup=2&nbdevdn=2&matype=0");
                break;

            case STOCH:
                url.append("&fastkperiod=5&slowkperiod=3&slowdperiod=3");
                url.append("&slowkmatype=0&slowdmatype=0");
                break;

            case VWAP:
                // VWAP имеет особые требования к интервалам
                if (!marketDataService.isVWAPCompatible(indicator.getInterval())) {
                    throw new IllegalArgumentException("VWAP requires intraday interval");
                }
                break;

            case SAR:
                url.append("&acceleration=0.02&maximum=0.20");
                break;

            // Большинство остальных индикаторов используют стандартные параметры
            default:
                if (needsTimePeriod(indicator.getType()) && indicator.getPeriod() > 0) {
                    url.append("&time_period=").append(indicator.getPeriod());
                }
                if (needsSeriesType(indicator.getType())) {
                    url.append("&series_type=close");
                }
                break;
        }

        url.append("&apikey=").append(apiKey);

        log.debug("Built indicator URL: {}", url.toString());
        return url.toString();
    }

    /**
     * Конвертирует интервалы приложения в интервалы для технических индикаторов
     * Технические индикаторы Alpha Vantage поддерживают другой набор интервалов
     */
    private String convertIntervalForIndicators(String appInterval) {
        switch (appInterval.toLowerCase()) {
            case "1min":
            case "5min":
            case "15min":
            case "30min":
            case "60min":
                return appInterval; // Intraday интервалы остаются как есть
            case "daily":
            case "daily_adjusted":
                return "daily";
            case "weekly":
            case "weekly_adjusted":
                return "weekly";
            case "monthly":
            case "monthly_adjusted":
                return "monthly";
            default:
                log.warn("Unknown interval for indicators: {}, defaulting to daily", appInterval);
                return "daily";
        }
    }

    /**
     * Проверяет, нужен ли параметр time_period для данного индикатора
     */
    private boolean needsTimePeriod(Indicator.IndicatorType type) {
        return type != Indicator.IndicatorType.MACD &&
                type != Indicator.IndicatorType.STOCH &&
                type != Indicator.IndicatorType.SAR &&
                type != Indicator.IndicatorType.VWAP &&
                type != Indicator.IndicatorType.OBV;
    }

    /**
     * Проверяет, нужен ли параметр series_type для данного индикатора
     */
    private boolean needsSeriesType(Indicator.IndicatorType type) {
        return type != Indicator.IndicatorType.OBV &&
                type != Indicator.IndicatorType.VWAP &&
                type != Indicator.IndicatorType.STOCH &&
                type != Indicator.IndicatorType.SAR;
    }

    /**
     * Извлекает значение простого индикатора из ответа API
     */
    private double extractIndicatorValue(Map<String, Object> response, Indicator indicator) {
        String timeSeriesKey = "Technical Analysis: " + indicator.getType().name();
        Map<String, Object> timeSeries = (Map<String, Object>) response.get(timeSeriesKey);

        if (timeSeries == null) {
            log.error("No time series data found with key: {}", timeSeriesKey);
            log.debug("Available keys: {}", response.keySet());
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
            log.debug("Extracted {} value: {}", indicator.getType(), value);
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
                extractMACDValues(indicatorData, indicator);
                break;
            case BBANDS:
                extractBollingerBandsValues(indicatorData, indicator);
                break;
            case STOCH:
                extractStochasticValues(indicatorData, indicator);
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
            case DEMA:
                return "DEMA";
            case TEMA:
                return "TEMA";
            case TRIMA:
                return "TRIMA";
            case KAMA:
                return "KAMA";
            case MAMA:
                return "MAMA";
            case T3:
                return "T3";
            case WILLR:
                return "WILLR";
            case CCI:
                return "CCI";
            case CMO:
                return "CMO";
            case ROC:
                return "ROC";
            case ROCP:
                return "ROCP";
            case ROCR:
                return "ROCR";
            case MFI:
                return "MFI";
            case BOP:
                return "BOP";
            case ATR:
                return "ATR";
            case NATR:
                return "NATR";
            case TRANGE:
                return "TRANGE";
            case ADX:
                return "ADX";
            case ADXR:
                return "ADXR";
            case DX:
                return "DX";
            case MINUS_DI:
                return "MINUS_DI";
            case PLUS_DI:
                return "PLUS_DI";
            case MINUS_DM:
                return "MINUS_DM";
            case PLUS_DM:
                return "PLUS_DM";
            case SAR:
                return "SAR";
            case TRIX:
                return "TRIX";
            case OBV:
                return "OBV";
            case AD:
                return "Chaikin A/D";
            case ADOSC:
                return "ADOSC";
            case VWAP:
                return "VWAP";
            case AVGPRICE:
                return "AVGPRICE";
            case MEDPRICE:
                return "MEDPRICE";
            case TYPPRICE:
                return "TYPPRICE";
            case WCLPRICE:
                return "WCLPRICE";
            case HT_DCPERIOD:
                return "HT_DCPERIOD";
            case HT_DCPHASE:
                return "HT_DCPHASE";
            case HT_TRENDMODE:
                return "HT_TRENDMODE";
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
            log.error("No response from Alpha Vantage API");
            return false;
        }

        if (response.containsKey("Error Message")) {
            log.error("Alpha Vantage API Error: {}", response.get("Error Message"));
            return false;
        }

        if (response.containsKey("Note")) {
            log.warn("Alpha Vantage API Note: {}", response.get("Note"));
            return false;
        }

        return true;
    }

    /**
     * Вспомогательный метод для получения последних рыночных данных
     */
    public MarketData getLatestMarketData(String symbol) {
        List<MarketData> dataList = marketDataRepository.findTopBySymbolOrderByDateDesc(symbol);
        return dataList.isEmpty() ? null : dataList.get(0);
    }

    /**
     * Метод для получения текущей цены из индикаторов или рыночных данных
     */
    public double getCurrentPrice(String symbol) {
        // Пытаемся получить текущую цену из последних рыночных данных
        MarketData latestData = getLatestMarketData(symbol);
        if (latestData != null) {
            return latestData.getClosePriceAsDouble();
        }

        // Если данных нет, возвращаем 0
        log.warn("No current price data available for symbol: {}", symbol);
        return 0.0;
    }

    /**
     * Проверяет поддерживается ли указанный индикатор
     */
    public boolean isIndicatorSupported(String indicatorType) {
        try {
            Indicator.IndicatorType.valueOf(indicatorType.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Получает описание индикатора
     */
    public String getIndicatorDescription(Indicator.IndicatorType type) {
        return type.getDescription();
    }

    /**
     * Проверяет, является ли индикатор комплексным (возвращающим несколько значений)
     */
    public boolean isComplexIndicator(Indicator.IndicatorType type) {
        return type.isComplexIndicator();
    }
}