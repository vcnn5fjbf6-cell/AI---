FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted \
    && set -eux; \
       jar_count="$(find extracted/dependencies/lib -maxdepth 1 -type f -name '*.jar' | wc -l)"; \
       test "$jar_count" -gt 0; \
       mkdir -p \
         dependency-layers/a-l/lib \
         dependency-layers/m-n/lib \
         dependency-layers/o/lib \
         dependency-layers/p-r/lib \
         dependency-layers/s/lib \
         dependency-layers/t-z/lib; \
       for jar in extracted/dependencies/lib/*.jar; do \
         case "$(basename "$jar")" in \
           [a-lA-L]*) bucket=a-l ;; \
           [m-nM-N]*) bucket=m-n ;; \
           [oO]*) bucket=o ;; \
           [p-rP-R]*) bucket=p-r ;; \
           [sS]*) bucket=s ;; \
           [t-zT-Z]*) bucket=t-z ;; \
           *) echo "Unsupported dependency name: $jar" >&2; exit 1 ;; \
         esac; \
         mv "$jar" "dependency-layers/$bucket/lib/"; \
       done; \
       test "$(find dependency-layers -type f -name '*.jar' | wc -l)" -eq "$jar_count"; \
       test -z "$(find extracted/dependencies/lib -type f -print -quit)"; \
       for bucket in a-l m-n o p-r s t-z; do \
         layer_bytes="$(du -sb "dependency-layers/$bucket" | cut -f1)"; \
         test "$layer_bytes" -le 33554432 || { \
           echo "Dependency layer $bucket exceeds 32 MiB: $layer_bytes bytes" >&2; \
           exit 1; \
         }; \
       done; \
       find \
         dependency-layers \
         extracted/spring-boot-loader \
         extracted/snapshot-dependencies \
         extracted/application \
         -exec touch -h -d '@0' {} +

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=prod
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/dependency-layers/a-l/ ./
COPY --from=builder /app/dependency-layers/m-n/ ./
COPY --from=builder /app/dependency-layers/o/ ./
COPY --from=builder /app/dependency-layers/p-r/ ./
COPY --from=builder /app/dependency-layers/s/ ./
COPY --from=builder /app/dependency-layers/t-z/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java","-jar","application.jar"]
