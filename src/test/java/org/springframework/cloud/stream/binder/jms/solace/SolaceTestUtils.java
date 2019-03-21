/*
 *  Copyright 2002-2016 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.stream.binder.jms.solace;

import com.solacesystems.jcsmp.*;
import com.solacesystems.jcsmp.impl.XMLContentMessageImpl;
import com.solacesystems.jcsmp.transaction.TransactedSession;
import com.solacesystems.jms.SolConnectionFactoryImpl;
import com.solacesystems.jms.SolJmsUtility;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.cloud.stream.binder.jms.solace.config.SolaceConfigurationProperties;
import org.springframework.core.io.ClassPathResource;

import com.solacesystems.jms.property.JMSProperties;
import javax.jms.ConnectionFactory;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solacesystems.jcsmp.JCSMPSession.FLAG_IGNORE_DOES_NOT_EXIST;
import static com.solacesystems.jcsmp.JCSMPSession.WAIT_FOR_CONFIRM;

public class SolaceTestUtils {

    public static final String APPLICATION_YML = "application.yml";

    public static final String DLQ_NAME = "#DEAD_MSG_QUEUE";
    public static final Queue DLQ = JCSMPFactory.onlyInstance().createQueue(DLQ_NAME);

    /**
     * Gets solace properties within application.yml, without requiring a Spring App.
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static SolaceConfigurationProperties getSolaceProperties() throws Exception {
        YamlMapFactoryBean factoryBean = new YamlMapFactoryBean();
        factoryBean.setResources(new ClassPathResource(APPLICATION_YML));

        Map<String, Object> mapObject = factoryBean.getObject();
        Map<String, String> solacePropertyMap = (Map<String, String>) mapObject.get("solace");

        SolaceConfigurationProperties solaceConfigurationProperties = new SolaceConfigurationProperties();
        solaceConfigurationProperties.setMaxRedeliveryAttempts(null);
        solaceConfigurationProperties.setUsername(solacePropertyMap.get("username"));
        solaceConfigurationProperties.setPassword(solacePropertyMap.get("password"));
        solaceConfigurationProperties.setHost(solacePropertyMap.get("host"));

        return solaceConfigurationProperties;
    }

    public static ConnectionFactory createConnectionFactory() throws Exception {
        SolaceConfigurationProperties solaceProperties = getSolaceProperties();
        JMSProperties properties = new JMSProperties((Hashtable<?, ?>) null);
        SolConnectionFactoryImpl solConnectionFactory = new SolConnectionFactoryImpl(properties);
        solConnectionFactory.setProperty("Host", solaceProperties.getHost());
        solConnectionFactory.setProperty("Username", solaceProperties.getUsername());
        solConnectionFactory.setProperty("Password", solaceProperties.getPassword());
        //Disabling direct transport allows JMS to use transacted sessions. Enabling at the same time
        //DLQ routing if maxRedeliveryAttempts is set
        solConnectionFactory.setDirectTransport(false);
        return solConnectionFactory;
    }

    public static JCSMPSession createSession() {
        try {
            return new SolaceQueueProvisioner.SessionFactory(getSolaceProperties()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void deprovisionDLQ() throws JCSMPException {
        createSession().deprovision(DLQ, WAIT_FOR_CONFIRM | FLAG_IGNORE_DOES_NOT_EXIST);
    }

    public static BytesXMLMessage waitForDeadLetter() {
        return waitForDeadLetter(2000);
    }

    public static BytesXMLMessage waitForDeadLetter(int timeout) {

        ConsumerFlowProperties consumerFlowProperties = new ConsumerFlowProperties();
        consumerFlowProperties.setEndpoint(DLQ);

        CountDownLatch latch = new CountDownLatch(1);
        CountingListener countingListener = new CountingListener(latch);

        try {
            FlowReceiver consumer = createSession().createFlow(countingListener, consumerFlowProperties);
            consumer.start();

            boolean success = countingListener.awaitExpectedMessages(timeout);
            return success ? countingListener.getMessages().get(0) : null;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class CountingListener implements XMLMessageListener {
        private final CountDownLatch latch;

        private final List<JCSMPException> errors = new ArrayList<>();

        private final List<String> payloads = new ArrayList<>();
        private final List<BytesXMLMessage> messages = new ArrayList<>();

        public CountingListener(CountDownLatch latch) {
            this.latch = latch;
        }

        public CountingListener(int expectedMessages) {
            this.latch = new CountDownLatch(expectedMessages);
        }

        public List<BytesXMLMessage> getMessages() {
            return messages;
        }

        @Override
        public void onReceive(BytesXMLMessage bytesXMLMessage) {
            if (bytesXMLMessage instanceof XMLContentMessageImpl) {
                payloads.add(((XMLContentMessageImpl)bytesXMLMessage).getXMLContent());
            }
            else {
                payloads.add(bytesXMLMessage.toString());
            }

            messages.add(bytesXMLMessage);
            latch.countDown();
        }

        @Override
        public void onException(JCSMPException e) {
            errors.add(e);
        }

        boolean awaitExpectedMessages() throws InterruptedException {
            return awaitExpectedMessages(2000);
        }

        boolean awaitExpectedMessages(int timeout) throws InterruptedException {
            return latch.await(timeout, TimeUnit.MILLISECONDS);
        }

        List<JCSMPException> getErrors() {
            return errors;
        }

        List<String> getPayloads() {
            return payloads;
        }
    }

    public static class RollbackListener implements XMLMessageListener {

        private AtomicInteger receivedMessageCount = new AtomicInteger();

        private TransactedSession transactedSession;

        public RollbackListener(TransactedSession transactedSession) {
            this.transactedSession = transactedSession;
        }

        @Override
        public void onReceive(BytesXMLMessage bytesXMLMessage) {
            receivedMessageCount.incrementAndGet();
            try {
                transactedSession.rollback();
            } catch (JCSMPException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onException(JCSMPException e) {

        }

        public int getReceivedMessageCount() {
            return receivedMessageCount.get();
        }
    }

}
