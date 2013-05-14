/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.socket.server.endpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.util.Assert;
import org.springframework.web.socket.support.BeanCreatingHandlerProvider;


/**
 * An implementation of {@link javax.websocket.server.ServerEndpointConfig} that also
 * contains the target {@link javax.websocket.Endpoint}, provided either as a reference or
 * as a bean name.
 * <p>
 * {@link ServerEndpointRegistration} beans are detected by {@link ServerEndpointExporter}
 * and registered with a Java WebSocket runtime at startup.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 *
 * @see ServerEndpointExporter
 */
public class ServerEndpointRegistration implements ServerEndpointConfig, BeanFactoryAware {

	private final String path;

	private final BeanCreatingHandlerProvider<Endpoint> endpointProvider;

	private final Endpoint endpoint;

    private List<Class<? extends Encoder>> encoders = new ArrayList<Class<? extends Encoder>>();

    private List<Class<? extends Decoder>> decoders = new ArrayList<Class<? extends Decoder>>();

	private List<String> subprotocols = new ArrayList<String>();

	private List<Extension> extensions = new ArrayList<Extension>();

	private final Map<String, Object> userProperties = new HashMap<String, Object>();

	private Configurator configurator = new EndpointRegistrationConfigurator();


	/**
	 * Class constructor with the {@code javax.webscoket.Endpoint} class.
	 *
	 * @param path
	 * @param endpointClass
	 */
	public ServerEndpointRegistration(String path, Class<? extends Endpoint> endpointClass) {
		Assert.hasText(path, "path must not be empty");
		Assert.notNull(endpointClass, "endpointClass is required");
		this.path = path;
		this.endpointProvider = new BeanCreatingHandlerProvider<Endpoint>(endpointClass);
		this.endpoint = null;
	}

	public ServerEndpointRegistration(String path, Endpoint endpoint) {
		Assert.hasText(path, "path must not be empty");
		Assert.notNull(endpoint, "endpoint is required");
		this.path = path;
		this.endpointProvider = null;
		this.endpoint = endpoint;
	}


	@Override
	public String getPath() {
		return this.path;
	}

	@Override
	public Class<? extends Endpoint> getEndpointClass() {
		return (this.endpoint != null) ?
				this.endpoint.getClass() : ((Class<? extends Endpoint>) this.endpointProvider.getHandlerType());
	}

	public Endpoint getEndpoint() {
		return (this.endpoint != null) ? this.endpoint : this.endpointProvider.getHandler();
	}

	public void setSubprotocols(List<String> subprotocols) {
		this.subprotocols = subprotocols;
	}

	@Override
	public List<String> getSubprotocols() {
		return this.subprotocols;
	}

	public void setExtensions(List<Extension> extensions) {
		this.extensions = extensions;
	}

	@Override
	public List<Extension> getExtensions() {
		return this.extensions;
	}

	public void setUserProperties(Map<String, Object> userProperties) {
		this.userProperties.clear();
		this.userProperties.putAll(userProperties);
	}

	@Override
	public Map<String, Object> getUserProperties() {
		return this.userProperties;
	}

	public void setEncoders(List<Class<? extends Encoder>> encoders) {
		this.encoders = encoders;
	}

	@Override
	public List<Class<? extends Encoder>> getEncoders() {
		return this.encoders;
	}

	public void setDecoders(List<Class<? extends Decoder>> decoders) {
		this.decoders = decoders;
	}

	@Override
	public List<Class<? extends Decoder>> getDecoders() {
		return this.decoders;
	}

	@Override
	public Configurator getConfigurator() {
		return this.configurator;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		if (this.endpointProvider != null) {
			this.endpointProvider.setBeanFactory(beanFactory);
		}
	}

	protected void modifyHandshake(HandshakeRequest request, HandshakeResponse response) {
		this.configurator.modifyHandshake(this, request, response);
	}

	protected boolean checkOrigin(String originHeaderValue) {
		return this.configurator.checkOrigin(originHeaderValue);
	}

	protected String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
		return this.configurator.getNegotiatedSubprotocol(supported, requested);
	}

	protected List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
		return this.configurator.getNegotiatedExtensions(installed, requested);
	}


	private class EndpointRegistrationConfigurator extends Configurator {

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
			return (T) ServerEndpointRegistration.this.getEndpoint();
		}

		@Override
		public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
			super.modifyHandshake(sec, request, response);
		}

		@Override
		public boolean checkOrigin(String originHeaderValue) {
			return super.checkOrigin(originHeaderValue);
		}

		@Override
		public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
			return super.getNegotiatedSubprotocol(supported, requested);
		}

		@Override
		public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
			return super.getNegotiatedExtensions(installed, requested);
		}
	}

}
