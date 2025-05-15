package org.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class DashboardRendererLambda implements RequestHandler<ScheduledEvent, String> {

    private static final String BUCKET_NAME = "nasdaq-analyzer-data";
    private static final String SUMMARY_PREFIX = "nasdaq-summary/summary_";
    private static final String QUOTE_PREFIX = "nasdaq-top10";
    private static final String OUTPUT_KEY = "index.html";

    private final S3Client s3Client = S3Client.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateEngine templateEngine = createTemplateEngine();

    @Override
    public String handleRequest(ScheduledEvent input, Context context) {
        try {
            String today = LocalDate.now().toString(); // ex: 2025-05-15
            context.getLogger().log(" 기준 날짜: " + today + "\n");

            // 1. 뉴스 요약 로드
            String summaryKey = SUMMARY_PREFIX + today + ".json";
            List<Map<String, String>> summaries = readJsonArray(summaryKey);
            context.getLogger().log(" 뉴스 요약 로드 완료 (" + summaries.size() + "개)\n");

            // 2. 가장 최근 Quote JSON 선택
            ListObjectsV2Response listResp = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(BUCKET_NAME)
                    .prefix(QUOTE_PREFIX)
                    .build());

            Optional<S3Object> latestQuote = listResp.contents().stream()
                    .filter(obj -> obj.key().contains(today))
                    .max(Comparator.comparing(S3Object::lastModified));

            if (latestQuote.isEmpty()) {
                context.getLogger().log(" Quote JSON 없음: " + today + "\n");
                return "No quote data for today.";
            }

            String quoteKey = latestQuote.get().key();
            List<Map<String, Object>> quotes = readJsonArray(quoteKey);
            context.getLogger().log(" 최신 Quote 로드 완료 (" + quotes.size() + "종목)\n");

            // 3. Thymeleaf 템플릿 렌더링
            org.thymeleaf.context.Context thymeCtx = new org.thymeleaf.context.Context();
            thymeCtx.setVariable("quotes", quotes);
            thymeCtx.setVariable("topics", summaries);


            String html = templateEngine.process("hot-topic", thymeCtx);

            // 4. 결과 HTML S3에 업로드
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(OUTPUT_KEY)
                    .contentType("text/html")
                    .build();

            s3Client.putObject(putReq, RequestBody.fromString(html, StandardCharsets.UTF_8));

            context.getLogger().log(" 대시보드 업로드 완료: " + OUTPUT_KEY + "\n");
            return "Dashboard created successfully.";

        } catch (Exception e) {
            context.getLogger().log(" 오류: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private <T> List<T> readJsonArray(String key) throws Exception {
        ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build());

        String json = new BufferedReader(new InputStreamReader(stream)).lines()
                .collect(Collectors.joining("\n"));

        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private TemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
