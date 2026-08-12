# Stage "builder": build the WAR file
#--------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder
ARG SKIP_TESTS=false

# Copy source
WORKDIR /app
COPY . .

# Build the WAR.
#       to be able to cache Maven libs so they aren't re-downloaded every time you build the image
RUN --mount=type=cache,target=/root/.m2 \
    if [ "$SKIP_TESTS" = "true" ]; then \
        mvn -q --no-transfer-progress -DskipTests clean package; \
    else \
        mvn -q --no-transfer-progress clean package; \
    fi


# Tomcat container with the WAR file
#--------------------------------------
FROM tomcat:10-jre21
ARG CONFIG_ROOT=docker/config
ARG TOMCAT_APP_NAME=blacklab-server

# Install custom server.xml
# (URIEncoding=utf-8)
COPY ${CONFIG_ROOT}/server.xml /usr/local/tomcat/conf/

# Copy the configuration file
COPY ${CONFIG_ROOT}/blacklab-server.yaml /etc/blacklab/

# Copy the startup scripts
COPY docker/*.sh /etc/blacklab/

# Create directories for formats and data
RUN mkdir -p /etc/blacklab/formats /data/index /data/user-index

# Copy the WAR file
COPY --from=builder /app/server/target/blacklab-server-*.war /usr/local/tomcat/webapps/${TOMCAT_APP_NAME}.war

# Copy JARs for the commandline tools
COPY --from=builder /app/core/target/blacklab-*.jar /app/core/target/lib /usr/local/lib/blacklab-tools/

# Our data directory (either an anonymous volume or a named volume mounted by user)
VOLUME /data
