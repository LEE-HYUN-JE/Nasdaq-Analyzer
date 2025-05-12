package com.nasdaq.analyzer.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NewsCollector implements RequestHandler<ScheduledEvent, String> {
    private static final String BUCKET_NAME = "nasdaq-analyzer-data";
    private static final String FINNHUB_API_KEY = System.getenv("FINNHUB_API_KEY");
    private static final String NEWS_API_KEY = System.getenv("NEWS_API_KEY");
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3Client s3Client = S3Client.builder()
        .region(Region.AP_NORTHEAST_2)  // Seoul Region
        .build();

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        try {
            context.getLogger().log("Starting news collection process at " + LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            
            // 1. Collect news from multiple sources
            List<NewsItem> newsItems = new ArrayList<>();
            newsItems.addAll(collectMarketNews());
            newsItems.addAll(collectStockNews());
            
            // 2. Generate summary using OpenAI
            String summary = generateSummary(newsItems);
            
            // 3. Save to S3
            saveToS3(newsItems, summary);
            
            return "Successfully processed " + newsItems.size() + " news items";
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            throw new RuntimeException("Failed to process news", e);
        }
    }

    private List<NewsItem> collectMarketNews() throws IOException {
        List<NewsItem> newsItems = new ArrayList<>();
        String url = String.format("https://newsapi.org/v2/everything?q=nasdaq market&language=en&sortBy=publishedAt&apiKey=%s", NEWS_API_KEY);
        
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected response: " + response);
            
            JSONObject jsonResponse = new JSONObject(response.body().string());
            JSONArray articles = jsonResponse.getJSONArray("articles");
            
            for (int i = 0; i < Math.min(5, articles.length()); i++) {
                JSONObject article = articles.getJSONObject(i);
                newsItems.add(new NewsItem(
                    article.getString("title"),
                    article.getString("description"),
                    article.getString("url"),
                    article.getString("publishedAt")
                ));
            }
        }
        return newsItems;
    }

    private List<NewsItem> collectStockNews() throws IOException {
        List<NewsItem> newsItems = new ArrayList<>();
        String url = String.format("https://finnhub.io/api/v1/news?category=general&token=%s", FINNHUB_API_KEY);
        
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected response: " + response);
            
            JSONArray news = new JSONArray(response.body().string());
            for (int i = 0; i < Math.min(5, news.length()); i++) {
                JSONObject article = news.getJSONObject(i);
                newsItems.add(new NewsItem(
                    article.getString("headline"),
                    article.getString("summary"),
                    article.getString("url"),
                    article.getString("datetime")
                ));
            }
        }
        return newsItems;
    }

    private String generateSummary(List<NewsItem> newsItems) throws IOException {
        StringBuilder content = new StringBuilder();
        for (NewsItem item : newsItems) {
            content.append("Title: ").append(item.title()).append("\n");
            content.append("Summary: ").append(item.description()).append("\n\n");
        }

        JSONObject requestBody = new JSONObject()
            .put("model", "gpt-3.5-turbo")
            .put("messages", new JSONArray()
                .put(new JSONObject()
                    .put("role", "system")
                    .put("content", "You are a financial analyst. Summarize the key points and potential market impacts from these news items."))
                .put(new JSONObject()
                    .put("role", "user")
                    .put("content", content.toString())));

        Request request = new Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer " + OPENAI_API_KEY)
            .header("Content-Type", "application/json")
            .post(okhttp3.RequestBody.create(requestBody.toString(), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected response: " + response);
            
            JSONObject jsonResponse = new JSONObject(response.body().string());
            return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
        }
    }

    private void saveToS3(List<NewsItem> newsItems, String summary) throws IOException {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        String key = String.format("analysis/%s.json", now.format(DateTimeFormatter.ISO_DATE_TIME));
        
        JSONObject data = new JSONObject()
            .put("timestamp", now.toString())
            .put("news_items", newsItems)
            .put("summary", summary);
        
        s3Client.putObject(builder -> builder
            .bucket(BUCKET_NAME)
            .key(key)
            .build(),
            RequestBody.fromString(data.toString())
        );
    }

    private record NewsItem(String title, String description, String url, String publishedAt) {}
} 