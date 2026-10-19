# Gradle 이 포함된 이미지로 빌드 (wrapper 다운로드 없이)
FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY settings.gradle build.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null
COPY src ./src
RUN gradle bootJar --no-daemon -x test -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
