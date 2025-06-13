package com.tradeback.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeback.model.Indicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class GroqChatService {

    private final RestTemplate restTemplate;

    @Value("${api.groq.key}")
    private String apiKey;

    @Value("${api.groq.endpoint:https://api.groq.com/openai/v1/chat/completions}")
    private String apiEndpoint;

    @Value("${api.groq.model:llama3-70b-8192}")
    private String model;

    public GroqChatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Получает торговые советы на основе трех индикаторов с поддержкой расширенного набора
     */
    public String getTradingAdvice(String symbol, double currentPrice,
                                   String firstIndicatorType, double firstIndicatorValue, int firstPeriod,
                                   String secondIndicatorType, double secondIndicatorValue, int secondPeriod,
                                   String thirdIndicatorType, double thirdIndicatorValue, int thirdPeriod) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            String prompt = buildEnhancedPrompt(symbol, currentPrice,
                    firstIndicatorType, firstIndicatorValue, firstPeriod,
                    secondIndicatorType, secondIndicatorValue, secondPeriod,
                    thirdIndicatorType, thirdIndicatorValue, thirdPeriod);

            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            requestBody.put("messages", new Object[]{message});
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 300);
            requestBody.put("top_p", 0.9);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String response = restTemplate.postForObject(apiEndpoint, entity, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            return root.path("choices").get(0).path("message").path("content").asText().trim();

        } catch (Exception e) {
            System.err.println("Groq API Error: " + e.getMessage());
            e.printStackTrace();

            return generateEnhancedFallbackAdvice(symbol, currentPrice,
                    firstIndicatorValue, secondIndicatorValue, thirdIndicatorValue,
                    firstIndicatorType, secondIndicatorType, thirdIndicatorType);
        }
    }

    /**
     * Построение расширенного промпта с учетом различных типов индикаторов
     */
    private String buildEnhancedPrompt(String symbol, double currentPrice,
                                       String firstIndicatorType, double firstIndicatorValue, int firstPeriod,
                                       String secondIndicatorType, double secondIndicatorValue, int secondPeriod,
                                       String thirdIndicatorType, double thirdIndicatorValue, int thirdPeriod) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a senior quantitative analyst with 20+ years of experience in technical analysis and algorithmic trading. ");
        prompt.append("Analyze the following technical indicators for ").append(symbol).append(" and provide a precise trading recommendation.\n\n");

        prompt.append("MARKET DATA:\n");
        prompt.append("• Symbol: ").append(symbol).append("\n");
        prompt.append("• Current Price: $").append(String.format("%.2f", currentPrice)).append("\n\n");

        prompt.append("TECHNICAL INDICATORS:\n");
        prompt.append("• ").append(getIndicatorDescription(firstIndicatorType, firstPeriod))
                .append(": ").append(String.format("%.4f", firstIndicatorValue)).append("\n");
        prompt.append("• ").append(getIndicatorDescription(secondIndicatorType, secondPeriod))
                .append(": ").append(String.format("%.4f", secondIndicatorValue)).append("\n");
        prompt.append("• ").append(getIndicatorDescription(thirdIndicatorType, thirdPeriod))
                .append(": ").append(String.format("%.4f", thirdIndicatorValue)).append("\n\n");

        prompt.append("ANALYSIS FRAMEWORK:\n");
        prompt.append(getIndicatorAnalysisGuidance(firstIndicatorType, firstIndicatorValue));
        prompt.append(getIndicatorAnalysisGuidance(secondIndicatorType, secondIndicatorValue));
        prompt.append(getIndicatorAnalysisGuidance(thirdIndicatorType, thirdIndicatorValue));

        prompt.append("\nCONSIDER:\n");
        prompt.append("- Indicator convergence/divergence signals\n");
        prompt.append("- Multi-timeframe momentum alignment\n");
        prompt.append("- Risk-reward ratio optimization\n");
        prompt.append("- Market regime and volatility context\n");
        prompt.append("- Position sizing and risk management\n\n");

        prompt.append("REQUIRED OUTPUT FORMAT:\n");
        prompt.append("BUY/SELL/HOLD: [2-3 sentences with specific reasoning based on the indicator values and their interaction]\n");
        prompt.append("Be specific about entry levels, stop-loss, and profit targets when applicable.");

        return prompt.toString();
    }

    /**
     * Получает описание индикатора с учетом периода
     */
    private String getIndicatorDescription(String indicatorType, int period) {
        switch (indicatorType.toUpperCase()) {
            case "SMA":
                return "SMA(" + period + ")";
            case "EMA":
                return "EMA(" + period + ")";
            case "WMA":
                return "WMA(" + period + ")";
            case "DEMA":
                return "DEMA(" + period + ")";
            case "TEMA":
                return "TEMA(" + period + ")";
            case "TRIMA":
                return "TRIMA(" + period + ")";
            case "KAMA":
                return "KAMA(" + period + ")";
            case "MAMA":
                return "MAMA(" + period + ")";
            case "T3":
                return "T3(" + period + ")";
            case "RSI":
                return "RSI(" + period + ")";
            case "STOCH":
                return "Stochastic Oscillator";
            case "STOCHF":
                return "Stochastic Fast";
            case "STOCHRSI":
                return "Stochastic RSI(" + period + ")";
            case "WILLR":
                return "Williams %R(" + period + ")";
            case "CCI":
                return "CCI(" + period + ")";
            case "CMO":
                return "CMO(" + period + ")";
            case "ROC":
                return "ROC(" + period + ")";
            case "MFI":
                return "MFI(" + period + ")";
            case "BOP":
                return "Balance of Power";
            case "MACD":
                return "MACD(12,26,9)";
            case "MACDEXT":
                return "MACD Extended";
            case "PPO":
                return "PPO(" + period + ")";
            case "APO":
                return "APO(" + period + ")";
            case "BBANDS":
                return "Bollinger Bands(" + period + ")";
            case "KELTNER":
                return "Keltner Channel(" + period + ")";
            case "DONCHIAN":
                return "Donchian Channel(" + period + ")";
            case "AD":
                return "Chaikin A/D Line";
            case "ADOSC":
                return "Chaikin A/D Oscillator";
            case "OBV":
                return "On Balance Volume";
            case "VWAP":
                return "VWAP";
            case "ATR":
                return "ATR(" + period + ")";
            case "NATR":
                return "NATR(" + period + ")";
            case "TRANGE":
                return "True Range";
            case "ADX":
                return "ADX(" + period + ")";
            case "ADXR":
                return "ADXR(" + period + ")";
            case "AROON":
                return "Aroon(" + period + ")";
            case "AROONOSC":
                return "Aroon Oscillator(" + period + ")";
            case "DX":
                return "DX(" + period + ")";
            case "MINUS_DI":
                return "Minus DI(" + period + ")";
            case "PLUS_DI":
                return "Plus DI(" + period + ")";
            case "SAR":
                return "Parabolic SAR";
            case "TRIX":
                return "TRIX(" + period + ")";
            default:
                return indicatorType + "(" + period + ")";
        }
    }

    /**
     * Получает руководство по анализу для конкретного индикатора
     */
    private String getIndicatorAnalysisGuidance(String indicatorType, double value) {
        switch (indicatorType.toUpperCase()) {
            case "RSI":
                return "- RSI Analysis: " + String.format("%.2f", value) +
                        " (>70=overbought, <30=oversold, 50=neutral)\n";
            case "STOCH":
            case "STOCHF":
                return "- Stochastic Analysis: " + String.format("%.2f", value) +
                        " (>80=overbought, <20=oversold)\n";
            case "WILLR":
                return "- Williams %R: " + String.format("%.2f", value) +
                        " (>-20=overbought, <-80=oversold)\n";
            case "CCI":
                return "- CCI Analysis: " + String.format("%.2f", value) +
                        " (>100=overbought, <-100=oversold)\n";
            case "MFI":
                return "- Money Flow Index: " + String.format("%.2f", value) +
                        " (>80=overbought, <20=oversold)\n";
            case "MACD":
                return "- MACD: " + String.format("%.4f", value) +
                        " (above signal=bullish, below signal=bearish)\n";
            case "BBANDS":
                return "- Bollinger Bands: Price vs Middle Band " + String.format("%.2f", value) +
                        " (price at upper band=overbought, lower band=oversold)\n";
            case "ATR":
                return "- ATR: " + String.format("%.4f", value) +
                        " (measures volatility, higher=more volatile)\n";
            case "ADX":
                return "- ADX: " + String.format("%.2f", value) +
                        " (>25=strong trend, <20=weak trend)\n";
            case "AROON":
                return "- Aroon: " + String.format("%.2f", value) +
                        " (Aroon Up > Aroon Down = uptrend)\n";
            case "VWAP":
                return "- VWAP: " + String.format("%.2f", value) +
                        " (price above VWAP=bullish, below=bearish)\n";
            case "OBV":
                return "- OBV: " + String.format("%.0f", value) +
                        " (rising OBV=accumulation, falling=distribution)\n";
            case "SMA":
            case "EMA":
            case "WMA":
                return "- Moving Average: " + String.format("%.2f", value) +
                        " (price above MA=bullish, below=bearish)\n";
            default:
                return "- " + indicatorType + ": " + String.format("%.4f", value) + "\n";
        }
    }

    /**
     * Улучшенная fallback логика с поддержкой расширенных индикаторов
     */
    private String generateEnhancedFallbackAdvice(String symbol, double currentPrice,
                                                  double firstIndicator, double secondIndicator, double thirdIndicator,
                                                  String firstType, String secondType, String thirdType) {

        StringBuilder advice = new StringBuilder();
        String signal = "HOLD";
        int bullishSignals = 0;
        int bearishSignals = 0;

        // Анализ первого индикатора
        String firstSignal = analyzeIndicator(firstType, firstIndicator, currentPrice);
        if (firstSignal.equals("BULLISH")) bullishSignals++;
        else if (firstSignal.equals("BEARISH")) bearishSignals++;

        // Анализ второго индикатора
        String secondSignal = analyzeIndicator(secondType, secondIndicator, currentPrice);
        if (secondSignal.equals("BULLISH")) bullishSignals++;
        else if (secondSignal.equals("BEARISH")) bearishSignals++;

        // Анализ третьего индикатора
        String thirdSignal = analyzeIndicator(thirdType, thirdIndicator, currentPrice);
        if (thirdSignal.equals("BULLISH")) bullishSignals++;
        else if (thirdSignal.equals("BEARISH")) bearishSignals++;

        // Определение основного сигнала
        if (bullishSignals >= 2) {
            signal = "BUY";
        } else if (bearishSignals >= 2) {
            signal = "SELL";
        }

        // Построение совета
        advice.append(signal).append(": ");

        switch (signal) {
            case "BUY":
                advice.append("Multiple bullish signals detected (").append(bullishSignals).append("/3). ");
                advice.append(getBullishReasoning(firstType, firstIndicator, secondType, secondIndicator, thirdType, thirdIndicator));
                advice.append(" Consider entering a long position with appropriate risk management.");
                break;
            case "SELL":
                advice.append("Multiple bearish signals detected (").append(bearishSignals).append("/3). ");
                advice.append(getBearishReasoning(firstType, firstIndicator, secondType, secondIndicator, thirdType, thirdIndicator));
                advice.append(" Consider exiting long positions or entering short positions with proper risk controls.");
                break;
            default:
                advice.append("Mixed signals detected. ");
                advice.append(getMixedSignalReasoning(firstType, firstIndicator, secondType, secondIndicator, thirdType, thirdIndicator));
                advice.append(" Wait for clearer directional confirmation before taking action.");
                break;
        }

        return advice.toString();
    }

    /**
     * Анализирует отдельный индикатор и возвращает сигнал
     */
    private String analyzeIndicator(String type, double value, double currentPrice) {
        switch (type.toUpperCase()) {
            case "RSI":
                if (value > 70) return "BEARISH";
                if (value < 30) return "BULLISH";
                return "NEUTRAL";
            case "STOCH":
            case "STOCHF":
                if (value > 80) return "BEARISH";
                if (value < 20) return "BULLISH";
                return "NEUTRAL";
            case "WILLR":
                if (value > -20) return "BEARISH";
                if (value < -80) return "BULLISH";
                return "NEUTRAL";
            case "CCI":
                if (value > 100) return "BEARISH";
                if (value < -100) return "BULLISH";
                return "NEUTRAL";
            case "MFI":
                if (value > 80) return "BEARISH";
                if (value < 20) return "BULLISH";
                return "NEUTRAL";
            case "MACD":
                if (value > 0) return "BULLISH";
                if (value < 0) return "BEARISH";
                return "NEUTRAL";
            case "ADX":
                if (value > 25) return "BULLISH"; // Strong trend
                return "NEUTRAL";
            case "SMA":
            case "EMA":
            case "WMA":
            case "VWAP":
                if (currentPrice > value) return "BULLISH";
                if (currentPrice < value) return "BEARISH";
                return "NEUTRAL";
            default:
                return "NEUTRAL";
        }
    }

    /**
     * Генерирует объяснение для бычьего сигнала
     */
    private String getBullishReasoning(String firstType, double firstValue, String secondType, double secondValue, String thirdType, double thirdValue) {
        StringBuilder reasoning = new StringBuilder();

        if (firstType.equals("RSI") && firstValue < 30) {
            reasoning.append("RSI shows oversold conditions. ");
        }
        if (secondType.contains("MA") || secondType.equals("VWAP")) {
            reasoning.append("Price is above key moving average/VWAP support. ");
        }
        if (thirdType.equals("MACD") && thirdValue > 0) {
            reasoning.append("MACD indicates positive momentum. ");
        }

        if (reasoning.length() == 0) {
            reasoning.append("Technical indicators show bullish convergence. ");
        }

        return reasoning.toString();
    }

    /**
     * Генерирует объяснение для медвежьего сигнала
     */
    private String getBearishReasoning(String firstType, double firstValue, String secondType, double secondValue, String thirdType, double thirdValue) {
        StringBuilder reasoning = new StringBuilder();

        if (firstType.equals("RSI") && firstValue > 70) {
            reasoning.append("RSI indicates overbought conditions. ");
        }
        if (secondType.contains("MA") || secondType.equals("VWAP")) {
            reasoning.append("Price is below key moving average/VWAP resistance. ");
        }
        if (thirdType.equals("MACD") && thirdValue < 0) {
            reasoning.append("MACD shows negative momentum. ");
        }

        if (reasoning.length() == 0) {
            reasoning.append("Technical indicators show bearish convergence. ");
        }

        return reasoning.toString();
    }

    /**
     * Генерирует объяснение для смешанных сигналов
     */
    private String getMixedSignalReasoning(String firstType, double firstValue, String secondType, double secondValue, String thirdType, double thirdValue) {
        return "Technical indicators are providing conflicting signals, suggesting a consolidation phase or potential trend reversal.";
    }

    /**
     * Проверка доступности Groq API
     */
    public boolean isApiAvailable() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> testRequest = new HashMap<>();
            testRequest.put("model", model);
            testRequest.put("messages", new Object[]{
                    Map.of("role", "user", "content", "Hello")
            });
            testRequest.put("max_tokens", 5);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(testRequest, headers);
            restTemplate.postForObject(apiEndpoint, entity, String.class);

            return true;
        } catch (Exception e) {
            System.err.println("Groq API unavailable: " + e.getMessage());
            return false;
        }
    }
}