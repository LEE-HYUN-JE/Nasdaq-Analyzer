package org.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.*;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

public class NasdaqNewsSummarizer implements RequestHandler<Object, String> {

    @Override
    public String handleRequest(Object input, Context context) {
        String NEWS_API_KEY = System.getenv("NEWS_API_KEY");
        String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

        if (NEWS_API_KEY == null || OPENAI_API_KEY == null) {
            context.getLogger().log("환경변수가 설정되지 않았습니다.\n");
            return "실패: 환경변수 없음";
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(15, TimeUnit.SECONDS)
                .build();

        String newsJson;
        try {
            HttpUrl url = HttpUrl.parse("https://newsapi.org/v2/top-headlines").newBuilder()
                    .addQueryParameter("country", "us")
                    .addQueryParameter("category", "business")
                    .addQueryParameter("pageSize", "3")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-Api-Key", NEWS_API_KEY)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    context.getLogger().log("뉴스 API 오류: " + response.code() + "\n");
                    return "실패: 뉴스 API 오류";
                }
                newsJson = response.body().string();
            }

            JsonArray articles = JsonParser.parseString(newsJson)
                    .getAsJsonObject()
                    .getAsJsonArray("articles");

            if (articles.size() == 0) {
                context.getLogger().log("뉴스 없음\n");
                return "성공: 뉴스 없음";
            }

            OpenAiService gpt = new OpenAiService(OPENAI_API_KEY);
            JsonArray resultArray = new JsonArray();

            for (JsonElement element : articles) {
                JsonObject article = element.getAsJsonObject();
                String title = article.get("title").getAsString();
                String description = article.has("description") && !article.get("description").isJsonNull()
                        ? article.get("description").getAsString()
                        : "";

                String prompt = String.format("""
                        다음은 미국 주식 시장 관련 뉴스입니다. 제목과 내용을 바탕으로 한국어로 요약하고 영향 분석해줘.

                        제목: %s
                        내용: %s

                        [요약 및 분석을 한글로 작성해주세요.]
                        """, title, description);

                CompletionRequest completionRequest = CompletionRequest.builder()
                        .prompt(prompt)
                        .model("gpt-3.5-turbo-instruct")
                        .temperature(0.7)
                        .maxTokens(500)
                        .build();

                CompletionChoice choice = gpt.createCompletion(completionRequest).getChoices().get(0);

                JsonObject result = new JsonObject();
                result.addProperty("title", title);
                result.addProperty("summary", choice.getText().trim());

                resultArray.add(result);
            }
            String jsonOutput = new GsonBuilder().setPrettyPrinting().create().toJson(resultArray);


            S3Client s3 = S3Client.create();
            String key = "nasdaq-summary/summary_" + LocalDate.now() + ".json";

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket("nasdaq-analyzer-data")
                    .key(key)
                    .contentType("application/json")
                    .build();

            s3.putObject(putRequest, RequestBody.fromString(jsonOutput, StandardCharsets.UTF_8));
            context.getLogger().log("S3 저장 완료: " + key + "\n");

            return "성공: " + key;

        } catch (IOException e) {
            e.printStackTrace();
            return "실패: 예외 발생";
        }
    }
}
