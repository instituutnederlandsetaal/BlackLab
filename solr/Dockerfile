## Stage 0: Build Solr plugin
#=============================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy source
COPY . .

# Build plugin JAR
RUN --mount=type=cache,target=/root/.m2 mvn -q --no-transfer-progress package


## Stage 1: Solr
#=============================
FROM solr:8.11.2
LABEL maintainer="Jan Niestadt <jan.niestadt@ivdnt.org>"

# Create directory for our test core
RUN mkdir -p /var/solr/data/test

# Copy test core
COPY solr/src/test/resources/existing-index /var/solr/data/test/

# NOTE: the following commented-out locations for our JARs don't work! See below.

# Install JAR in core's lib dir (will be loaded autmatically)
# (still gives us the SPI problem though)
#COPY solr/target/blacklab-solr-no-deps.jar /var/solr/data/test/lib/

# Install JAR in main lib dir(s)
#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/lib/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/lib/

# (doesn't work, even if we load them explicitly?)
# COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/server/solr/lib/
# COPY solr/target/lib/blacklab-*.jar /opt/solr/server/solr/lib/

# If we include this, we get ClassNotFoundException: org.apache.lucene.codecs.PostingsFormat
#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/server/lib/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/server/lib/

#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/contrib/blacklab-solr/lib/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/contrib/blacklab-solr/lib/

# Copy to dir specified as sharedLib in solr.xml
#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/shared/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/shared/

# Copy JARs from previous stage to Solr's WEB-INF/lib. THIS WORKS!!
COPY --from=builder \
     /app/solr/target/blacklab-solr-no-deps.jar /app/solr/target/lib/*.jar \
     /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/

USER root

# Make sure Solr can write snapshot data
RUN chgrp -R $SOLR_GROUP /var/solr/data/test/ && \
    chmod -R g+rw /var/solr/data/test/

USER $SOLR_USER
