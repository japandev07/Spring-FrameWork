/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.web.multipart.support;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * Abstract base implementation of the MultipartHttpServletRequest interface.
 * Provides management of pre-generated MultipartFile instances.
 *
 * @author Juergen Hoeller
 * @since 06.10.2003
 */
public abstract class AbstractMultipartHttpServletRequest extends HttpServletRequestWrapper
	implements MultipartHttpServletRequest {

	private Map<String, MultipartFile> multipartFiles;


	/**
	 * Wrap the given HttpServletRequest in a MultipartHttpServletRequest.
	 * @param request the request to wrap
	 */
	protected AbstractMultipartHttpServletRequest(HttpServletRequest request) {
		super(request);
	}


	public Iterator<String> getFileNames() {
		return getMultipartFiles().keySet().iterator();
	}

	public MultipartFile getFile(String name) {
		return getMultipartFiles().get(name);
	}

	public Map<String, MultipartFile> getFileMap() {
		return getMultipartFiles();
	}


	/**
	 * Set a Map with parameter names as keys and MultipartFile objects as values.
	 * To be invoked by subclasses on initialization.
	 */
	protected final void setMultipartFiles(Map<String, MultipartFile> multipartFiles) {
		this.multipartFiles = Collections.unmodifiableMap(multipartFiles);
	}

	/**
	 * Obtain the MultipartFile Map for retrieval,
	 * lazily initializing it if necessary.
	 * @see #initializeMultipart()
	 */
	protected Map<String, MultipartFile> getMultipartFiles() {
		if (this.multipartFiles == null) {
			initializeMultipart();
		}
		return this.multipartFiles;
	}

	/**
	 * Lazily initialize the multipart request, if possible.
	 * Only called if not already eagerly initialized.
	 */
	protected void initializeMultipart() {
		throw new IllegalStateException("Multipart request not initialized");
	}

}
