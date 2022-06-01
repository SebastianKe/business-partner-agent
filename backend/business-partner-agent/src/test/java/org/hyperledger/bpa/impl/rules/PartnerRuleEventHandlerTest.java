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
package org.hyperledger.bpa.impl.rules;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.aries.config.GsonConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.hyperledger.bpa.api.exception.WrongApiUsageException;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.connection.ConnectionState;
import org.hyperledger.bpa.api.notification.PartnerAddedEvent;
import org.hyperledger.bpa.impl.aries.connection.ConnectionManager;
import org.hyperledger.bpa.persistence.model.Partner;
import org.hyperledger.bpa.persistence.model.Tag;
import org.hyperledger.bpa.persistence.repository.PartnerRepository;
import org.hyperledger.bpa.BaseTest;
import org.hyperledger.bpa.impl.aries.AriesEventHandler;
import org.hyperledger.bpa.persistence.repository.RulesRepository;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@MicronautTest
public class PartnerRuleEventHandlerTest extends BaseTest {

    private final Gson gson = GsonConfig.defaultConfig();

    //@Inject
    PartnerRuleEventHandler partnerRuleEventHandler;

    @Inject
    RulesService rs;

    //@Inject
    //AriesEventHandler ariesEventHandler;
    @Inject
    ConnectionManager manager;

    @Inject
    PartnerRepository repo;

    @Inject
    RulesRepository rrepo;

    @Test
    void testTest() {
        assert (true);
    }

    static Boolean triggered = false;

    public static class EventTriggered extends RulesData.Action {

        @Override
        void run(EventContext ctx) {
            triggered = true;
            assert (false);
        }

        public static EventTriggered convert(String source) {
            return new EventTriggered();
        }
    }


    /*
    @Bean
    @Singleton
    public TypeConverter<String, RulesData.Action> stringToEventTriggered() {
    return TypeConverter.of(
        String.class,
        RulesData.Action.class,
        EventTriggered::convert
    );
    }
     */


    @Test
    void testNewPartnerOnConnection() throws InterruptedException {

        RulesData data = rs.add(new RulesData.Trigger.EventTrigger(PartnerAddedEvent.class.getSimpleName()), new RulesData.Action.TagConnection("", "action tag"));

        log.debug(data.toString());

        assertEquals(rs.getAll().size(), 1);

        log.debug(rs.getAll().toString());
        // added asynchron?

        String connectionId = "de0d51e8-4c7f-4dc9-8b7b-a8f57182d8a5";

        repo.save(Partner
                .builder()
                .ariesSupport(Boolean.TRUE)
                .connectionId(connectionId)
                .alias("Alice")
                .state(ConnectionState.INIT)
                .did("dummy")
                .build());
        final ConnectionRecord invite = gson.fromJson(connInvitationInvite, ConnectionRecord.class);

        manager.handleOutgoingConnectionEvent(invite);

        Optional<Partner> p = repo.findByConnectionId(invite.getConnectionId()); assertTrue(p.isPresent());
        //ariesEventHandler.handleConnection(invite);

        /*
        Optional<Partner> p = repo.findByConnectionId(invite.getConnectionId());
        assertTrue(p.isPresent());
        assertEquals(ConnectionState.REQUEST, p.get().getState());
        assertEquals("Alice", p.get().getAlias());
        assertNotNull(p.get().getConnectionId());

        final ConnectionRecord active = gson.fromJson(connInvitationActive, ConnectionRecord.class);
        ariesEventHandler.handleConnection(active);

        p = repo.findByConnectionId(active.getConnectionId());
        assertTrue(p.isPresent());
        assertEquals(ConnectionState.COMPLETED, p.get().getState());
        assert (true);
        Thread.sleep(2000);
        assert (triggered);
        */
    }

    private final String connInvitationInvite = """
            {
                "invitation_mode": "once",
                "their_role": "inviter",
                "connection_protocol": "didexchange/1.0",
                "rfc23_state": "request-sent",
                "accept": "manual",
                "routing_state": "none",
                "alias": "a950b832-59ac-480c-8135-e76ba76f03ba",
                "updated_at": "2021-07-05T13:31:09.179622Z",
                "their_did": "did:sov:EraYCDJUPsChbkw7S1vV96",
                "their_public_did": "did:sov:EraYCDJUPsChbkw7S1vV96",
                "state": "request",
                "my_did": "F6dB7dMVHUQSC64qemnBi7",
                "request_id": "e7b668eb-2a26-4dc0-84e5-73b0f2c0fe05",
                "connection_id": "de0d51e8-4c7f-4dc9-8b7b-a8f57182d8a5",
                "created_at": "2021-07-05T13:31:09.179622Z"
            }""";

    private final String connInvitationActive = """
            {
                "invitation_mode": "once",
                "their_role": "inviter",
                "connection_protocol": "didexchange/1.0",
                "rfc23_state": "completed",
                "accept": "manual",
                "routing_state": "none",
                "alias": "a950b832-59ac-480c-8135-e76ba76f03ba",
                "updated_at": "2021-07-05T13:32:29.689513Z",
                "their_did": "8hXCW94BRYSm2PQeFHFcV1",
                "their_public_did": "did:sov:EraYCDJUPsChbkw7S1vV96",
                "state": "completed",
                "my_did": "F6dB7dMVHUQSC64qemnBi7",
                "request_id": "e7b668eb-2a26-4dc0-84e5-73b0f2c0fe05",
                "connection_id": "de0d51e8-4c7f-4dc9-8b7b-a8f57182d8a5",
                "created_at": "2021-07-05T13:31:09.179622Z"
            }""";
}
