# syntax=docker/dockerfile:1
# Build from the REPO ROOT so Maven sees ../data:  docker build -f be/Dockerfile .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY data/ data/
COPY be/.mvn/ be/.mvn/
COPY be/mvnw be/pom.xml be/
WORKDIR /src/be
# mvnw may have Windows line endings after a checkout on Windows
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY be/src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && cp target/travelpilot-*.jar /app.jar \
    && java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --uid 10001 app
WORKDIR /app
# Layers from least to most often changed, so a code change only rebuilds the last one
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
EXPOSE 8088
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
