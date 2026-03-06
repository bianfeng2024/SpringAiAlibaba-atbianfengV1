package com.atguigu.study.controller;

import com.atguigu.study.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ChatClient chatClient;
    private final WeatherService weatherService;

    private static final Pattern[] WEATHER_PATTERNS = {
            Pattern.compile(".*天气.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*什么天气.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*会下雨.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*温度.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*冷.*热.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*下雨.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*晴.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*多云.*", Pattern.CASE_INSENSITIVE)
    };

    private static final String AI_PERSONA = "你是一个友好、专业、有趣的AI助手，名字叫'小通'。" +
            "回复时请注意：1) 语言简洁自然，像和朋友聊天一样；2) 适当使用表情符号增加亲和力；3)" +
            "回答问题要准确清晰；4) 如果不确定的事情要诚实说明；5) 保持积极乐观的态度。";

    @Autowired
    public ChatController(ChatModel chatModel, WeatherService weatherService) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(AI_PERSONA)
                .build();
        this.weatherService = weatherService;
    }

    @GetMapping("/hello")
    public String hello() {
        return "Spring AI Alibaba is running!";
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "SAA-01HelloWorld");
        return health;
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam(value = "message", defaultValue = "你好") String message) {
        log.info("SSE聊天请求: {}", message);

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

            String prompt = message;
            if (weatherInfo != null) {
                prompt = message + "\n\n参考信息：" + weatherInfo;
            }

            return chatClient.prompt()
                    .user(prompt)
                    .stream()
                    .content()
                    .map(content -> {
                        // 移除 SSE 格式的 "data:" 前缀
                        if (content != null && content.startsWith("data:")) {
                            return content.substring(5).trim();
                        }
                        return content;
                    })
                    .doOnNext(log::info)
                    .doOnError(e -> log.error("SSE流式输出错误: {}", e.getMessage(), e))
                    .doOnComplete(() -> log.info("SSE流式输出完成"));

        } catch (Exception e) {
            log.error("AI服务调用失败: {}", e.getMessage(), e);
            return Flux.just("抱歉，发生错误: " + e.getMessage());
        }
    }

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

            String prompt = message;
            if (weatherInfo != null) {
                prompt = message + "\n\n参考信息：" + weatherInfo;
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

            if (weatherData != null) {
                result.put("weatherData", weatherData);
            }

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

    private String detectWeatherCity(String message) {
        if (!isWeatherQuery(message)) {
            return null;
        }

        String[] cities = {
                "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安",
                "南京", "重庆", "天津", "苏州", "长沙", "青岛", "大连",
                "厦门", "昆明", "合肥", "郑州", "济南", "沈阳", "哈尔滨",
                "石家庄", "太原", "南昌", "南宁", "贵阳", "兰州", "乌鲁木齐"
        };

        for (String city : cities) {
            if (message.contains(city)) {
                return city;
            }
        }

        if (message.contains("天气")) {
            return "北京";
        }

        return null;
    }

    private boolean isWeatherQuery(String message) {
        for (Pattern pattern : WEATHER_PATTERNS) {
            if (pattern.matcher(message).matches()) {
                return true;
            }
        }
        return false;
    }

    private String formatWeatherInfo(Map<String, Object> weather) {
        if (weather == null || weather.isEmpty()) {
            return "无法获取天气信息";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(weather.get("city")).append("天气预报】\n");
        sb.append("天气：").append(weather.get("weather")).append("\n");
        sb.append("温度：").append(weather.get("temperature")).append("°C\n");
        sb.append("风向：").append(weather.get("winddirection")).append(" ").append(weather.get("windpower")).append("\n");
        sb.append("湿度：").append(weather.get("humidity")).append("\n");
        sb.append("更新时间：").append(weather.get("reporttime"));

        return sb.toString();
    }
}
