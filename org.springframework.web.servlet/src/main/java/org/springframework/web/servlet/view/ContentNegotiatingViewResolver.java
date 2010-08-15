/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.web.servlet.view;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.WebUtils;

/**
 * Implementation of {@link ViewResolver} that resolves a view based on the request file name or {@code Accept} header.
 *
 * <p>The {@code ContentNegotiatingViewResolver} does not resolve views itself, but delegates to other {@link
 * ViewResolver}s. By default, these other view resolvers are picked up automatically from the application context,
 * though they can also be set explicitly by using the {@link #setViewResolvers(List) viewResolvers} property.
 * <strong>Note</strong> that in order for this view resolver to work properly, the {@link #setOrder(int) order}
 * property needs to be set to a higher precedence than the others (the default is {@link Ordered#HIGHEST_PRECEDENCE}.)
 *
 * <p>This view resolver uses the requested {@linkplain MediaType media type} to select a suitable {@link View} for a
 * request. This media type is determined by using the following criteria:
 * <ol>
 * <li>If the requested path has a file extension and if the {@link #setFavorPathExtension(boolean)} property is
 * {@code true}, the {@link #setMediaTypes(Map) mediaTypes} property is inspected for a matching media type.</li>
 * <li>If the request contains a parameter defining the extension and if the {@link #setFavorParameter(boolean)}
 * property is <code>true</code>, the {@link #setMediaTypes(Map) mediaTypes} property is inspected for a matching
 * media type. The default name of the parameter is <code>format</code> and it can be configured using the
 * {@link #setParameterName(String) parameterName} property.</li>
 * <li>If there is no match in the {@link #setMediaTypes(Map) mediaTypes} property and if the Java Activation
 * Framework (JAF) is both {@linkplain #setUseJaf(boolean) enabled} and present on the class path,
 * {@link FileTypeMap#getContentType(String)} is used instead.</li>
 * <li>If the previous steps did not result in a media type, and
 * {@link #setIgnoreAcceptHeader(boolean) ignoreAcceptHeader} is {@code false}, the request {@code Accept} header is
 * used.</li>
 * </ol>
 *
 * Once the requested media type has been determined, this resolver queries each delegate view resolver for a
 * {@link View} and determines if the requested media type is {@linkplain MediaType#includes(MediaType) compatible}
 * with the view's {@linkplain View#getContentType() content type}). The most compatible view is returned.
 *
 * <p>Additionally, this view resolver exposes the {@link #setDefaultViews(List) defaultViews} property, allowing you to
 * override the views provided by the view resolvers. Note that these default views are offered as candicates, and
 * still need have the content type requested (via file extension, parameter, or {@code Accept} header, described above).
 * You can also set the {@linkplain #setDefaultContentType(MediaType) default content type} directly, which will be
 * returned when the other mechanisms ({@code Accept} header, file extension or parameter) do not result in a match.
 *
 * <p>For example, if the request path is {@code /view.html}, this view resolver will look for a view that has the
 * {@code text/html} content type (based on the {@code html} file extension). A request for {@code /view} with a {@code
 * text/html} request {@code Accept} header has the same result.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 * @see ViewResolver
 * @see InternalResourceViewResolver
 * @see BeanNameViewResolver
 */
public class ContentNegotiatingViewResolver extends WebApplicationObjectSupport implements ViewResolver, Ordered {

	private static final Log logger = LogFactory.getLog(ContentNegotiatingViewResolver.class);

	private static final String ACCEPT_HEADER = "Accept";

	private static final boolean jafPresent =
			ClassUtils.isPresent("javax.activation.FileTypeMap", ContentNegotiatingViewResolver.class.getClassLoader());

	private static final UrlPathHelper urlPathHelper = new UrlPathHelper();


	private int order = Ordered.HIGHEST_PRECEDENCE;

	private boolean favorPathExtension = true;

	private boolean favorParameter = false;

	private String parameterName = "format";

	private boolean useNotAcceptableStatusCode = false;

	private boolean ignoreAcceptHeader = false;

	private boolean useJaf = true;

	private ConcurrentMap<String, MediaType> mediaTypes = new ConcurrentHashMap<String, MediaType>();

	private List<View> defaultViews;

	private MediaType defaultContentType;

	private List<ViewResolver> viewResolvers;


	public void setOrder(int order) {
		this.order = order;
	}

	public int getOrder() {
		return this.order;
	}

