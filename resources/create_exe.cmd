SET LAUNCH4J=C:\Program Files (x86)\Launch4j

CD ../client
CALL mvn clean assembly:assembly
"%LAUNCH4J%/launch4jc.exe" ../resources/Amphibia.xml