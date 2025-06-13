package com.tradeback.service;

import com.tradeback.config.ApplicationConstants;
import com.tradeback.model.Listing;
import com.tradeback.model.MarketData;
import com.tradeback.repository.ListingRepository;
import com.tradeback.repository.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketDataRepository marketDataRepository;
    private final ListingRepository listingRepository;
    private final RestTemplate restTemplate;

    @Value("${api.alpha-vantage.key}")
    private String apiKey;

    @Value("${api.alpha-vantage.base-url}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Определяет функцию Alpha Vantage API на основе интервала
     */
    public String getAlphaVantageFunction(String interval) {
        switch (interval.toLowerCase()) {
            case "1min":
            case "5min":
            case "15min":
            case "30min":
            case "60min":
                return "TIME_SERIES_INTRADAY";
            case "daily":
                return "TIME_SERIES_DAILY";
            case "weekly":
                return "TIME_SERIES_WEEKLY";
            case "monthly":
                return "TIME_SERIES_MONTHLY";
            case "daily_adjusted":
                return "TIME_SERIES_DAILY_ADJUSTED";
            case "weekly_adjusted":
                return "TIME_SERIES_WEEKLY_ADJUSTED";
            case "monthly_adjusted":
                return "TIME_SERIES_MONTHLY_ADJUSTED";
            default:
                log.warn("Unknown interval '{}', defaulting to TIME_SERIES_DAILY", interval);
                return "TIME_SERIES_DAILY";
        }
    }

    /**
     * Строит URL для получения данных с правильной функцией API
     */
    public String buildMarketDataUrl(String symbol, String interval) {
        String function = getAlphaVantageFunction(interval);
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("?function=").append(function);
        url.append("&symbol=").append(symbol);

        // Добавляем interval только для intraday данных
        if (function.equals("TIME_SERIES_INTRADAY")) {
            url.append("&interval=").append(interval);
        }

        // Добавляем outputsize для получения полных данных
        url.append("&outputsize=compact"); // compact = последние 100 точек, full = все данные

        url.append("&apikey=").append(apiKey);

        log.debug("Built URL for {} with interval {}: {}", symbol, interval, url.toString());
        return url.toString();
    }

    /**
     * Определяет ключ временного ряда в ответе API
     */
    public String getTimeSeriesKey(String interval) {
        String function = getAlphaVantageFunction(interval);

        switch (function) {
            case "TIME_SERIES_INTRADAY":
                return "Time Series (" + interval + ")";
            case "TIME_SERIES_DAILY":
                return "Time Series (Daily)";
            case "TIME_SERIES_WEEKLY":
                return "Weekly Time Series";
            case "TIME_SERIES_MONTHLY":
                return "Monthly Time Series";
            case "TIME_SERIES_DAILY_ADJUSTED":
                return "Time Series (Daily)";
            case "TIME_SERIES_WEEKLY_ADJUSTED":
                return "Weekly Adjusted Time Series";
            case "TIME_SERIES_MONTHLY_ADJUSTED":
                return "Monthly Adjusted Time Series";
            default:
                return "Time Series (Daily)";
        }
    }

    /**
     * Проверяет, поддерживается ли интервал для VWAP
     */
    public boolean isVWAPCompatible(String interval) {
        return Arrays.asList("1min", "5min", "15min", "30min", "60min")
                .contains(interval.toLowerCase());
    }

    /**
     * Проверяет, является ли интервал внутридневным
     */
    private boolean isIntradayInterval(String interval) {
        return interval.contains("min");
    }

    /**
     * Валидирует комбинацию интервала и индикатора
     */
    public boolean validateIntervalIndicatorCombination(String interval, String indicatorType) {
        // VWAP работает только с intraday интервалами
        if ("VWAP".equals(indicatorType) && !isVWAPCompatible(interval)) {
            log.warn("VWAP indicator is not compatible with interval: {}", interval);
            return false;
        }

        // Все остальные индикаторы работают со всеми интервалами
        return true;
    }

    /**
     * Получает исторические данные с учетом правильного интервала
     */
    public List<MarketData> getHistoricalData(String symbol, String interval) {
        try {
            String url = buildMarketDataUrl(symbol, interval);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (!isValidResponse(response, symbol)) {
                return new ArrayList<>();
            }

            String timeSeriesKey = getTimeSeriesKey(interval);
            Map<String, Object> timeSeries = (Map<String, Object>) response.get(timeSeriesKey);

            if (timeSeries == null) {
                log.warn("No time series data found for {} with interval {} using key '{}'",
                        symbol, interval, timeSeriesKey);
                return new ArrayList<>();
            }

            List<MarketData> dataList = new ArrayList<>();

            for (Map.Entry<String, Object> entry : timeSeries.entrySet()) {
                try {
                    MarketData marketData = parseMarketDataEntry(symbol, entry.getKey(),
                            (Map<String, String>) entry.getValue(), interval);
                    if (marketData != null) {
                        dataList.add(marketData);
                    }
                } catch (Exception e) {
                    log.debug("Error parsing entry for {}: {}", entry.getKey(), e.getMessage());
                }
            }

            // Сортируем по дате (самые новые первыми)
            dataList.sort((a, b) -> b.getDate().compareTo(a.getDate()));

            log.info("Retrieved {} data points for {} with interval {}",
                    dataList.size(), symbol, interval);

            return dataList;

        } catch (Exception e) {
            log.error("Error getting historical data for {} with interval {}: {}",
                    symbol, interval, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Парсит отдельную запись данных о рынке
     */
    private MarketData parseMarketDataEntry(String symbol, String dateStr,
                                            Map<String, String> data, String interval) {
        try {
            MarketData marketData = new MarketData();
            marketData.setSymbol(symbol);

            // Парсим дату в зависимости от интервала
            if (isIntradayInterval(interval)) {
                marketData.setDate(parseDateTime(dateStr));
            } else {
                //FIXME
                marketData.setDate(parseDate(dateStr).atStartOfDay());
            }

            // Парсим OHLCV данные (ключи стандартные для Alpha Vantage)
            marketData.setOpenPrice(Double.parseDouble(data.get("1. open")));
            marketData.setHighPrice(Double.parseDouble(data.get("2. high")));
            marketData.setLowPrice(Double.parseDouble(data.get("3. low")));
            marketData.setClosePrice(Double.parseDouble(data.get("4. close")));
            marketData.setVolume(Long.parseLong(data.get("5. volume")));

            return marketData;

        } catch (Exception e) {
            log.debug("Error parsing market data entry for {}: {}", dateStr, e.getMessage());
            return null;
        }
    }

    /**
     * Получает рекомендации по использованию интервала
     */
    //FIXME
    public List<String> getIntervalRecommendations(String interval) {
        Map<String, List<String>> recommendations = Map.of(
                "1min", Arrays.asList("Scalping", "High-frequency trading", "Market microstructure"),
                "5min", Arrays.asList("Day trading", "Momentum trading", "Quick reversals"),
                "15min", Arrays.asList("Swing entries", "Breakout confirmation", "Intraday trends"),
                "30min", Arrays.asList("Position trading", "Multi-timeframe analysis"),
                "60min", Arrays.asList("Swing trading", "Position entries", "Trend confirmation"),
                "daily", Arrays.asList("Swing trading", "Position trading", "Technical analysis"),
                "weekly", Arrays.asList("Medium-term trading", "Trend analysis", "Sector rotation"),
                "monthly", Arrays.asList("Long-term investing", "Portfolio planning"),
                "daily_adjusted", Arrays.asList("Backtesting", "Historical analysis", "Performance measurement"),
                "weekly_adjusted", Arrays.asList("Historical research", "Long-term backtesting"),
                "monthly_adjusted", Arrays.asList("Investment research", "Long-term performance")
        );

        return recommendations.getOrDefault(interval, Arrays.asList("General analysis"));
    }

    // Existing methods updated to use new interval system

    @Cacheable(ApplicationConstants.CACHE_SYMBOLS)
    public List<Listing> getListOfSymbols() {
        List<Listing> listings = listingRepository.findAll();
        if (listings.isEmpty()) {
            log.info("No symbols found in database");
        }
        log.debug("Retrieved {} symbols for dropdowns", listings.size());
        return listings;
    }

    public List<Listing> getPopularSymbols() {
        try {
            List<String> popularSymbolsList = Arrays.asList(
                    "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "BRK.B", "UNH", "JNJ",
                    "JPM", "V", "PG", "XOM", "HD", "CVX", "MA", "PFE", "ABBV", "AVGO",
                    "KO", "COST", "PEP", "TMO", "MRK"
            );

            List<Listing> allListings = listingRepository.findAll();
            List<Listing> popularListings = new ArrayList<>();

            for (String popularSymbol : popularSymbolsList) {
                allListings.stream()
                        .filter(listing -> listing.getSymbol().equals(popularSymbol))
                        .findFirst()
                        .ifPresent(popularListings::add);
            }

            log.debug("Found {} popular symbols out of {} total", popularListings.size(), allListings.size());

            if (popularListings.isEmpty()) {
                return allListings.stream().limit(25).collect(Collectors.toList());
            }

            return popularListings;

        } catch (Exception e) {
            log.error("Error getting popular symbols: {}", e.getMessage());
            return listingRepository.findAll().stream().limit(25).collect(Collectors.toList());
        }
    }

    public boolean isPopularSymbol(String symbol) {
        List<String> popularSymbols = Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "BRK.B", "UNH", "JNJ",
                "JPM", "V", "PG", "XOM", "HD", "CVX", "MA", "PFE", "ABBV", "AVGO",
                "KO", "COST", "PEP", "TMO", "MRK"
        );
        return popularSymbols.contains(symbol);
    }

    @Cacheable(value = ApplicationConstants.CACHE_MARKET_DATA, key = "#symbol")
    public Iterable<MarketData> getAllMarketData(String symbol) {
        return marketDataRepository.findBySymbolOrderByDateAsc(symbol);
    }

    public MarketData getMarketDataBySymbolAndDate(String symbol, LocalDate date) {
        try {
            return marketDataRepository.findFirstBySymbolAndDate(symbol, date);
        } catch (Exception e) {
            log.error("Error getting market data by date for {} on {}: {}", symbol, date, e.getMessage());
            return null;
        }
    }

    public List<MarketData> getMarketDataByDateRange(String symbol, LocalDate startDate, LocalDate endDate) {
        try {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            return marketDataRepository.findBySymbolAndDateRange(symbol, startDateTime, endDateTime);
        } catch (Exception e) {
            log.error("Error getting market data by date range for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    // Updated methods to use new interval system
    public void saveStockData(String symbol) {
        saveStockData(symbol, "daily"); // Default to daily
    }

    public void saveStockData(String symbol, String interval) {
        try {
            List<MarketData> data = getHistoricalData(symbol, interval);

            if (data.isEmpty()) {
                log.warn("No data retrieved for {} with interval {}", symbol, interval);
                return;
            }

            // Save to database
            marketDataRepository.saveAll(data);
            log.info("Successfully saved {} data points for {} with interval {}",
                    data.size(), symbol, interval);

        } catch (Exception e) {
            log.error("Error saving market data for {} with interval {}: {}",
                    symbol, interval, e.getMessage());
        }
    }

    public List<MarketData> getStockData(String symbol) {
        return getStockData(symbol, "1min"); // Default to 1min for intraday
    }

    public List<MarketData> getStockData(String symbol, String interval) {
        return getHistoricalData(symbol, interval);
    }

    // Helper methods
    private boolean isValidResponse(Map<String, Object> response, String symbol) {
        if (response == null) {
            log.error("No response from API for symbol: {}", symbol);
            return false;
        }

        if (response.containsKey("Error Message")) {
            log.error("API Error for {}: {}", symbol, response.get("Error Message"));
            return false;
        }

        if (response.containsKey("Note")) {
            log.warn("API Limit for {}: {}", symbol, response.get("Note"));
            return false;
        }

        return true;
    }

    private LocalDateTime parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException e) {
            log.error("Failed to parse date: {}", dateStr);
            return LocalDateTime.now();
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }

        try {
            LocalDate date = LocalDate.parse(dateTimeStr.substring(0, 10));
            return date.atStartOfDay();
        } catch (Exception e) {
            log.error("Failed to parse datetime: {}", dateTimeStr);
            return LocalDateTime.now();
        }
    }
}