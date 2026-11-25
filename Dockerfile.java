# =========================================================================
# HUNTER OF THE SEA - JAVA STANDALONE SERVER DOCKERFILE
# =========================================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build
COPY Backend/src/main/java/com/game/hunter/StandaloneServer.java .
RUN javac --release 8 -encoding UTF-8 -d . StandaloneServer.java

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /build/com ./com

ENV PORT=8080
ENV WS_PORT=8081
EXPOSE 8080 8081

CMD ["java", "-cp", ".", "com.game.hunter.StandaloneServer"]
