# 🧠 Nasdaq Analyzer: AI 기반 증시 요약 대시보드 자동 생성기

이 프로젝트는 매일 오전 6시 30분부터 자동으로 작동하는 3개의 AWS Lambda 함수로 구성되어 있으며, 미국 증시 관련 뉴스를 수집하고 GPT로 요약한 뒤, 나스닥 상위 10개 종목의 주가 데이터를 함께 정적 웹페이지로 구성하여 S3에 자동 배포하는 시스템입니다.

> 💡 모든 과정은 완전 자동이며, 사용자는 S3에서 정적 HTML 페이지를 확인함으로써 매일 아침 증시 요약을 빠르게 확인할 수 있습니다.

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

아래 PDF 문서에서 전체 인프라 구성 및 Lambda 연동 구조를 시각적으로 확인할 수 있습니다.

📎 **[아키텍처 구조도 보기 (GitHub 파일 미리보기)](https://github.com/user-attachments/files/20218548/_.pdf)**  
> GitHub 링크는 PDF 뷰어로 열립니다 (다운로드 아님)


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

