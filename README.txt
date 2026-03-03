SetHome plugin project

Contents:
- src/main/java/com/sethome/SetHomePlugin.java  (main source)
- src/main/resources/plugin.yml
- pom.xml

How to build:
- Requires Java 17+ and Maven
- Run: mvn clean package
- Output JAR will be in target/SetHome.jar

Notes:
- Plugin saves player home files to plugins/SetHome/homes/<uuid>.yml
- mainconfig.yml is auto-created at plugins/SetHome/mainconfig.yml (with comments)
- Default cooldown is 3 seconds (configurable)
- Messages use Minecraft color codes (&b and &3) per your request.
