# Third-party artifacts not on Maven Central

## FYERS Java SDK 2.2.0 (`in.fyers:fyers-java-sdk:2.2.0`)

- Source: https://github.com/FyersDev/fyers-java-sdk, file `dist/fyersjavasdk-obs.jar`, repo commit
  `17a8f06fb23503465d07bb83f196bd20cda8ccc5` (2026-11-18). README title: "fyers-api-v3 - v2.2.0".
- SHA-256 of the jar: `860fe8fe54adfa31a50e9367fa9f538f5c3dbac0e315ffeb41ab15adf08753af`.
- FYERS publishes it only as this jar (no Maven Central, no releases), so it lives in `maven-repo/` here and the
  parent POM points at it with `checksumPolicy=fail`.
- The jar is obfuscated and is a fat jar targeting Java 8. It bundles, without relocation: org.json, OkHttp/Okio,
  the Kotlin stdlib, Apache HttpClient, commons-codec, commons-logging and **log4j 1.2** (end of life). PredictiveEdge
  never configures log4j 1.x appenders, so the known log4j 1.x CVEs (JMSAppender, SocketServer, JDBCAppender) are
  not reachable through our code, but scanners will still report them.
- No FYERS licence is published with the SDK. The bundled `META-INF/LICENSE*` files cover the third-party libraries
  only. Confirm FYERS's terms before distributing builds that include it.
- Only `org.predictiveedge.fyers.FyersSdkGateway` may call `com.tts.in.model.FyersClass` (it is a process-wide
  singleton holding one token; the gateway serialises calls per user).

To upgrade: download the new jar, run from the repository root

    mvn deploy:deploy-file -Durl=file:$(pwd)/backend/predictiveedge-parent/third-party/maven-repo \
      -Dfile=fyersjavasdk-obs.jar -DgroupId=in.fyers -DartifactId=fyers-java-sdk -Dversion=<version> \
      -Dpackaging=jar -DgeneratePom=true

then update the version in `fyers-adapter/pom.xml` and the details above.
