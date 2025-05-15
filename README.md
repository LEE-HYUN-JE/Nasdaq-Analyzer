# 🧠 Nasdaq Analyzer: AI 기반 증시 요약 대시보드
자는 상황에 벌어진, 미국 증시에 대해 빠르고 간편하게 확인해볼 수 있습니다!

---

## 📆 전체 작동 흐름

### EventBridge 기반 스케줄

| 시각 (KST) | Lambda 함수        | 역할 |
|------------|---------------------|------|
| 06:30      | `quote-collector`   | 나스닥 주요 종목의 종가 및 변동률 수집 |
| 06:35      | `news-summarizer`   | GPT 기반 뉴스 요약 분석 수행 |
| 06:50      | `page-generator`    | 데이터를 바탕으로 정적 웹페이지 생성 후 S3 저장 |

---

## 🖼 시스템 아키텍처 구조
![Image](https://github.com/user-attachments/assets/0325c20b-8137-49de-a506-2b4dd5026c47)



---

## 💾 저장되는 S3 구조

| 목적           | 예시 키 이름 |
|----------------|---------------|
| 📈 종가 데이터     | `nasdaq-top10-2025-05-15.json` |
| 📰 뉴스 요약 데이터 | `nasdaq-summary/summary_2025-05-15.json` |
| 🌐 HTML 결과 페이지 | `nasdaq-dashboard/index.html` |

---

## 🧰 사용 기술 스택

- Java 17
- AWS Lambda + EventBridge + S3
- Finnhub API (주가 수집)
- NewsAPI + GPT-3.5 (뉴스 요약)
- Thymeleaf (템플릿 기반 정적 HTML 생성)
- AWS SDK v2
- Gradle + ShadowJar 플러그인

