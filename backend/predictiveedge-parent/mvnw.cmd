@ECHO OFF
SETLOCAL
SET MVNW_REPOURL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2
IF "%MAVEN_PROJECTBASEDIR%"=="" SET MAVEN_PROJECTBASEDIR=%~dp0
SET MAVEN_CMD=mvn
%MAVEN_CMD% %*
