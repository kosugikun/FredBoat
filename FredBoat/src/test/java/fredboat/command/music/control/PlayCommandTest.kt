package fredboat.command.music.control

import com.fredboat.sentinel.entities.AudioQueueRequest
import com.fredboat.sentinel.entities.AudioQueueRequestEnum
import com.fredboat.sentinel.entities.EditMessageRequest
import fredboat.audio.player.PlayerRegistry
import fredboat.audio.player.VideoSelectionCache
import fredboat.sentinel.GuildCache
import fredboat.test.IntegrationTest
import fredboat.test.sentinel.*
import fredboat.test.util.cachedGuild
import fredboat.test.util.queue
import org.junit.Assert
import org.junit.Assert.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

internal class PlayCommandTest : IntegrationTest() {

    companion object {
        const val url = "https://www.youtube.com/watch?v=8EdW28B-In4"
        const val url2 = "https://www.youtube.com/watch?v=pqUuvRkFfLI"
    }

    @Test
    fun notInChannel() {
        testCommand(";;play $url") {
            assertReply("You must join a voice channel first.")
        }
    }

    @Test
    fun search(selections: VideoSelectionCache, players: PlayerRegistry) {
        SentinelState.joinChannel()
        var editedMessage = -1L
        var selection: VideoSelectionCache.VideoSelection? = null
        testCommand(";;play demetori") {
            assertReply("Searching YouTube for `demetori`...")
            assertRequest { req: EditMessageRequest ->
                editedMessage = req.messageId
                req.message.startsWith("**Please select a track with the `;;play 1-5` command:**")
            }
            delayUntil { selections[member] != null }
            selection = selections[member]
            assertNotNull(selection)
            assertEquals(selection!!.message.id, editedMessage)
        }
        testCommand(";;play 5") {
            assertRequest { req: EditMessageRequest ->
                assertEquals(req.messageId, editedMessage)
                req.message.contains("Song **#5** has been selected")
            }
            Assert.assertNull(selections[member])
            delayUntil { players.getExisting(guild) != null }
            assertNotNull(players.getExisting(guild))
            assertEquals(selection!!.choices[4], players.getExisting(guild)!!.playingTrack?.track)
            assertEquals(member, players.getExisting(guild)!!.playingTrack?.member)
        }
    }

    @Test
    fun playUrl(players: PlayerRegistry) {
        SentinelState.joinChannel(channel = DefaultSentinelRaws.musicChannel)
        testCommand(";;play $url") {
            assertRequest<AudioQueueRequest> { it.channel == DefaultSentinelRaws.musicChannel.id }
            assertReply { it.contains("Best of Demetori") && it.contains("will now play") }
            assertNotNull(players.getExisting(guild))

            delayUntil { players.getOrCreate(guild).playingTrack != null }
            assertNotNull(players.getOrCreate(guild).playingTrack)
            assertEquals(url, players.getOrCreate(guild).playingTrack?.track?.info?.uri)
        }

        testCommand(";;play $url2") {
            assertRequest<AudioQueueRequest> { it.channel == DefaultSentinelRaws.musicChannel.id }
            assertReply { it.contains("BEGIERDE DES ZAUBERER") && it.contains("has been added to the queue") }

            delayUntil { players.getOrCreate(guild).remainingTracks.size == 2 }
            assertNotNull(players.getOrCreate(guild).remainingTracks[1])
            assertEquals(url2, players.getOrCreate(guild).remainingTracks[1].track.info?.uri)
        }
    }

    @Test
    fun unpause(players: PlayerRegistry) {
        // Setup
        SentinelState.joinChannel()
        SentinelState.joinChannel(DefaultSentinelRaws.self)
        cachedGuild.queue(url).setPause(true)

        // We should unpause
        testCommand(";;play") {
            delayUntil { players.getExisting(guild)?.isPlaying == true }
            assertFalse("Assert unpaused", players.getOrCreate(guild).isPaused)
            assertReply("The player will now play.")
        }
    }

    @BeforeEach
    fun beforeEach(players: PlayerRegistry, guildCache: GuildCache) {
        val guild = guildCache.get(SentinelState.guild.id).block(Duration.ofSeconds(5))!!
        val link = guild.existingLink
        players.destroyPlayer(guild)
        SentinelState.reset()

        // Ignore any disconnect requests
        if (link == null) return
        val req = SentinelState.poll(AudioQueueRequest::class.java, timeoutMillis = 2000)
        req?.let { assertEquals(
                "Expected disconnect (if anything at all)",
                AudioQueueRequestEnum.QUEUE_DISCONNECT,
                it.type
        ) }
    }
}