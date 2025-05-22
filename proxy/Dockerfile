# Stage "builder": build the WAR file
#--------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

# Copy source
WORKDIR /app
COPY . .

# Build the WAR.
# NOTE: make sure BuildKit is enabled (see https://docs.docker.com/develop/develop-images/build_enhancements/)
#       to be able to cache Maven libs so they aren't re-downloaded every time you build the image
RUN --mount=type=cache,target=/root/.m2 mvn -q --no-transfer-progress package


# Tomcat container with the WAR file
#--------------------------------------
FROM tomcat:9-jre17

# Create config directory
RUN mkdir /etc/blacklab/

# Copy the WAR file
COPY --from=builder /app/proxy/service/target/blacklab-proxy.war /usr/local/tomcat/webapps/blacklab-server.war
