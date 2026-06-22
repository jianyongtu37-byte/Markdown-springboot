FROM docker.1ms.run/library/elasticsearch:8.10.4
COPY elasticsearch-analysis-ik-8.10.4.zip /tmp/
RUN elasticsearch-plugin install --batch file:///tmp/elasticsearch-analysis-ik-8.10.4.zip
