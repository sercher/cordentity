package com.luxoft.blockchainlab.corda.hyperledger.indy

import com.luxoft.blockchainlab.hyperledger.indy.CredentialOffer
import com.luxoft.blockchainlab.hyperledger.indy.KeyCorrectnessProof
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.net.URI
import java.net.URL

class AgentConnectionTest {

    private fun agentInitEndpoint(agentUrl: String) : String {
        /**
         * HTTP GET / in order to let the agent (pythonic indy-agent) know its endpoint address
         * indy-agent.py is incapable of determining its endpoint other than this way
         */
        val uri = URI(agentUrl)
        val rootPath = "http://" + uri.host + ":" + uri.port + "/"
        val rootUrl = URL(rootPath)
        rootUrl.openConnection().getInputStream().close()
        return agentUrl
    }

    @Ignore("Requires external services")
    @Test
    fun `externalTest`() {
        val agentUrl1 = "ws://127.0.0.1:8094/ws"
        val agentUrl2 = "ws://127.0.0.1:8095/ws"
        val agent95completed = CompletableFuture<Unit>()
        val agent94completed = CompletableFuture<Unit>()
        val agent95 = AgentConnection().connect(agentInitEndpoint(agentUrl1),"user95","pass95")
        val inviteMsg = agent95.genInvite()
        CompletableFuture.runAsync {
            agent95.run {
                waitForInvitedParty()
                val proof = receiveCredentialOffer()
                val proof2 = receiveCredentialOffer()
                val proof3 = receiveCredentialOffer()
                assertEquals(proof.schemaId,"1")
                assertEquals(proof2.schemaId,"2")
                assertEquals(proof3.schemaId,"3")

            }
        }.handle { t, u ->
            if (u != null) u.printStackTrace()
            assertNull(u)
            agent95completed.complete(Unit)
        }

        val agent94 = AgentConnection().apply {
            connect(agentInitEndpoint(agentUrl2),"user94","pass94")
            acceptInvite(inviteMsg)
            sendCredentialOffer(CredentialOffer("1", "", KeyCorrectnessProof("", "", emptyList()), ""))
            sendCredentialOffer(CredentialOffer("2", "", KeyCorrectnessProof("", "", emptyList()), ""))
            sendCredentialOffer(CredentialOffer("3", "", KeyCorrectnessProof("", "", emptyList()), ""))
            agent94completed.complete(Unit)
        }

        CompletableFuture.allOf(agent94completed,agent95completed).get()
        agent95.disconnect()
        agent94.disconnect()
    }
}