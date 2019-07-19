package com.luxoft.blockchainlab.corda.hyperledger.indy

import com.luxoft.blockchainlab.hyperledger.indy.helpers.TailsHelper
import com.luxoft.blockchainlab.hyperledger.indy.models.CredentialOffer
import com.luxoft.blockchainlab.hyperledger.indy.models.KeyCorrectnessProof
import com.luxoft.blockchainlab.hyperledger.indy.models.TailsResponse
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.exceptions.InvalidDataException
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.junit.Test
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import java.io.File
import java.lang.Exception
import java.lang.RuntimeException
import java.net.URI
import kotlin.test.assertEquals
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotEquals

class PythonRefAgentConnectionTest {

    class InvitedPartyProcess (
            private val agentUrl: String,
            val proofSchemaId: String = "${Random().nextInt()}:::1",
            val tailsHash: String = "${Random().nextInt(Int.MAX_VALUE)}"
            ) {

        fun start(invitationString: String) {
            val rand = Random().nextInt()
            PythonRefAgentConnection().apply {
                connect(agentUrl, "User$rand", "pass$rand").handle { _, ex ->
                    if (ex != null) {
                        println("Error connecting User-$rand to $agentUrl: ${ex.message!!}")
                    } else {
                        acceptInvite(invitationString).subscribe { master ->
                            val tails = master.requestTails(tailsHash).toBlocking().value().tails[tailsHash]
                            if (tails?.toString(Charsets.UTF_8) != tailsHash)
                                println("Tails file content doesn't match!!! hash $tailsHash, received $tails")
                            else {
                                val offer = CredentialOffer(proofSchemaId, ":::1", KeyCorrectnessProof("", "", emptyList()), "")
                                master.sendCredentialOffer(offer)
                                println("Client User$rand completed successfully")
                            }
                            disconnect()
                        }
                    }
                }
            }
        }
    }

    class MasterProcess (
            private val agentUrl: String,
            private val invitedPartyAgents: List<String>) {

        var testOk = false
        fun start() {
            val rand = Random().nextInt()
            val tailsDir = File("tails").apply { deleteOnExit() }
            if (!tailsDir.exists())
                tailsDir.mkdirs()
            PythonRefAgentConnection().apply {
                connect(agentUrl, "User$rand", "pass$rand").toBlocking().value()
                val invitedPartiesCompleted = mutableListOf<Observable<Boolean>>()
                invitedPartyAgents.forEach { agentUrl ->
                    val party = InvitedPartyProcess(agentUrl)
                    Paths.get("tails", party.tailsHash).toFile().apply { deleteOnExit() }
                        .writeText(party.tailsHash, Charsets.UTF_8)
                    invitedPartiesCompleted.add(Observable.create { observer ->
                        generateInvite().subscribe ({invitation ->
                            waitForInvitedParty(invitation).subscribe ({ invitedParty ->
                                invitedParty.handleTailsRequestsWith {
                                    TailsHelper.DefaultReader(tailsDir.absolutePath).read(it)
                                }
                                invitedParty.receiveCredentialOffer().subscribe { proof ->
                                    assertEquals(proof?.schemaIdRaw, party.proofSchemaId)
                                    observer.onNext(true)
                                    observer.onCompleted()
                                }
                            }, {
                                observer.onNext(false)
                                observer.onCompleted()
                            })
                            party.start(invitation)
                        }, {
                            println("Error generating an invitation code: $it")
                        })
                    })
                }

                val completed = Single.create<Boolean> { completedObserver ->
                    Observable.from(invitedPartiesCompleted)
                        .flatMap { it.observeOn(Schedulers.computation()) }
                        .toList().subscribe({
                            results ->
                            testOk = results.all { result -> result == true }
                            if (!testOk)
                                println("Not all invited parties have completed successfully")
                            completedObserver.onSuccess(true)
                        }, {
                            completedObserver.onError(AgentConnectionException("Some of the invited parties threw an error: $it"))
                        })
                }
                completed.toBlocking().value()
                disconnect()
            }
        }
    }

    private val invitedPartyAgents = listOf(
            "ws://127.0.0.1:8094/ws",
            "ws://127.0.0.1:8096/ws",
            "ws://127.0.0.1:8097/ws",
            "ws://127.0.0.1:8098/ws",
            "ws://127.0.0.1:8099/ws"
            )
    private val masterAgent = "ws://127.0.0.1:8095/ws"

    @Test
    fun `externalTest`() = repeat(10) {
        val master = MasterProcess(masterAgent, invitedPartyAgents).apply { start() }
        if (!master.testOk)
            throw AgentConnectionException("Master process didn't complete Ok")
    }

