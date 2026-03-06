package com.atguigu.study.controller;

import com.alibaba.cloud.ai.advisor.DocumentRetrievalAdvisor;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import com.atguigu.study.service.WeatherService;
import com.atguigu.study.service.StockService;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/ai")
public class GaodeRagController {

    private static final Logger log = LoggerFactory.getLogger(GaodeRagController.class);
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ChatClient chatClient;
    private final WeatherService weatherService;
    private final StockService stockService;
    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;
    @Value("${spring.ai.weather.amap.key:}")
    private String amapApiKey;
    // 复用同一个 retriever，避免每次请求重复创建
    private final DocumentRetriever retriever;

    // 天气关键词
    private static final Pattern[] WEATHER_PATTERNS = {
            Pattern.compile("天气", Pattern.CASE_INSENSITIVE),
            Pattern.compile("会下雨", Pattern.CASE_INSENSITIVE),
            Pattern.compile("温度", Pattern.CASE_INSENSITIVE),
            Pattern.compile("冷|热", Pattern.CASE_INSENSITIVE)
    };

    private static final String[] CITIES = {
            "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安",
            "南京", "重庆", "天津", "苏州", "长沙", "青岛", "大连"
    };

    // 股票关键词
    private static final Pattern[] STOCK_PATTERNS = {
            Pattern.compile("股票|行情|股价|涨跌|涨了|跌了|多少钱|市值", Pattern.CASE_INSENSITIVE),
            Pattern.compile("今天.*价格|价格.*今天", Pattern.CASE_INSENSITIVE),
            Pattern.compile("现在.*多少|多少.*现在", Pattern.CASE_INSENSITIVE)
    };

