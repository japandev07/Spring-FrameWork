/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.jms.support.converter;

import javax.jms.Message;

import org.springframework.messaging.mapping.HeaderMapper;

/**
 * Strategy interface for mapping messaging Message headers to an outbound
 * JMS Message (e.g. to configure JMS properties) or extracting messaging
 * header values from an inbound JMS Message.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @since 4.1
 */
public interface JmsHeaderMapper extends HeaderMapper<Message> {

	static final String CONTENT_TYPE_PROPERTY = "content_type";

}