	/**
	 * Indicates whether the extension of the request path should be used to determine the requested media type,
	 * in favor of looking at the {@code Accept} header. The default value is {@code true}.
	 * <p>For instance, when this flag is <code>true</code> (the default), a request for {@code /hotels.pdf}
	 * will result in an {@code AbstractPdfView} being resolved, while the {@code Accept} header can be the
	 * browser-defined {@code text/html,application/xhtml+xml}.
	 */
	public void setFavorPathExtension(boolean favorPathExtension) {
		this.favorPathExtension = favorPathExtension;
	}

	/**
	 * Indicates whether a request parameter should be used to determine the requested media type,
	 * in favor of looking at the {@code Accept} header. The default value is {@code false}.
	 * <p>For instance, when this flag is <code>true</code>, a request for {@code /hotels?format=pdf} will result
	 * in an {@code AbstractPdfView} being resolved, while the {@code Accept} header can be the browser-defined
	 * {@code text/html,application/xhtml+xml}.
	 */
	public void setFavorParameter(boolean favorParameter) {
		this.favorParameter = favorParameter;
	}

	/**
	 * Sets the parameter name that can be used to determine the requested media type if the {@link
	 * #setFavorParameter(boolean)} property is {@code true}. The default parameter name is {@code format}.
	 */
	public void setParameterName(String parameterName) {
		this.parameterName = parameterName;
	}

	/**
	 * Indicates whether the HTTP {@code Accept} header should be ignored. Default is {@code false}.
	 * If set to {@code true}, this view resolver will only refer to the file extension and/or paramter,
	 * as indicated by the {@link #setFavorPathExtension(boolean) favorPathExtension} and
	 * {@link #setFavorParameter(boolean) favorParameter} properties.
	 */
	public void setIgnoreAcceptHeader(boolean ignoreAcceptHeader) {
		this.ignoreAcceptHeader = ignoreAcceptHeader;
	}

	/**
	 * Indicates whether a {@link HttpServletResponse#SC_NOT_ACCEPTABLE 406 Not Acceptable} status code should be
	 * returned if no suitable view can be found.
	 *
	 * <p>Default is {@code false}, meaning that this view resolver returns {@code null} for
	 * {@link #resolveViewName(String, Locale)} when an acceptable view cannot be found. This will allow for view
	 * resolvers chaining. When this property is set to {@code true},
	 * {@link #resolveViewName(String, Locale)} will respond with a view that sets the response status to
	 * {@code 406 Not Acceptable} instead.
	 */
	public void setUseNotAcceptableStatusCode(boolean useNotAcceptableStatusCode) {
		this.useNotAcceptableStatusCode = useNotAcceptableStatusCode;
	}

	/**
	 * Sets the mapping from file extensions to media types.
	 * <p>When this mapping is not set or when an extension is not present, this view resolver
	 * will fall back to using a {@link FileTypeMap} when the Java Action Framework is available.
	 */
	public void setMediaTypes(Map<String, String> mediaTypes) {
		Assert.notNull(mediaTypes, "'mediaTypes' must not be null");
		for (Map.Entry<String, String> entry : mediaTypes.entrySet()) {
			String extension = entry.getKey().toLowerCase(Locale.ENGLISH);
			MediaType mediaType = MediaType.parseMediaType(entry.getValue());
			this.mediaTypes.put(extension, mediaType);
		}
	}

	/**
	 * Sets the default views to use when a more specific view can not be obtained
	 * from the {@link ViewResolver} chain.
	 */
	public void setDefaultViews(List<View> defaultViews) {
		this.defaultViews = defaultViews;
	}

	/**
	 * Sets the default content type.
	 * <p>This content type will be used when file extension, parameter, nor {@code Accept}
	 * header define a content-type, either through being disabled or empty.
	 */
	public void setDefaultContentType(MediaType defaultContentType) {
		this.defaultContentType = defaultContentType;
	}

	/**
	 * Indicates whether to use the Java Activation Framework to map from file extensions to media types.
	 * <p>Default is {@code true}, i.e. the Java Activation Framework is used (if available).
	 */
	public void setUseJaf(boolean useJaf) {
		this.useJaf = useJaf;
	}

	/**
	 * Sets the view resolvers to be wrapped by this view resolver.
	 * <p>If this property is not set, view resolvers will be detected automatically.
	 */
	public void setViewResolvers(List<ViewResolver> viewResolvers) {
		this.viewResolvers = viewResolvers;
	}