    @Autowired
    public GaodeRagController(ChatModel chatModel, WeatherService weatherService,
            StockService stockService, DashScopeApi dashScopeApi) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个友好的AI助手，名字叫'小通'。能够结合高德天气数据、股票实时行情和知识库信息回答用户问题。")
                .build();
        this.weatherService = weatherService;
        this.stockService = stockService;
        // 在构造时创建一次，复用
        this.retriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName("ops")
                        .build());
    }

    /**
     * 健康检查
     * http://localhost:8019/ai/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "SAA-19GaodeRag");
        health.put("dashscopeApiKeyConfigured", dashscopeApiKey != null && !dashscopeApiKey.isBlank());
        health.put("dashscopeApiKeyMasked", maskKey(dashscopeApiKey));
        health.put("amapApiKeyConfigured", amapApiKey != null && !amapApiKey.isBlank());
        health.put("amapApiKeyMasked", maskKey(amapApiKey));
        return health;
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return "NOT_SET";
        }
        int len = key.length();
        if (len <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(len - 4);
    }

    /**
     * 普通聊天 - 流式输出
     * http://localhost:8019/ai/chat/stream?message=你好
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam(value = "message", defaultValue = "你好") String message) {
        log.info("流式聊天请求: {}", message);

        if (message == null || message.trim().isEmpty()) {
            message = "你好";
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }

        try {
            String weatherCity = detectWeatherCity(message);
            String weatherInfo = null;
            if (weatherCity != null) {
                Map<String, Object> weatherData = weatherService.getWeather(weatherCity);
                weatherInfo = formatWeatherInfo(weatherData);
                log.info("天气查询: {} - {}", weatherCity, weatherInfo);
            }

            String stockCode = detectStockCode(message);
            String stockInfo = null;
            if (stockCode != null) {
                Map<String, Object> quoteData = stockService.getRealtimeQuote(stockCode);
                stockInfo = formatStockInfo(quoteData);
                log.info("股票查询: {} - {}", stockCode, stockInfo);
            }

            String prompt = message;
            if (weatherInfo != null) {
                prompt += "\n\n【天气参考信息】" + weatherInfo;
            }
            if (stockInfo != null) {
                prompt += "\n\n【股票参考信息】" + stockInfo;
            }

            return chatClient.prompt()
                    .user(prompt)
                    .stream()
                    .content()
                    .doOnNext(log::info)
                    .doOnError(e -> log.error("流式输出错误: {}", e.getMessage(), e))
                    .onErrorResume(e -> Flux.just("抱歉，发生错误: " + e.getMessage()))
                    .doOnComplete(() -> log.info("流式输出完成"));

        } catch (Exception e) {
            log.error("AI服务调用失败: {}", e.getMessage(), e);
            return Flux.just("抱歉，发生错误: " + e.getMessage());
        }
    }

    /**
     * 普通聊天 - 非流式输出
     * http://localhost:8019/ai/chat?message=你好
     */
    @GetMapping("/chat")
    public Map<String, Object> chat(@RequestParam(value = "message", defaultValue = "你好") String message) {
        log.info("聊天请求: {}", message);

        if (message == null || message.trim().isEmpty()) {
            message = "你好";
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }

        try {
            String weatherCity = detectWeatherCity(message);
            String weatherInfo = null;
            Map<String, Object> weatherData = null;
            if (weatherCity != null) {
                weatherData = weatherService.getWeather(weatherCity);
                weatherInfo = formatWeatherInfo(weatherData);
                log.info("天气查询: {} - {}", weatherCity, weatherInfo);
            }

            String stockCode = detectStockCode(message);
            String stockInfo = null;
            Map<String, Object> stockData = null;
            if (stockCode != null) {
                stockData = stockService.getRealtimeQuote(stockCode);
                stockInfo = formatStockInfo(stockData);
                log.info("股票查询: {} - {}", stockCode, stockInfo);
            }

            String prompt = message;
            if (weatherInfo != null) {
                prompt += "\n\n【天气参考信息】" + weatherInfo;
            }
            if (stockInfo != null) {
                prompt += "\n\n【股票参考信息】" + stockInfo;
            }

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("AI回复成功");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", message);
            result.put("response", response != null ? response : "AI未给出回复");
            if (weatherData != null)
                result.put("weatherData", weatherData);
            if (stockData != null)
                result.put("stockData", stockData);

            return result;

        } catch (Exception e) {
            log.error("AI服务调用失败: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", "AI服务调用失败");
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * 百炼 RAG 聊天 - 流式输出
     * http://localhost:8019/ai/rag/chat/stream?msg=问题
     */
    @GetMapping(value = "/rag/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ragChatStream(@RequestParam(name = "msg", defaultValue = "你好") String msg) {
        log.info("RAG流式聊天请求: {}", msg);

        return chatClient.prompt()
                .user(msg)
                .advisors(new DocumentRetrievalAdvisor(retriever))
                .stream()
                .content()
                .doOnNext(log::info)
                .doOnError(e -> log.error("RAG流式聊天错误: {}", e.getMessage(), e))
                .onErrorResume(e -> Flux.just("抱歉，RAG服务发生错误: " + e.getMessage()))
                .doOnComplete(() -> log.info("RAG流式聊天完成"));
    }

    /**
     * 百炼 RAG 聊天 - 非流式输出
     * http://localhost:8019/ai/rag/chat?msg=问题
     */
    @GetMapping("/rag/chat")
    public Map<String, Object> ragChat(@RequestParam(name = "msg", defaultValue = "你好") String msg) {
        log.info("RAG聊天请求: {}", msg);

        try {
            String response = chatClient.prompt()
                    .user(msg)
                    .advisors(new DocumentRetrievalAdvisor(retriever))
                    .call()
                    .content();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", msg);
            result.put("response", response != null ? response : "AI未给出回复");

            return result;

        } catch (Exception e) {
            log.error("RAG聊天失败: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", "RAG聊天失败");
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * 高德天气查询接口
     * http://localhost:8019/ai/weather?city=北京
     */
    @GetMapping("/weather")
    public Map<String, Object> getWeather(@RequestParam(name = "city", defaultValue = "北京") String city) {
        log.info("天气查询: {}", city);
        Map<String, Object> result = weatherService.getWeather(city);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        return response;
    }

    private String detectWeatherCity(String message) {
        if (!isWeatherQuery(message))
            return null;
        for (String city : CITIES) {
            if (message.contains(city))
                return city;
        }
        return null;
    }

    private boolean isWeatherQuery(String message) {
        for (Pattern pattern : WEATHER_PATTERNS) {
            if (pattern.matcher(message).find())
                return true;
        }
        return false;
    }

    /**
     * 检测消息中是否包含股票查询意图，并返回股票代码
     * 支持：中文名称（茅台/宁德时代等）、代码（sh600519/600519）
     */
    private String detectStockCode(String message) {
        boolean isStockQuery = false;
        for (Pattern pattern : STOCK_PATTERNS) {
            if (pattern.matcher(message).find()) {
                isStockQuery = true;
                break;
            }
        }
        // 包含中文股票名也算股票查询
        Map<String, String> presets = stockService.getPresetStocks();
        for (String name : presets.keySet()) {
            if (message.contains(name)) {
                // 直接返回对应代码
                return presets.get(name);
            }
        }
        if (!isStockQuery)
            return null;

        // 尝试从消息里提取 sh/sz 开头的代码
        java.util.regex.Matcher m = Pattern.compile("(?i)(sh|sz)\\d{6}").matcher(message);
        if (m.find())
            return m.group();
        // 纯6位数字
        m = Pattern.compile("\\b(\\d{6})\\b").matcher(message);
        if (m.find())
            return m.group();

        return null;
    }

    private String formatWeatherInfo(Map<String, Object> weather) {
        if (weather == null || weather.isEmpty())
            return "无法获取天气信息";
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(weather.get("city")).append("天气预报】\n");
        sb.append("天气：").append(weather.get("weather")).append("\n");
        sb.append("温度：").append(weather.get("temperature")).append("°C\n");
        sb.append("风向：").append(weather.get("winddirection")).append(" ").append(weather.get("windpower")).append("\n");
        sb.append("湿度：").append(weather.get("humidity")).append("\n");
        sb.append("更新时间：").append(weather.get("reporttime"));
        return sb.toString();
    }

    private String formatStockInfo(Map<String, Object> q) {
        if (q == null || Boolean.TRUE.equals(q.get("error"))) {
            return "暂无法获取股票行情";
        }
        return String.format(
                "【%s (%s)】当前价格：%s 元，涨跌：%s（%s），今开：%s，最高：%s，最低：%s，成交量：%s，更新时间：%s %s",
                q.getOrDefault("name", ""), q.getOrDefault("code", ""),
                q.getOrDefault("price", ""), q.getOrDefault("change", ""),
                q.getOrDefault("changePercent", ""), q.getOrDefault("open", ""),
                q.getOrDefault("high", ""), q.getOrDefault("low", ""),
                q.getOrDefault("volume", ""), q.getOrDefault("date", ""),
                q.getOrDefault("time", ""));
    }
}
