FROM openjdk:11-jre-slim
RUN addgroup --system atlas && adduser --system atlas && adduser atlas atlas
USER atlas
RUN mkdir -p /home/atlas/{bin,test_data}
COPY target/scala-2.13/AtlasRideScheduler-assembly-0.1.0-SNAPSHOT.jar /home/atlas/bin/atlas-assembly.jar
COPY src/main/resources/test_data/ /home/atlas/test_data/
RUN mkdir -p /home/atlas/out/
RUN chmod a+rw /home/atlas/out/
CMD ["java", "-jar", "/home/atlas/bin/atlas-assembly.jar", "Main"]