	@Override
	protected void initServletContext(ServletContext servletContext) {
		if (this.viewResolvers == null) {
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(getApplicationContext(), ViewResolver.class);
			this.viewResolvers = new ArrayList<ViewResolver>(matchingBeans.size());
			for (ViewResolver viewResolver : matchingBeans.values()) {
				if (this != viewResolver) {
					this.viewResolvers.add(viewResolver);
				}
			}
		}
		if (this.viewResolvers.isEmpty()) {
			logger.warn("Did not find any ViewResolvers to delegate to; please configure them using the " +
					"'viewResolvers' property on the ContentNegotiatingViewResolver");
		}
		OrderComparator.sort(this.viewResolvers);
	}

	/**
	 * Determines the list of {@link MediaType} for the given {@link HttpServletRequest}.
	 * <p>The default implementation invokes {@link #getMediaTypeFromFilename(String)} if {@linkplain
	 * #setFavorPathExtension(boolean) favorPathExtension} property is <code>true</code>. If the property is
	 * <code>false</code>, or when a media type cannot be determined from the request path, this method will
	 * inspect the {@code Accept} header of the request.
	 * <p>This method can be overriden to provide a different algorithm.
	 * @param request the current servlet request
	 * @return the list of media types requested, if any
	 */
	protected List<MediaType> getMediaTypes(HttpServletRequest request) {
		if (this.favorPathExtension) {
			String requestUri = urlPathHelper.getRequestUri(request);
			String filename = WebUtils.extractFullFilenameFromUrlPath(requestUri);
			MediaType mediaType = getMediaTypeFromFilename(filename);
			if (mediaType != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Requested media type is '" + mediaType + "' (based on filename '" + filename + "')");
				}
				return Collections.singletonList(mediaType);
			}
		}
		if (this.favorParameter) {
			if (request.getParameter(this.parameterName) != null) {
				String parameterValue = request.getParameter(this.parameterName);
				MediaType mediaType = getMediaTypeFromParameter(parameterValue);
				if (mediaType != null) {
					if (logger.isDebugEnabled()) {
						logger.debug("Requested media type is '" + mediaType + "' (based on parameter '" +
								this.parameterName + "'='" + parameterValue + "')");
					}
					return Collections.singletonList(mediaType);
				}
			}
		}
		if (!this.ignoreAcceptHeader) {
			String acceptHeader = request.getHeader(ACCEPT_HEADER);
			if (StringUtils.hasText(acceptHeader)) {
				List<MediaType> mediaTypes = MediaType.parseMediaTypes(acceptHeader);
				MediaType.sortByQualityValue(mediaTypes);
				if (logger.isDebugEnabled()) {
					logger.debug("Requested media types are " + mediaTypes + " (based on Accept header)");
				}
				return mediaTypes;
			}
		}
		if (this.defaultContentType != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Requested media types is " + this.defaultContentType +
						" (based on defaultContentType property)");
			}
			return Collections.singletonList(this.defaultContentType);
		}
		else {
			return Collections.emptyList();
		}
	}

	/**
	 * Determines the {@link MediaType} for the given filename.
	 * <p>The default implementation will check the {@linkplain #setMediaTypes(Map) media types}
	 * property first for a defined mapping. If not present, and if the Java Activation Framework
	 * can be found on the classpath, it will call {@link FileTypeMap#getContentType(String)}
	 * <p>This method can be overriden to provide a different algorithm.
	 * @param filename the current request file name (i.e. {@code hotels.html})
	 * @return the media type, if any
	 */
	protected MediaType getMediaTypeFromFilename(String filename) {
		String extension = StringUtils.getFilenameExtension(filename);
		if (!StringUtils.hasText(extension)) {
			return null;
		}
		extension = extension.toLowerCase(Locale.ENGLISH);
		MediaType mediaType = this.mediaTypes.get(extension);
		if (mediaType == null && this.useJaf && jafPresent) {
			mediaType = ActivationMediaTypeFactory.getMediaType(filename);
			if (mediaType != null) {
				this.mediaTypes.putIfAbsent(extension, mediaType);
			}
		}
		return mediaType;
	}

	/**
	 * Determines the {@link MediaType} for the given parameter value.
	 * <p>The default implementation will check the {@linkplain #setMediaTypes(Map) media types}
	 * property for a defined mapping.
	 * <p>This method can be overriden to provide a different algorithm.
	 * @param parameterValue the parameter value (i.e. {@code pdf}).
	 * @return the media type, if any
	 */
	protected MediaType getMediaTypeFromParameter(String parameterValue) {
		return this.mediaTypes.get(parameterValue.toLowerCase(Locale.ENGLISH));
	}

	public View resolveViewName(String viewName, Locale locale) throws Exception {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.isInstanceOf(ServletRequestAttributes.class, attrs);
		List<MediaType> requestedMediaTypes = getMediaTypes(((ServletRequestAttributes) attrs).getRequest());
		List<View> candidateViews = getCandidateViews(viewName, locale, requestedMediaTypes);
		View bestView = getBestView(candidateViews, requestedMediaTypes);
		if (bestView != null) {
			return bestView;
		}
		else {
			if (this.useNotAcceptableStatusCode) {
				if (logger.isDebugEnabled()) {
					logger.debug("No acceptable view found; returning 406 (Not Acceptable) status code");
				}
				return NOT_ACCEPTABLE_VIEW;
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("No acceptable view found; returning null");
				}
				return null;
			}
		}
	}

	private List<View> getCandidateViews(String viewName, Locale locale, List<MediaType> requestedMediaTypes)
			throws Exception {

		List<View> candidateViews = new ArrayList<View>();
		for (ViewResolver viewResolver : this.viewResolvers) {
			View view = viewResolver.resolveViewName(viewName, locale);
			if (view != null) {
				candidateViews.add(view);
			}
			for (MediaType requestedMediaType : requestedMediaTypes) {
				List<String> extensions = getExtensionsForMediaType(requestedMediaType);
				for (String extension : extensions) {
					String viewNameWithExtension = viewName + "." + extension;
					view = viewResolver.resolveViewName(viewNameWithExtension, locale);
					if (view != null) {
						candidateViews.add(view);
					}
				}

			}
		}
		if (!CollectionUtils.isEmpty(this.defaultViews)) {
			candidateViews.addAll(this.defaultViews);
		}
		return candidateViews;
	}

	private List<String> getExtensionsForMediaType(MediaType requestedMediaType) {
		List<String> result = new ArrayList<String>();
		for (Entry<String, MediaType> entry : mediaTypes.entrySet()) {
			if (requestedMediaType.includes(entry.getValue())) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	private View getBestView(List<View> candidateViews, List<MediaType> requestedMediaTypes) {
		MediaType bestRequestedMediaType = null;
		View bestView = null;
		for (MediaType requestedMediaType : requestedMediaTypes) {
			for (View candidateView : candidateViews) {
				if (StringUtils.hasText(candidateView.getContentType())) {
					MediaType candidateContentType = MediaType.parseMediaType(candidateView.getContentType());
					if (requestedMediaType.includes(candidateContentType)) {
						bestRequestedMediaType = requestedMediaType;
						bestView = candidateView;
						break;
					}
				}
			}
			if (bestView != null) {
				if (logger.isDebugEnabled()) {
					logger.debug(
							"Returning [" + bestView + "] based on requested media type '" + bestRequestedMediaType +
									"'");
				}
				break;
			}
		}
		return bestView;

	}


	/**
	 * Inner class to avoid hard-coded JAF dependency.
	 */
	private static class ActivationMediaTypeFactory {

		private static final FileTypeMap fileTypeMap;

		static {
			fileTypeMap = loadFileTypeMapFromContextSupportModule();
		}

		private static FileTypeMap loadFileTypeMapFromContextSupportModule() {
			// see if we can find the extended mime.types from the context-support module
			Resource mappingLocation = new ClassPathResource("org/springframework/mail/javamail/mime.types");
			if (mappingLocation.exists()) {
				if (logger.isTraceEnabled()) {
					logger.trace("Loading Java Activation Framework FileTypeMap from " + mappingLocation);
				}
				InputStream inputStream = null;
				try {
					inputStream = mappingLocation.getInputStream();
					return new MimetypesFileTypeMap(inputStream);
				}
				catch (IOException ex) {
					// ignore
				}
				finally {
					if (inputStream != null) {
						try {
							inputStream.close();
						}
						catch (IOException ex) {
							// ignore
						}
					}
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Loading default Java Activation Framework FileTypeMap");
			}
			return FileTypeMap.getDefaultFileTypeMap();
		}

		public static MediaType getMediaType(String fileName) {
			String mediaType = fileTypeMap.getContentType(fileName);
			return StringUtils.hasText(mediaType) ? MediaType.parseMediaType(mediaType) : null;
		}
	}


	private static final View NOT_ACCEPTABLE_VIEW = new View() {

		public String getContentType() {
			return null;
		}

		public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
				throws Exception {
			response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
		}
	};

}
