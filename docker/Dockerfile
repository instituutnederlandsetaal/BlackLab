# Stage "builder": build the WAR file
#--------------------------------------
FROM maven:3.10.0-eclipse-temurin-11 AS builder

# Copy source
WORKDIR /app
COPY . .

# Build the WAR.
# NOTE: make sure BuildKit is enabled (see https://docs.docker.com/develop/develop-images/build_enhancements/)
#       to be able to cache Maven libs so they aren't re-downloaded every time you build the image
RUN --mount=type=cache,target=/root/.m2 mvn --no-transfer-progress package


# Tomcat container with the WAR file
#--------------------------------------
FROM tomcat:10-jre11
ARG CONFIG_ROOT=docker
ARG TOMCAT_APP_NAME=blacklab

# Install custom server.xml
# (URIEncoding=utf-8)
COPY ${CONFIG_ROOT}/server.xml /usr/local/tomcat/conf/

# Copy the configuration file
COPY ${CONFIG_ROOT}/blacklab-server.yaml /etc/blacklab/

# Copy the starup script
COPY docker/start-server.sh /etc/blacklab/start-server.sh

# Create directory for formats
RUN mkdir /etc/blacklab/formats

# Copy the WAR file
COPY --from=builder /app/server/target/blacklab-server-*.war /usr/local/tomcat/webapps/${TOMCAT_APP_NAME}.war

# Copy JARs for the commandline tools
COPY --from=builder /app/core/target/blacklab-*.jar /app/core/target/lib /usr/local/lib/blacklab-tools/

# Our data directory (either an anonymous volume or a named volume mounted by user)
VOLUME /data
