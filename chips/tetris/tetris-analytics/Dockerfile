# 교재 4장 Dockerfile (계층으로 나눈 jar), 이미지 태그만 Java 25
# 먼저 레포 루트에서 ./gradlew build -x test 로 이 모듈의 build/libs 에 실행 jar 하나를 만들어 둔다
FROM eclipse-temurin:25-jre AS builder
WORKDIR /builder
ADD ./build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# 컨테이너는 다시 뜰 때 IP가 바뀔 수 있어, 서비스 이름의 IP를 5초까지만 기억한다 (JVM 기본 30초)
ENTRYPOINT ["java", "-Dsun.net.inetaddr.ttl=5", "org.springframework.boot.loader.launch.JarLauncher"]
