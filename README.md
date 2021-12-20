# schema-converter
- 입력 폼 : json(schema)
- 출력 폼 : xls(OOXML)

# 사용법
- GCP 프로젝트 생성 및 translate API에 대한 서비스 어카운트 생성[참조 링크](https://cloud.google.com/translate/docs/basic/setup-basic#client-libraries-install-java)
- 서비스 어카운트 credential 다운로드 및 해당 경로를 시스템 환경변수로 설정(GOOGLE_APPLICATION_CREDENTIALS)

# Run Command
- without parameter
```bash
./gradlew run
```
- with parameter
```bash
./gradlew run --args="root (root dir 경로) output (output dir 경로) translate (true/false)"
```
- parameter details
    - 모든 parameter는 key-value 쌍으로 주어져야함
    - 순서는 무관함
    - parameter 종류
        - root (root dir 경로)
        - output (output dir 경로)
        - translate (true/false)