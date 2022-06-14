/*
 * Copyright (c) 2020-2022 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository at
 * https://github.com/hyperledger-labs/business-partner-agent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hyperledger.bpa.impl.aries;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.aries.api.ExchangeVersion;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;
import org.hyperledger.aries.api.issue_credential_v2.V20CredExRecord;
import org.hyperledger.aries.api.issue_credential_v2.V2IssueIndyCredentialEvent;
import org.hyperledger.aries.api.issue_credential_v2.V2IssueLDCredentialEvent;
import org.hyperledger.aries.api.message.BasicMessage;
import org.hyperledger.aries.api.present_proof.PresentationExchangeRecord;
import org.hyperledger.aries.api.present_proof_v2.V20PresExRecord;
import org.hyperledger.aries.api.present_proof_v2.V20PresExRecordToV1Converter;
import org.hyperledger.aries.api.revocation.RevocationNotificationEvent;
import org.hyperledger.aries.api.trustping.PingEvent;
import org.hyperledger.aries.webhook.EventHandler;
import org.hyperledger.bpa.impl.aries.chat.ChatMessageManager;
import org.hyperledger.bpa.impl.aries.connection.ConnectionManager;
import org.hyperledger.bpa.impl.aries.connection.PingManager;
import org.hyperledger.bpa.impl.aries.credential.HolderManager;
import org.hyperledger.bpa.impl.aries.credential.IssuerManager;
import org.hyperledger.bpa.impl.aries.jsonld.LDEventHandler;
import org.hyperledger.bpa.impl.aries.proof.ProofEventHandler;
import org.hyperledger.bpa.persistence.model.converter.ExchangePayload;

import java.util.Optional;

@Slf4j
@Singleton
public class AriesEventHandler extends EventHandler {

    private final ConnectionManager connection;

    private final Optional<PingManager> ping;

    private final HolderManager credHolder;

    private final IssuerManager credIssuer;

    private final ProofEventHandler proof;

    private final LDEventHandler jsonLD;

    private final ChatMessageManager chatMessage;

    @Inject
    public AriesEventHandler(
            ConnectionManager connectionManager,
            Optional<PingManager> pingManager,
            HolderManager holderCredentialManager,
            ProofEventHandler proofEventHandler,
            LDEventHandler jsonLD,
            IssuerManager issuerCredentialManager,
            ChatMessageManager chatMessageManager) {
        this.connection = connectionManager;
        this.ping = pingManager;
        this.credHolder = holderCredentialManager;
        this.credIssuer = issuerCredentialManager;
        this.proof = proofEventHandler;
        this.jsonLD = jsonLD;
        this.chatMessage = chatMessageManager;
    }

    @Override
    public void handleConnection(ConnectionRecord connectionRecord) {
        log.debug("Connection Event: {}", connectionRecord);
        // all events in state invitation are handled in the managers
        if (connectionRecord.stateIsInvitation()) {
            return;
        }
        synchronized (connection) {
            if (connectionRecord.isInvitationResponse()) {
                connection.handleInvitationEvent(connectionRecord);
            } else if (connectionRecord.isOutgoingConnection()) {
                connection.handleOutgoingConnectionEvent(connectionRecord);
            } else {
                connection.handleIncomingConnectionEvent(connectionRecord);
            }
        }
    }

    @Override
    public void handlePing(PingEvent ping) {
        this.ping.ifPresent(mgmt -> mgmt.handlePingEvent(ping));
    }

    @Override
    public void handleProof(PresentationExchangeRecord presExRecord) {
        log.debug("Present Proof Event: {}", presExRecord);
        synchronized (proof) {
            proof.dispatch(presExRecord);
        }
    }

    @Override
    public void handleProofV2(V20PresExRecord v2) {
        log.debug("Present Proof V2 Event: {}", v2);
        synchronized (proof) {
            if (v2.isIndy()) {
                proof.dispatch(V20PresExRecordToV1Converter.toV1(v2));
            } else if (v2.isDif()) {
                proof.dispatch(v2);
            }
        }
    }

    @Override
    public void handleCredential(V1CredentialExchange v1CredEx) {
        log.debug("Credential Event: {}", v1CredEx);
        // holder events
        if (v1CredEx.roleIsHolder()) {
            synchronized (credHolder) {
                if (v1CredEx.stateIsCredentialAcked()) {
                    credHolder.handleV1CredentialExchangeAcked(v1CredEx);
                } else if (v1CredEx.stateIsOfferReceived()) {
                    credHolder.handleOfferReceived(v1CredEx, ExchangePayload
                            .indy(v1CredEx.getCredentialProposalDict().getCredentialProposal()), ExchangeVersion.V1);
                } else {
                    credHolder.handleStateChangesOnly(
                            v1CredEx.getCredentialExchangeId(), v1CredEx.getState(),
                            v1CredEx.getUpdatedAt(), v1CredEx.getErrorMsg());
                }
            }
            // issuer events
        } else if (v1CredEx.roleIsIssuer()) {
            synchronized (credIssuer) {
                if (v1CredEx.stateIsProposalReceived()) {
                    credIssuer.handleV1CredentialProposal(v1CredEx);
                } else if (v1CredEx.stateIsRequestReceived()) {
                    credIssuer.handleV1CredentialRequest(v1CredEx);
                } else {
                    credIssuer.handleV1CredentialExchange(v1CredEx);
                }
            }
        }
    }

    @Override
    public void handleCredentialV2(V20CredExRecord v2CredEx) {
        log.debug("Credential V2 Event: {}", v2CredEx);
        if (v2CredEx.roleIsIssuer()) {
            synchronized (credIssuer) {
                if (v2CredEx.stateIsProposalReceived()) {
                    credIssuer.handleV2CredentialProposal(v2CredEx);
                } else if (v2CredEx.stateIsRequestReceived()) {
                    credIssuer.handleV2CredentialRequest(v2CredEx);
                } else {
                    credIssuer.handleV2CredentialExchange(v2CredEx);
                }
            }
        } else if (v2CredEx.roleIsHolder()) {
            synchronized (credHolder) {
                if (v2CredEx.stateIsOfferReceived()) {
                    credHolder.handleV2OfferReceived(v2CredEx);
                } else if (v2CredEx.stateIsCredentialReceived()) {
                    credHolder.handleV2CredentialReceived(v2CredEx);
                } else {
                    credHolder.handleStateChangesOnly(
                            v2CredEx.getCredentialExchangeId(), v2CredEx.getState(),
                            v2CredEx.getUpdatedAt(), v2CredEx.getErrorMsg());
                }
            }
        }
    }

    @Override
    public void handleIssueCredentialV2Indy(V2IssueIndyCredentialEvent revocationInfo) {
        log.debug("Issue Credential V2 Indy Event: {}", revocationInfo);
        synchronized (credIssuer) {
            credIssuer.handleIssueCredentialV2Indy(revocationInfo);
        }
    }

    @Override
    public void handleIssueCredentialV2LD(V2IssueLDCredentialEvent credentialInfo) {
        log.debug("Issue LD Credential V2 Event: {}", credentialInfo);
        synchronized (jsonLD) {
            jsonLD.handleIssueCredentialV2LD(credentialInfo);
        }
    }

    @Override
    public void handleBasicMessage(BasicMessage message) {
        // since basic message handling is so simple (only one way to handle it), let
        // the manager handle it.
        chatMessage.handleIncomingMessage(message);
    }

    @Override
    public void handleRevocationNotification(RevocationNotificationEvent revocationNotification) {
        credHolder.handleRevocationNotification(revocationNotification);
    }

    @Override
    public void handleRaw(String eventType, String json) {
        log.trace(json);
    }
}
