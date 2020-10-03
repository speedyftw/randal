# randal
A discord random team bot.

# development
## requirements
- Java 1.8
- Gradle

## build/run
Build a fat jar
`./gradlew shadowJar`
Run the fat jar
`RANDOM_TEAM_BOT_TOKEN={your-token} java -jar build/libs/discordTeamGenerator-1.0-SNAPSHOT-all.jar`

## testing
1. Create a discord bot application
2. Run Randal with the environment variable `RANDOM_TEAM_BOT_TOKEN` as your bot's token


