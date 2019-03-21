/*
 *  Copyright 2016 the original author or authors.
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

import javax.jms.ConnectionFactory;

import org.springframework.cloud.stream.binder.AbstractBinderTests;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.cloud.stream.binder.Spy;
import org.springframework.cloud.stream.binder.jms.JMSMessageChannelBinder;
import org.springframework.cloud.stream.binder.jms.spi.QueueProvisioner;
import org.springframework.cloud.stream.binder.jms.utils.DestinationNameResolver;
import org.springframework.cloud.stream.binder.jms.utils.JmsMessageDrivenChannelAdapterFactory;
import org.springframework.cloud.stream.binder.jms.utils.JmsSendingMessageHandlerFactory;
import org.springframework.cloud.stream.binder.jms.utils.ListenerContainerFactory;
import org.springframework.cloud.stream.binder.jms.utils.MessageRecoverer;
import org.springframework.cloud.stream.binder.jms.utils.RepublishMessageRecoverer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jms.core.JmsTemplate;

/**
 * @author Ilayaperumal Gopinathan
 */
public class SolaceBinderTests extends AbstractBinderTests<SolaceTestBinder, ConsumerProperties,
		ProducerProperties> {

	@Override
	protected SolaceTestBinder getBinder() throws Exception {
		ConnectionFactory connectionFactory = SolaceTestUtils.createConnectionFactory();
		QueueProvisioner queueProvisioner = new SolaceQueueProvisioner(SolaceTestUtils.getSolaceProperties());
		GenericApplicationContext applicationContext = new GenericApplicationContext();
		applicationContext.refresh();
		JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
		JmsSendingMessageHandlerFactory jmsSendingMessageHandlerFactory = new JmsSendingMessageHandlerFactory(jmsTemplate, applicationContext.getBeanFactory());
		DestinationNameResolver destinationNameResolver = new DestinationNameResolver();
		ListenerContainerFactory listenerContainerFactory = new ListenerContainerFactory(connectionFactory);
		MessageRecoverer messageRecoverer = new RepublishMessageRecoverer(queueProvisioner, jmsTemplate);
		JmsMessageDrivenChannelAdapterFactory jmsMessageDrivenChannelAdapterFactory = new JmsMessageDrivenChannelAdapterFactory(listenerContainerFactory, messageRecoverer, destinationNameResolver);
		JMSMessageChannelBinder binder = new JMSMessageChannelBinder(queueProvisioner, new DestinationNameResolver(), jmsSendingMessageHandlerFactory, jmsMessageDrivenChannelAdapterFactory);
		binder.setApplicationContext(applicationContext);
		SolaceTestBinder testBinder = new SolaceTestBinder();
		testBinder.setBinder(binder);
		return testBinder;
	}

	@Override
	protected ConsumerProperties createConsumerProperties() {
		return new ConsumerProperties();
	}

	@Override
	protected ProducerProperties createProducerProperties() {
		return new ProducerProperties();
	}

	@Override
	public Spy spyOn(String name) {
		throw new UnsupportedOperationException("'spyOn' is not used by JMS tests");
	}
}
