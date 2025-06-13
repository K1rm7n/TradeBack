package com.tradeback.config;

import com.tradeback.model.Listing;
import com.tradeback.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ListingRepository listingRepository;
    private final RestTemplate restTemplate;

    @Value("${api.alpha-vantage.key}")
    private String apiKey;

    @Value("${api.alpha-vantage.base-url}")
    private String baseUrl;

    @Override
    public void run(String... args) throws Exception {
        try {
            long existingCount = listingRepository.count();
            if (existingCount == 0) {
                log.info("No symbols found in database, initializing...");

                // СТРАТЕГИЯ: Сначала все символы, потом популярные
                boolean allSymbolsLoaded = loadAllSymbolsFromApi();
                boolean popularSymbolsLoaded = false;

                if (allSymbolsLoaded) {
                    log.info("All symbols loaded from API, now loading popular symbols data...");
                    popularSymbolsLoaded = loadPopularSymbolsFromApi();
                }

                // Если API недоступен, загружаем fallback список
                if (!allSymbolsLoaded) {
                    log.info("API unavailable, loading fallback symbol list");
                    initializeFallbackSymbols();
                }

                log.info("Symbol initialization completed. All symbols loaded: {}, Popular symbols enriched: {}",
                        allSymbolsLoaded, popularSymbolsLoaded);
            } else {
                log.info("Found {} symbols in database, skipping initialization", existingCount);
            }
        } catch (Exception e) {
            log.warn("Could not initialize symbols: {}", e.getMessage());
            // В случае любой ошибки, загружаем минимальный набор символов
            if (listingRepository.count() == 0) {
                initializeFallbackSymbols();
            }
        }
    }

    /**
     * Загружает ВСЕ символы из LISTING_STATUS API (~8000 символов)
     * Для использования в dropdown формах генерации сигналов
     */
    private boolean loadAllSymbolsFromApi() {
        try {
            String url = String.format("%s?function=LISTING_STATUS&apikey=%s&state=active", baseUrl, apiKey);
            log.info("Fetching ALL symbols from Alpha Vantage LISTING_STATUS API...");

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String csvContent = response.getBody();

                // Проверяем, что получили CSV данные, а не ошибку
                if (csvContent.contains("Error Message")) {
                    log.warn("Alpha Vantage API returned error message: {}",
                            csvContent.length() > 200 ? csvContent.substring(0, 200) + "..." : csvContent);
                    return false;
                }

                // Проверяем на предупреждения о лимитах, но не останавливаем загрузку
                if (csvContent.contains("Note")) {
                    log.info("Alpha Vantage API note (proceeding with data loading): {}",
                            csvContent.length() > 200 ? csvContent.substring(0, 200) + "..." : csvContent);
                }

                // Проверяем, что это действительно CSV с данными (должен содержать заголовок)
                if (!csvContent.contains("symbol,name,exchange") || csvContent.split("\n").length < 2) {
                    log.warn("Invalid CSV format received from Alpha Vantage API");
                    return false;
                }

                List<Listing> allListings = parseCsvContent(csvContent);

                if (!allListings.isEmpty()) {
                    listingRepository.saveAll(allListings);
                    log.info("Successfully loaded {} symbols from LISTING_STATUS", allListings.size());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load all symbols from Alpha Vantage: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Загружает ПОПУЛЯРНЫЕ символы через OVERVIEW API
     * Обогащает уже существующие записи детальной информацией
     */
    private boolean loadPopularSymbolsFromApi() {
        try {
            log.info("Loading popular symbols data from OVERVIEW API...");

            // S&P 500 топ-25 по рыночной капитализации
            String[] topSymbols = {
                    "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "BRK.B", "UNH", "JNJ",
                    "JPM", "V", "PG", "XOM", "HD", "CVX", "MA", "PFE", "ABBV", "AVGO",
                    "KO", "COST", "PEP", "TMO", "MRK"
            };

            int updatedCount = 0;
            int maxApiCalls = 12; // Ограничиваем для соблюдения rate limits

            for (int i = 0; i < Math.min(topSymbols.length, maxApiCalls); i++) {
                String symbol = topSymbols[i];

                try {
                    // Получаем детальную информацию о символе
                    String detailedInfo = fetchSymbolDetails(symbol);

                    if (detailedInfo != null) {
                        // Обновляем существующий символ в базе с детальной информацией
                        updateSymbolWithDetails(symbol, detailedInfo);
                        updatedCount++;
                        log.debug("Updated popular symbol: {}", symbol);
                    }

                    // Rate limit: 5 вызовов в минуту для free tier
                    Thread.sleep(13000); // 13 секунд между вызовами

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread interrupted while loading popular symbols");
                    break;
                } catch (Exception e) {
                    log.warn("Failed to fetch details for popular symbol {}: {}", symbol, e.getMessage());
                }
            }

            log.info("Updated {} popular symbols with detailed information", updatedCount);
            return updatedCount > 0;

        } catch (Exception e) {
            log.warn("Failed to load popular symbols details: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Получает детальную информацию о символе через OVERVIEW API
     */
    private String fetchSymbolDetails(String symbol) {
        try {
            String url = String.format("%s?function=OVERVIEW&symbol=%s&apikey=%s",
                    baseUrl, symbol, apiKey);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String jsonResponse = response.getBody();

                // Проверяем, что получили валидные данные
                if (!jsonResponse.contains("Error Message") && !jsonResponse.equals("{}") &&
                        jsonResponse.contains("Name") && jsonResponse.length() > 100) {
                    return jsonResponse;
                }
            }
        } catch (Exception e) {
            log.debug("Error fetching details for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    /**
     * Обновляет существующий символ детальной информацией из OVERVIEW API
     */
    private void updateSymbolWithDetails(String symbol, String jsonResponse) {
        try {
            // Находим существующий символ в базе
            List<Listing> existingListings = listingRepository.findAll();

            for (Listing listing : existingListings) {
                if (listing.getSymbol().equals(symbol)) {
                    // Обновляем с более детальной информацией
                    String detailedName = extractJsonValue(jsonResponse, "Name");
                    String exchange = extractJsonValue(jsonResponse, "Exchange");

                    if (detailedName != null && !detailedName.equals("None") && detailedName.length() > 0) {
                        listing.setName(detailedName);
                    }

                    if (exchange != null && !exchange.equals("None") && exchange.length() > 0) {
                        listing.setExchange(exchange);
                    }

                    listingRepository.save(listing);
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Error updating symbol {} with details: {}", symbol, e.getMessage());
        }
    }

    /**
     * Парсит CSV содержимое от LISTING_STATUS API
     */
    private List<Listing> parseCsvContent(String csvContent) {
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Listing> listings = new ArrayList<>();
        String[] lines = csvContent.split("\n");

        // Пропускаем заголовок (первая строка)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            try {
                String[] values = line.split(",", -1);

                if (values.length >= 7) {
                    Listing listing = new Listing(
                            values[0].trim(),  // symbol
                            values[1].trim(),  // name
                            values[2].trim(),  // exchange
                            values[3].trim(),  // assetType
                            values[4].trim(),  // ipoDate (String)
                            values[5].equals("null") ? null : values[5].trim(), // delistingDate
                            values[6].trim()   // status
                    );
                    listings.add(listing);
                }
            } catch (Exception e) {
                log.debug("Error parsing CSV line {}: {} - {}", i, line, e.getMessage());
            }
        }

        log.info("Parsed {} listings from CSV content", listings.size());
        return listings;
    }

    /**
     * Fallback метод: загружает кураторский список популярных символов
     * Используется когда Alpha Vantage API недоступен
     */
    private void initializeFallbackSymbols() {
        log.info("Initializing fallback symbol list (popular S&P 500 companies)...");

        List<Listing> fallbackSymbols = Arrays.asList(
                new Listing(null, "AAPL", "Apple Inc.", "NASDAQ", "Stock", LocalDate.of(1980, 12, 12), null, "Active"),
                new Listing(null, "MSFT", "Microsoft Corporation", "NASDAQ", "Stock", LocalDate.of(1986, 3, 13), null, "Active"),
                new Listing(null, "GOOGL", "Alphabet Inc.", "NASDAQ", "Stock", LocalDate.of(2004, 8, 19), null, "Active"),
                new Listing(null, "AMZN", "Amazon.com Inc.", "NASDAQ", "Stock", LocalDate.of(1997, 5, 15), null, "Active"),
                new Listing(null, "TSLA", "Tesla Inc.", "NASDAQ", "Stock", LocalDate.of(2010, 6, 29), null, "Active"),
                new Listing(null, "META", "Meta Platforms Inc.", "NASDAQ", "Stock", LocalDate.of(2012, 5, 18), null, "Active"),
                new Listing(null, "NVDA", "NVIDIA Corporation", "NASDAQ", "Stock", LocalDate.of(1999, 1, 22), null, "Active"),
                new Listing(null, "BRK.B", "Berkshire Hathaway Inc.", "NYSE", "Stock", LocalDate.of(1996, 5, 9), null, "Active"),
                new Listing(null, "UNH", "UnitedHealth Group Incorporated", "NYSE", "Stock", LocalDate.of(1984, 10, 17), null, "Active"),
                new Listing(null, "JNJ", "Johnson & Johnson", "NYSE", "Stock", LocalDate.of(1944, 9, 25), null, "Active"),
                new Listing(null, "JPM", "JPMorgan Chase & Co.", "NYSE", "Stock", LocalDate.of(1969, 3, 5), null, "Active"),
                new Listing(null, "V", "Visa Inc.", "NYSE", "Stock", LocalDate.of(2008, 3, 19), null, "Active"),
                new Listing(null, "PG", "The Procter & Gamble Company", "NYSE", "Stock", LocalDate.of(1890, 5, 5), null, "Active"),
                new Listing(null, "XOM", "Exxon Mobil Corporation", "NYSE", "Stock", LocalDate.of(1882, 8, 30), null, "Active"),
                new Listing(null, "HD", "The Home Depot Inc.", "NYSE", "Stock", LocalDate.of(1981, 9, 22), null, "Active"),
                new Listing(null, "CVX", "Chevron Corporation", "NYSE", "Stock", LocalDate.of(1879, 9, 10), null, "Active"),
                new Listing(null, "MA", "Mastercard Incorporated", "NYSE", "Stock", LocalDate.of(2006, 5, 25), null, "Active"),
                new Listing(null, "PFE", "Pfizer Inc.", "NYSE", "Stock", LocalDate.of(1942, 12, 3), null, "Active"),
                new Listing(null, "ABBV", "AbbVie Inc.", "NYSE", "Stock", LocalDate.of(2013, 1, 2), null, "Active"),
                new Listing(null, "AVGO", "Broadcom Inc.", "NASDAQ", "Stock", LocalDate.of(2009, 8, 6), null, "Active"),
                new Listing(null, "KO", "The Coca-Cola Company", "NYSE", "Stock", LocalDate.of(1919, 9, 5), null, "Active"),
                new Listing(null, "COST", "Costco Wholesale Corporation", "NASDAQ", "Stock", LocalDate.of(1985, 12, 5), null, "Active"),
                new Listing(null, "PEP", "PepsiCo Inc.", "NASDAQ", "Stock", LocalDate.of(1965, 12, 30), null, "Active"),
                new Listing(null, "TMO", "Thermo Fisher Scientific Inc.", "NYSE", "Stock", LocalDate.of(2006, 11, 9), null, "Active"),
                new Listing(null, "MRK", "Merck & Co. Inc.", "NYSE", "Stock", LocalDate.of(1946, 6, 21), null, "Active"),

                // Дополнительные популярные символы
                new Listing(null, "NFLX", "Netflix Inc.", "NASDAQ", "Stock", LocalDate.of(2002, 5, 23), null, "Active"),
                new Listing(null, "AMD", "Advanced Micro Devices Inc.", "NASDAQ", "Stock", LocalDate.of(1972, 9, 27), null, "Active"),
                new Listing(null, "INTC", "Intel Corporation", "NASDAQ", "Stock", LocalDate.of(1971, 10, 13), null, "Active"),
                new Listing(null, "CRM", "Salesforce Inc.", "NYSE", "Stock", LocalDate.of(2004, 6, 23), null, "Active"),
                new Listing(null, "ADBE", "Adobe Inc.", "NASDAQ", "Stock", LocalDate.of(1986, 8, 20), null, "Active"),
                new Listing(null, "ORCL", "Oracle Corporation", "NYSE", "Stock", LocalDate.of(1986, 3, 12), null, "Active"),
                new Listing(null, "IBM", "International Business Machines Corporation", "NYSE", "Stock", LocalDate.of(1911, 6, 16), null, "Active"),
                new Listing(null, "PYPL", "PayPal Holdings Inc.", "NASDAQ", "Stock", LocalDate.of(2015, 7, 20), null, "Active"),
                new Listing(null, "BAC", "Bank of America Corporation", "NYSE", "Stock", LocalDate.of(1998, 9, 30), null, "Active"),
                new Listing(null, "WMT", "Walmart Inc.", "NYSE", "Stock", LocalDate.of(1972, 8, 25), null, "Active"),
                new Listing(null, "DIS", "The Walt Disney Company", "NYSE", "Stock", LocalDate.of(1957, 11, 12), null, "Active"),
                new Listing(null, "NOW", "ServiceNow Inc.", "NYSE", "Stock", LocalDate.of(2012, 6, 29), null, "Active"),
                new Listing(null, "QCOM", "QUALCOMM Incorporated", "NASDAQ", "Stock", LocalDate.of(1991, 12, 13), null, "Active"),
                new Listing(null, "CSCO", "Cisco Systems Inc.", "NASDAQ", "Stock", LocalDate.of(1990, 2, 16), null, "Active"),
                new Listing(null, "VZ", "Verizon Communications Inc.", "NYSE", "Stock", LocalDate.of(2000, 7, 3), null, "Active")
        );

        listingRepository.saveAll(fallbackSymbols);
        log.info("Initialized {} fallback symbols", fallbackSymbols.size());
    }

    /**
     * Простой JSON парсер для извлечения значений
     */
    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return null;

            startIndex += searchKey.length();
            // Пропускаем пробелы
            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            if (startIndex >= json.length() || json.charAt(startIndex) != '"') return null;
            startIndex++; // Пропускаем открывающую кавычку

            int endIndex = json.indexOf('"', startIndex);
            if (endIndex == -1) return null;

            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            return null;
        }
    }
}