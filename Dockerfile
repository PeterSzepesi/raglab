FROM node:24-alpine AS ui-build
WORKDIR /workspace
COPY package.json package-lock.json tsconfig.json vite.config.ts ./
COPY src/frontend ./src/frontend
COPY src/main/resources/static ./src/main/resources/static
RUN npm ci && npm run build

FROM eclipse-temurin:21-jdk-alpine AS app-build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src ./src
COPY --from=ui-build /workspace/src/main/resources/static ./src/main/resources/static
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S arxivrag && adduser -S arxivrag -G arxivrag
WORKDIR /app
COPY --from=app-build /workspace/target/arxiv-rag-0.0.1-SNAPSHOT.jar app.jar
USER arxivrag
EXPOSE 8080
ENV RAG_SERVER_ADDRESS=0.0.0.0
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