    class Client (private val agentUrl: String) {
        private lateinit var agentConnection : PythonRefAgentConnection
        fun connect(invitationString: String, timeoutMs: Long = 10000) : IndyPartyConnection {
            val rand = Random().nextInt()
            agentConnection = PythonRefAgentConnection()
            agentConnection.connect(agentUrl, "User$rand", "pass$rand", timeoutMs).toBlocking().value()
            return agentConnection.acceptInvite(invitationString).toBlocking().value()
        }
        fun disconnect() {
            agentConnection.disconnect()
        }
    }
    class Server (private val agentUrl: String, private val tailsHash: String) {
        private lateinit var agentConnection: PythonRefAgentConnection
        fun getInvite() : String {
            val rand = Random().nextInt()
            agentConnection = PythonRefAgentConnection()
            agentConnection.connect(agentUrl, "User$rand", "pass$rand").toBlocking().value()
            val invitationString = agentConnection.generateInvite().toBlocking().value()
            agentConnection.waitForInvitedParty(invitationString).subscribe { invitedParty ->
                val partyDid = invitedParty.partyDID()
                println("Server: client $partyDid connected")
                invitedParty.handleTailsRequestsWith {
                    TailsResponse(tailsHash, mapOf(tailsHash to tailsHash.toByteArray()))
                }
            }
            return invitationString
        }
        fun disconnect() {
            agentConnection.disconnect()
        }
    }
    class ExtraClient (private val agentUrl: String) {
        private lateinit var agentConnection: PythonRefAgentConnection
        fun connect(timeoutMs: Long = 10000) : Single<Unit>  {
            val rand = Random().nextInt()
            agentConnection = PythonRefAgentConnection()
            return agentConnection.connect(agentUrl, "User$rand", "pass$rand", timeoutMs)
        }
        fun disconnect() {
            agentConnection.disconnect()
        }
    }

    @Test
    fun `client reconnects to server when the connection is interrupted `() {
        val tailsHash = "${Random().nextInt(Int.MAX_VALUE)}"
        val server = Server(masterAgent, tailsHash)
        val invitationString = server.getInvite()
        val client = Client(invitedPartyAgents[0])
        val clientConnection = client.connect(invitationString)
        println("Client connected the agent. Local DID is ${clientConnection.myDID()}.")
        repeat(5) {
            val tails = clientConnection.requestTails(tailsHash).toBlocking().value()
            println("Tails received: ${tails.tails[tailsHash]?.toString()}")
        }
        val extraClient = ExtraClient(invitedPartyAgents[0]).apply {
            connect().toBlocking().value()
        }
        val tails = clientConnection.requestTails(tailsHash).toBlocking().value()
        println("Latest tails: ${tails.tails[tailsHash]?.toString()}")
        client.disconnect()
        extraClient.disconnect()
        server.disconnect()
    }

    @Test
    fun `client fails due to connection timeout (non-existent server)`() {
        var testOk = false
        val client = ExtraClient("ws://8.8.8.127:8093/ws").apply {
            connect(2000).subscribe({}, {
                if (it is TimeoutException)
                    testOk = true
            })
        }
        Thread.sleep(3000)
        client.disconnect()
        if (!testOk) throw RuntimeException("Test failed")
    }

    @Test
    fun `client fails due to refused connection (closed port)`() {
        var testOk = false
        val client = ExtraClient("ws://127.0.0.1:8093/ws").apply {
            connect().subscribe({}, {
                if(it is AgentConnectionException)
                    testOk = true
            })
        }
        Thread.sleep(1000)
        client.disconnect()
        if (!testOk) throw RuntimeException("Test failed")
    }

    /**
     * Some of the messages are consumed by a different client, connected in the middle of the message exchange
     */
    @Test
    fun `the connection is lost in the middle of asynchronous message exchange, some messages are lost`() {
        val tailsHash = "${Random().nextInt(Int.MAX_VALUE)}"
        val invitationString = Server(masterAgent, tailsHash).getInvite()
        val clientConnection = Client(invitedPartyAgents[0]).connect(invitationString)
        println("Client connected the agent. Local DID is ${clientConnection.myDID()}.")
        val i = AtomicInteger(100)
        repeat(100) {
            /**
             * asynchronously request 100 messages
             */
            clientConnection.requestTails(tailsHash).subscribe({tails ->
                /**
                 * atomically decrement the counter of received message upon receipt
                 */
                println("Tails received: ${tails.tails[tailsHash]?.toString()}, ${i.getAndDecrement()} messages left")
            }, {

            })
        }
        /**
         * break the clientConnection in the middle of the exchange
         */
        try {
            val conn = object : WebSocketClient(URI(invitedPartyAgents[0])) {
                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    println("!!!Closed WebSocket with code $code, reason $reason")
                }
                override fun onError(ex: Exception?) {
                    println("!!!WebSocket Error: $ex")
                }
                override fun onMessage(message: String?) {
                    println("!!!Message received: $message")
                }
                override fun onOpen(handshakedata: ServerHandshake?) {
                    println("!!!WebSocket opened")
                }
                override fun onWebsocketHandshakeReceivedAsClient(conn: WebSocket?, request: ClientHandshake?, response: ServerHandshake?) {
                    throw InvalidDataException(1006)
                }
            }
            conn.connect()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        Thread.sleep(7000)
        /**
         * Some of the messages should have been lost
         */
        assert(i.get() < 50)
    }
}
