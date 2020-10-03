package com.speedy

import com.jessecorbett.diskord.api.model.UserStatus
import com.jessecorbett.diskord.api.websocket.model.ActivityType
import com.jessecorbett.diskord.api.websocket.model.UserStatusActivity
import com.jessecorbett.diskord.dsl.Command
import com.jessecorbett.diskord.dsl.bot
import com.jessecorbett.diskord.dsl.command
import com.jessecorbett.diskord.dsl.commands
import com.jessecorbett.diskord.util.words
import kotlin.math.ceil
import kotlin.random.Random.Default.nextInt

// Connect to bot
// https://discord.com/api/oauth2/authorize?client_id=752048565051981872&permissions=16780288&scope=bot

const val HELP_OUTPUT = """Here are my commands!

**roll**
Rolls for random teams amongst the players and team size provided.

Usage: !teams roll [team size] <tag players>
Example: !teams roll 3 @Speedy @Rollie ...

**reroll**
Re-run the most recent roll with the same players and team size.

Usage: !teams reroll

**glue**
Glue-ing allows two or more players to always to the same team.

Usage: !teams glue <tag players>
Example: !teams glue @Speedy @Rollie ...

**unglue**
Remove all the glue.

Usage: !teams unglue

**whosglued**
See who is glued.

Usage: !teams whosglued
"""

const val ERR_ROLL_NO_USERS_MENTIONED = """You forgot to tag who is playing!

*!teams help* for a list of commands and examples
"""

const val ERR_ROLL_NO_TEAM_SIZE = """You must provide a team size!

*!teams help* for a list of commands and examples
"""

const val ERR_REROLL_NO_RECENT_ROLL = """You can't use **reroll** if you have not used **roll** recently!

*!teams help* for a list of commands and examples
"""

const val ERR_GLUE_NO_PLAYERS = """You need to tag two or more players to glue!

*!teams help* for a list of commands and examples
"""


val TEAM_NAMES = listOf(
    "Dudes",
    "Buds",
    "Pals",
    "Super Friend Squad",
    "The Boys"
)

val recentRolls = mutableMapOf<String, Pair<Int, List<String>>>()
val glue = mutableMapOf<String, MutableList<List<String>>>()
var teamNameSeed = 0

suspend fun main() {
    print("Bot Started")
    val token = System.getenv("RANDOM_TEAM_BOT_TOKEN")
        ?: throw IllegalArgumentException("missing env var RANDOM_TEAM_BOT_TOKEN")

    bot(token) {
        val bot = this
        started {
            bot.setStatus(
                UserStatus.ONLINE,
                activity = UserStatusActivity("!teams commands", ActivityType.LISTENING)
            )
        }
        commands(
            prefix = "!teams ",
            commands = mutableListOf(
                Command("roll") {
                    val teamSize = try {
                        words[2].toInt()
                    } catch (e: Exception) {
                        reply(ERR_ROLL_NO_TEAM_SIZE)
                        delete()
                        return@Command
                    }

                    if (usersMentioned.isEmpty()) {
                        reply(ERR_ROLL_NO_USERS_MENTIONED)
                        delete()
                        return@Command
                    }

                    val userIds = usersMentioned.map { it.id }
                    guildId?.let { recentRolls[it] = Pair(teamSize, userIds) }
                    val glueState = guildId?.let { glue[it] } ?: mutableListOf()
                    reply(randomizedTeamString(userIds, teamSize, glueState))
                    delete()
                },
                Command("reroll") {
                    guildId?.let { recentRolls[it] }?.let { (teamSize, users) ->
                        val glueState = guildId?.let { glue[it] } ?: mutableListOf()
                        reply(randomizedTeamString(users, teamSize, glueState))
                    } ?: reply(ERR_REROLL_NO_RECENT_ROLL)
                    delete()
                },
                Command("glue") {
                    if (usersMentioned.size < 2) {
                        reply(ERR_GLUE_NO_PLAYERS)
                        delete()
                        return@Command
                    }
                    val guildId = guildId ?: return@Command
                    val currGlue = glue[guildId] ?: mutableListOf()
                    val newUserIdsToGlue = usersMentioned.map { it.id }
                    // clear out any existing glue for these users
                    newUserIdsToGlue.forEach { newUserId ->
                        currGlue.removeIf { existingGlue -> existingGlue.any { it == newUserId } }
                    }
                    currGlue.add(newUserIdsToGlue)

                    reply(currGlueStateString(currGlue))
                    delete()
                },
                Command("unglue") {
                    val guildId = guildId ?: return@Command
                    glue.remove(guildId)
                    reply("All glue removed.")
                    delete()
                },
                Command("whosglued") {
                    val currGlue = guildId?.let { glue[it] } ?: return@Command
                    reply(currGlueStateString(currGlue))
                    delete()
                },
                Command("help") {
                    reply(HELP_OUTPUT)
                    delete()
                })
        ) {}
    }
    print("Bot Stopped")
}

fun userIdsToMentionString(userIds: List<String>) = userIds.joinToString(transform = { userId -> "<@$userId>" })

fun randomizedTeamString(userIds: List<String>, teamSize: Int, glueState: List<List<String>>): String {
    val userIdsToPlace = userIds.toMutableSet()
    val numTeams = ceil(userIds.size.toDouble() / teamSize).toInt()
    val teams = ArrayList<MutableList<String>>(numTeams).apply {
        repeat(numTeams) { add(mutableListOf()) }
    }

    // first, add the glued users to teams
    glueState.forEach { group ->
        val activeGroupMembers = group.filter { it in userIdsToPlace }
        val eligibleTeams = teams.filter { teamSize - it.size < activeGroupMembers.size }
        val selectedTeam = if(eligibleTeams.size > 1) {
            eligibleTeams[nextInt(eligibleTeams.size - 1)]
        } else eligibleTeams[0]
        selectedTeam.addAll(activeGroupMembers)
        userIdsToPlace.removeAll(activeGroupMembers)
    }
    // next, add the remaining users to teams
    userIdsToPlace.forEach { user ->
        val eligibleTeams = teams.filter { teamSize - it.size > 0 }
        val selectedTeam = if(eligibleTeams.size > 1) {
            eligibleTeams[nextInt(eligibleTeams.size - 1)]
        } else eligibleTeams[0]
        selectedTeam.add(user)
    }

    val sb = StringBuilder()
    sb.appendln("Teams:")
    teams.forEach { team ->
        sb.appendln("${TEAM_NAMES[teamNameSeed % (TEAM_NAMES.size - 1)]}: ${userIdsToMentionString(team)}")
        teamNameSeed += 1
    }
    return sb.toString()
}

fun currGlueStateString(glueState: List<List<String>>): String {
    val sb = StringBuilder()
    sb.appendln("Current glue:")
    glueState.forEach { squad ->
        sb.appendln("${userIdsToMentionString(squad)} will always team up.")
    }
    return sb.toString()
}