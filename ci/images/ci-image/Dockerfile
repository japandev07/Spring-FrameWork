FROM ubuntu:focal-20220826

ADD setup.sh /setup.sh
ADD get-jdk-url.sh /get-jdk-url.sh
RUN ./setup.sh java8

ENV JAVA_HOME /opt/openjdk/java8
ENV JDK11 /opt/openjdk/java11
ENV JDK17 /opt/openjdk/java17

ENV PATH $JAVA_HOME/bin:$PATH
