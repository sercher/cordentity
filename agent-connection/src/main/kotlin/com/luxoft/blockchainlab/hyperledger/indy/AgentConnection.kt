package com.luxoft.blockchainlab.hyperledger.indy

import org.hyperledger.indy.sdk.did.Did
//import okhttp3.WebSocket
//import io.ktor.websocket.*

enum class ConnectionStatus { AGENT_CONNECTION_CONNECTED, AGENT_CONNECTION_DISCONNECTED }

data class IndyParty(val did: Did, val verkey: String, val endpoint: String)

interface Connection {
    fun getConnectionStatus(): ConnectionStatus
    fun getCounterParty(): IndyParty

    fun sendCredentialOffer(offer: CredentialOffer)
    fun receiveCredentialOffer(): CredentialOffer

    fun sendCredentialRequest(request: CredentialRequestInfo)
    fun receiveCredentialRequest(): CredentialRequestInfo

    fun sendCredential(credential: CredentialInfo)
    fun receiveCredential(): CredentialInfo

    fun sendProofRequest(request: ProofRequest)
    fun receiveProofRequest(): ProofRequest

    fun sendProof(proof: Proof)
    fun receiveProof(): Proof

    fun getInvite(): String
    fun acceptInvite(invite: String)
}

class AgentConnection:Connection{
    override fun getConnectionStatus(): ConnectionStatus {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptInvite(invite: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getInvite(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCounterParty(): IndyParty {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendCredentialOffer(offer: CredentialOffer) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveCredentialOffer(): CredentialOffer {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendCredentialRequest(request: CredentialRequestInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveCredentialRequest(): CredentialRequestInfo {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendCredential(credential: CredentialInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveCredential(): CredentialInfo {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendProofRequest(request: ProofRequest) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveProofRequest(): ProofRequest {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendProof(proof: Proof) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveProof(): Proof {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}