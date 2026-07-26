# 多阶段构建：第一阶段编译出 fat jar，第二阶段只保留运行时需要的东西。
# 老版本基于已经停止维护的 anapsix/alpine-java:8u201（JDK 8），
# 而项目现在需要 JDK 17，直接换成官方的 temurin 镜像。

FROM eclipse-temurin:17-jdk AS builder

WORKDIR /src
COPY gradle gradle
COPY gradlew settings.gradle build.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon --console=plain shadowJar \
    && cp build/libs/*-all.jar /src/BilibiliTask.jar


FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=builder /src/BilibiliTask.jar /app/BilibiliTask.jar

ENV TZ=Asia/Shanghai

# 需要的环境变量：BILI_JCT / SESSDATA / DEDEUSERID
# 跑一次就退出，定时执行交给宿主机的 cron、Kubernetes CronJob 或者 docker run 的调度方式。
# 例：
#   docker run --rm -e BILI_JCT=... -e SESSDATA=... -e DEDEUSERID=... bilibilitask
ENTRYPOINT ["java", "-jar", "/app/BilibiliTask.jar"]
