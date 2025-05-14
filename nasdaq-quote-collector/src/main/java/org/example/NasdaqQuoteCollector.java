package org.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.oscerd.finnhub.client.FinnhubClient;
import com.github.oscerd.finnhub.models.Quote;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class NasdaqQuoteCollector implements RequestHandler<ScheduledEvent, String> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final S3Client s3Client = S3Client.create();
    private final FinnhubClient finnhubClient = new FinnhubClient(System.getenv("FINNHUB_API_KEY"));

    private static final List<String> NASDAQ_TOP10 = List.of("AAPL", "MSFT", "NVDA", "AMZN", "GOOG", "META", "TSLA", "AVGO", "ADBE", "COST");
    private static final String BUCKET_NAME = "nasdaq-analyzer-data";

    @Override
    public String handleRequest(ScheduledEvent input, Context context) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (String symbol : NASDAQ_TOP10) {
            try {
                Quote quote = finnhubClient.quote(symbol);
                Map<String, Object> entry = new HashMap<>();
                entry.put("symbol", symbol);
                entry.put("close", quote.getC());
                entry.put("changePercent", quote.getDp());
                entry.put("time", LocalDateTime.now().toString());
                results.add(entry);
            } catch (Exception e) {
                context.getLogger().log("Error fetching quote for " + symbol + ": " + e.getMessage());
            }
        }

        try {
            String json = mapper.writeValueAsString(results);
            String key = "nasdaq-top10-" + LocalDateTime.now() + ".json";

            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(key)
                    .contentType("application/json")
                    .build();

            s3Client.putObject(putReq, RequestBody.fromString(json, StandardCharsets.UTF_8));

            return "Uploaded: " + key;
        } catch (Exception e) {
            context.getLogger().log("Error uploading to S3: " + e.getMessage());
            return "Failed";
        }
    }
}
