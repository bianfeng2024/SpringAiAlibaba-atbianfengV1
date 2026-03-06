package com.atguigu.study.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final RestTemplate restTemplate;

    // 常用股票代码映射（中文名 -> 代码）
    private static final Map<String, String> NAME_TO_CODE = new LinkedHashMap<>();

    static {
        NAME_TO_CODE.put("茅台", "sh600519");
        NAME_TO_CODE.put("贵州茅台", "sh600519");
        NAME_TO_CODE.put("宁德时代", "sz300750");
        NAME_TO_CODE.put("比亚迪", "sz002594");
        NAME_TO_CODE.put("工商银行", "sh601398");
        NAME_TO_CODE.put("平安银行", "sz000001");
        NAME_TO_CODE.put("招商银行", "sh600036");
        NAME_TO_CODE.put("腾讯", "hk00700");
        NAME_TO_CODE.put("阿里巴巴", "us.BABA");
        NAME_TO_CODE.put("上证指数", "sh000001");
        NAME_TO_CODE.put("沪深300", "sh000300");
        NAME_TO_CODE.put("创业板", "sz399006");
    }

    public StockService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(8))
                .build();
    }

    /**
     * 解析股票代码：支持直接输入代码或中文名称
     */
    public String resolveCode(String input) {
        if (input == null)
            return null;
        String trimmed = input.trim();
        // 直接是代码格式（sh/sz 开头或纯数字6位）
        if (trimmed.matches("(?i)(sh|sz|hk)\\d+.*") || trimmed.matches("\\d{6}")) {
            // 纯数字6位，猜测前缀
            if (trimmed.matches("\\d{6}")) {
                char first = trimmed.charAt(0);
                return (first == '6') ? "sh" + trimmed : "sz" + trimmed;
            }
            return trimmed.toLowerCase();
        }
        // 中文名称查映射
        for (Map.Entry<String, String> entry : NAME_TO_CODE.entrySet()) {
            if (trimmed.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return trimmed;
    }

    /**
     * 获取实时行情（新浪财经）
     * 返回字段：code, name, price, change, changePercent, open, high, low,
     * volume, amount, preClose, date, time
     */
    public Map<String, Object> getRealtimeQuote(String code) {
        String resolvedCode = resolveCode(code);
        log.info("查询实时行情: {} -> {}", code, resolvedCode);

        try {
            String url = "https://hq.sinajs.cn/list=" + resolvedCode;
            HttpHeaders headers = new HttpHeaders();
            // 新浪需要 Referer
            headers.set("Referer", "https://finance.sina.com.cn");
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            log.info("新浪行情响应: {}", body);

            return parseSinaQuote(resolvedCode, body);

        } catch (Exception e) {
            log.error("查询实时行情失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", true);
            err.put("message", "查询失败: " + e.getMessage());
            err.put("code", resolvedCode);
            return err;
        }
    }

    /**
     * 解析新浪行情数据
     * 格式: var hq_str_sh600519="贵州茅台,1700.00,1695.00,1710.50,1720.00,1695.00,..."
     */
    private Map<String, Object> parseSinaQuote(String code, String body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);

        if (body == null || body.trim().isEmpty() || body.contains("\"\"")) {
            result.put("error", true);
            result.put("message", "股票代码不存在或暂无数据");
            return result;
        }

        // 提取引号内内容
        Pattern p = Pattern.compile("\"([^\"]+)\"");
        Matcher m = p.matcher(body);
        if (!m.find()) {
            result.put("error", true);
            result.put("message", "数据格式异常");
            return result;
        }

        String[] fields = m.group(1).split(",");
        if (fields.length < 32) {
            result.put("error", true);
            result.put("message", "返回字段不足，股票代码可能有误");
            return result;
        }

        try {
            result.put("error", false);
            result.put("name", fields[0]);
            result.put("open", fields[1]);
            result.put("preClose", fields[2]);
            result.put("price", fields[3]);
            result.put("high", fields[4]);
            result.put("low", fields[5]);
            result.put("volume", formatVolume(fields[8])); // 手
            result.put("amount", formatAmount(fields[9])); // 元
            result.put("date", fields[30]);
            result.put("time", fields[31]);

            // 计算涨跌
            double price = Double.parseDouble(fields[3]);
            double preClose = Double.parseDouble(fields[2]);
            double change = price - preClose;
            double changePct = preClose > 0 ? (change / preClose * 100) : 0;
            result.put("change", String.format("%.2f", change));
            result.put("changePercent", String.format("%.2f%%", changePct));
            result.put("upDown", change >= 0 ? "up" : "down");
        } catch (Exception e) {
            log.warn("解析行情字段异常: {}", e.getMessage());
            result.put("error", true);
            result.put("message", "数据解析异常");
        }
        return result;
    }

    /**
     * 获取历史K线记录（腾讯财经）
     * 返回列表，每条: date, open, close, high, low, volume, changePercent
     */
    public Map<String, Object> getTradeHistory(String code, int days) {
        String resolvedCode = resolveCode(code);
        if (days <= 0 || days > 90)
            days = 10;
        log.info("查询历史K线: {} -> {}, {}天", code, resolvedCode, days);

        try {
            // 腾讯前复权K线接口
            String url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
                    + "?_var=kline_day&param=" + resolvedCode + ",day,,," + days + ",qfq";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", "https://gu.qq.com");
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            log.info("腾讯K线响应（前200字）: {}", body != null && body.length() > 200 ? body.substring(0, 200) : body);

            return parseTencentKline(resolvedCode, body);

        } catch (Exception e) {
            log.error("查询历史K线失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", true);
            err.put("message", "查询失败: " + e.getMessage());
            err.put("code", resolvedCode);
            return err;
        }
    }

    /**
     * 解析腾讯K线数据
     * 格式: kline_day={...,
     * "data":{"sh600519":{"day":[["2024-01-02","1700","1720","1750","1695","100000","1.2"],...],...}}}
     */
    private Map<String, Object> parseTencentKline(String code, String body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);

        if (body == null || body.trim().isEmpty()) {
            result.put("error", true);
            result.put("message", "暂无数据");
            return result;
        }

        try {
            // 去掉 JSONP 包装: kline_day={...}
            String json = body.replaceFirst("^kline_day=", "").trim();
            if (json.endsWith(";"))
                json = json.substring(0, json.length() - 1);

            // 用简单字符串解析提取 day 数组（避免引入JSON库依赖）
            // 腾讯返回格式固定，用正则提取即可
            Pattern dayPattern = Pattern.compile("\\[(\"[^]]+\")\\]");
            Matcher m = dayPattern.matcher(json);

            List<Map<String, String>> records = new ArrayList<>();
            while (m.find()) {
                String rowStr = m.group(1);
                // 拆分: "2024-01-02","1700.00","1720.00","1750.00","1695.00","12345","1.2"
                String[] parts = rowStr.split("\",\"");
                if (parts.length < 6)
                    continue;
                // 去掉首尾引号
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].replace("\"", "");
                }
                Map<String, String> row = new LinkedHashMap<>();
                row.put("date", parts[0]);
                row.put("open", parts[1]);
                row.put("close", parts[2]);
                row.put("high", parts[3]);
                row.put("low", parts[4]);
                row.put("volume", formatVolume(parts[5]));
                if (parts.length > 6)
                    row.put("changePercent", parts[6] + "%");
                // 过滤掉 date 不符合日期格式的脏数据行（如股票名称误入数组）
                if (parts[0].matches("\\d{4}-\\d{2}-\\d{2}")) {
                    records.add(row);
                }
            }

            if (records.isEmpty()) {
                result.put("error", true);
                result.put("message", "未找到K线数据，请检查股票代码");
                return result;
            }

            result.put("error", false);
            result.put("records", records);
            result.put("count", records.size());
        } catch (Exception e) {
            log.error("解析K线数据异常: {}", e.getMessage(), e);
            result.put("error", true);
            result.put("message", "数据解析异常: " + e.getMessage());
        }
        return result;
    }

    private String formatVolume(String vol) {
        try {
            long v = Double.valueOf(vol).longValue();
            if (v >= 100_000_000)
                return String.format("%.2f亿手", v / 100_000_000.0);
            if (v >= 10_000)
                return String.format("%.2f万手", v / 10_000.0);
            return v + "手";
        } catch (Exception e) {
            return vol;
        }
    }

    private String formatAmount(String amt) {
        try {
            double a = Double.parseDouble(amt);
            if (a >= 100_000_000)
                return String.format("%.2f亿元", a / 100_000_000);
            if (a >= 10_000)
                return String.format("%.2f万元", a / 10_000);
            return String.format("%.2f元", a);
        } catch (Exception e) {
            return amt;
        }
    }

    /**
     * 获取支持的市场列表（用于前端提示）
     */
    public Map<String, String> getPresetStocks() {
        return Collections.unmodifiableMap(NAME_TO_CODE);
    }
}
