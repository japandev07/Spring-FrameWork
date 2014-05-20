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

package org.springframework.web.multipart.support;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.springframework.http.HttpHeaders;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Spring MultipartHttpServletRequest adapter, wrapping a Servlet 3.0 HttpServletRequest
 * and its Part objects. Parameters get exposed through the native request's getParameter
 * methods - without any custom processing on our side.
 *
 * @author Juergen Hoeller
 * @since 3.1
 */
public class StandardMultipartHttpServletRequest extends AbstractMultipartHttpServletRequest {

	private static final String CONTENT_DISPOSITION = "content-disposition";

	private static final String FILENAME_KEY = "filename=";

	private Set<String> multipartParameterNames;


	/**
	 * Create a new StandardMultipartHttpServletRequest wrapper for the given request,
	 * immediately parsing the multipart content.
	 * @param request the servlet request to wrap
	 * @throws MultipartException if parsing failed
	 */
	public StandardMultipartHttpServletRequest(HttpServletRequest request) throws MultipartException {
		this(request, false);
	}

	/**
	 * Create a new StandardMultipartHttpServletRequest wrapper for the given request.
	 * @param request the servlet request to wrap
	 * @param lazyParsing whether multipart parsing should be triggered lazily on
	 * first access of multipart files or parameters
	 * @throws MultipartException if an immediate parsing attempt failed
	 */
	public StandardMultipartHttpServletRequest(HttpServletRequest request, boolean lazyParsing) throws MultipartException {
		super(request);
		if (!lazyParsing) {
			parseRequest(request);
		}
	}


	private void parseRequest(HttpServletRequest request) {
		try {
			Collection<Part> parts = request.getParts();
			this.multipartParameterNames = new LinkedHashSet<String>(parts.size());
			MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<String, MultipartFile>(parts.size());
			for (Part part : parts) {
				String filename = extractFilename(part.getHeader(CONTENT_DISPOSITION));
				if (filename != null) {
					files.add(part.getName(), new StandardMultipartFile(part, filename));
				}
				else {
					this.multipartParameterNames.add(part.getName());
				}
			}
			setMultipartFiles(files);
		}
		catch (Exception ex) {
			throw new MultipartException("Could not parse multipart servlet request", ex);
		}
	}

	private String extractFilename(String contentDisposition) {
		if (contentDisposition == null) {
			return null;
		}
		// TODO: can only handle the typical case at the moment
		int startIndex = contentDisposition.indexOf(FILENAME_KEY);
		if (startIndex == -1) {
			return null;
		}
		String filename = contentDisposition.substring(startIndex + FILENAME_KEY.length());
		if (filename.startsWith("\"")) {
			int endIndex = filename.indexOf("\"", 1);
			if (endIndex != -1) {
				return filename.substring(1, endIndex);
			}
		}
		else {
			int endIndex = filename.indexOf(";");
			if (endIndex != -1) {
				return filename.substring(0, endIndex);
			}
		}
		return filename;
	}


	@Override
	protected void initializeMultipart() {
		parseRequest(getRequest());
	}

	@Override
	public Enumeration<String> getParameterNames() {
		if (this.multipartParameterNames == null) {
			initializeMultipart();
		}
		if (this.multipartParameterNames.isEmpty()) {
			return super.getParameterNames();
		}

		// Servlet 3.0 getParameterNames() not guaranteed to include multipart form items
		// (e.g. on WebLogic 12) -> need to merge them here to be on the safe side
		Set<String> paramNames = new LinkedHashSet<String>();
		Enumeration<String> paramEnum = super.getParameterNames();
		while (paramEnum.hasMoreElements()) {
			paramNames.add(paramEnum.nextElement());
		}
		paramNames.addAll(this.multipartParameterNames);
		return Collections.enumeration(paramNames);
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		if (this.multipartParameterNames == null) {
			initializeMultipart();
		}
		if (this.multipartParameterNames.isEmpty()) {
			return super.getParameterMap();
		}

		// Servlet 3.0 getParameterMap() not guaranteed to include multipart form items
		// (e.g. on WebLogic 12) -> need to merge them here to be on the safe side
		Map<String, String[]> paramMap = new LinkedHashMap<String, String[]>();
		paramMap.putAll(super.getParameterMap());
		for (String paramName : this.multipartParameterNames) {
			if (!paramMap.containsKey(paramName)) {
				paramMap.put(paramName, getParameterValues(paramName));
			}
		}
		return paramMap;
	}

	@Override
	public String getMultipartContentType(String paramOrFileName) {
		try {
			Part part = getPart(paramOrFileName);
			return (part != null ? part.getContentType() : null);
		}
		catch (Exception ex) {
			throw new MultipartException("Could not access multipart servlet request", ex);
		}
	}

	@Override
	public HttpHeaders getMultipartHeaders(String paramOrFileName) {
		try {
			Part part = getPart(paramOrFileName);
			if (part != null) {
				HttpHeaders headers = new HttpHeaders();
				for (String headerName : part.getHeaderNames()) {
					headers.put(headerName, new ArrayList<String>(part.getHeaders(headerName)));
				}
				return headers;
			}
			else {
				return null;
			}
		}
		catch (Exception ex) {
			throw new MultipartException("Could not access multipart servlet request", ex);
		}
	}


	/**
	 * Spring MultipartFile adapter, wrapping a Servlet 3.0 Part object.
	 */
	private static class StandardMultipartFile implements MultipartFile {

		private final Part part;

		private final String filename;

		public StandardMultipartFile(Part part, String filename) {
			this.part = part;
			this.filename = filename;
		}

		@Override
		public String getName() {
			return this.part.getName();
		}

		@Override
		public String getOriginalFilename() {
			return this.filename;
		}

		@Override
		public String getContentType() {
			return this.part.getContentType();
		}

		@Override
		public boolean isEmpty() {
			return (this.part.getSize() == 0);
		}

		@Override
		public long getSize() {
			return this.part.getSize();
		}

		@Override
		public byte[] getBytes() throws IOException {
			return FileCopyUtils.copyToByteArray(this.part.getInputStream());
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return this.part.getInputStream();
		}

		@Override
		public void transferTo(File dest) throws IOException, IllegalStateException {
			this.part.write(dest.getPath());
		}
	}

}
