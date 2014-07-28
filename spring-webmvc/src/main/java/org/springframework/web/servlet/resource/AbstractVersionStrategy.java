package org.springframework.web.servlet.resource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for {@link VersionStrategy} implementations.
 * Supports versions as:
 * <ul>
 *     <li>prefix in the request path, like "version/static/myresource.js"
 *     <li>file name suffix in the request path, like "static/myresource-version.js"
 * </ul>
 *
 * <p>Note: This base class does <i>not</i> provide support for generating the
 * version string.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 4.1
 */
public abstract class AbstractVersionStrategy implements VersionStrategy {

	protected final Log logger = LogFactory.getLog(getClass());

	private final VersionPathStrategy pathStrategy;


	protected AbstractVersionStrategy(VersionPathStrategy pathStrategy) {
		Assert.notNull(pathStrategy, "'pathStrategy' is required");
		this.pathStrategy = pathStrategy;
	}


	public VersionPathStrategy getVersionPathStrategy() {
		return this.pathStrategy;
	}


	@Override
	public String extractVersion(String requestPath) {
		return this.pathStrategy.extractVersion(requestPath);
	}

	@Override
	public String removeVersion(String requestPath, String version) {
		return this.pathStrategy.removeVersion(requestPath, version);
	}

	@Override
	public String addVersion(String requestPath, String version) {
		return this.pathStrategy.addVersion(requestPath, version);
	}


	/**
	 * A prefix-based {@code VersionPathStrategy},
	 * e.g. {@code "{version}/path/foo.js"}.
	 */
	protected static class PrefixVersionPathStrategy implements VersionPathStrategy {

		private final String prefix;


		public PrefixVersionPathStrategy(String version) {
			Assert.hasText(version, "'version' is required and must not be empty");
			this.prefix = version;
		}

		@Override
		public String extractVersion(String requestPath) {
			return (requestPath.startsWith(this.prefix) ? this.prefix : null);
		}

		@Override
		public String removeVersion(String requestPath, String version) {
			return requestPath.substring(this.prefix.length());
		}

		@Override
		public String addVersion(String path, String version) {
			return (this.prefix.endsWith("/") || path.startsWith("/") ? this.prefix + path : this.prefix + "/" + path);
		}

	}

	/**
	 * File name-based {@code VersionPathStrategy},
	 * e.g. {@code "path/foo-{version}.css"}.
	 */
	protected static class FileNameVersionPathStrategy implements VersionPathStrategy {

		private static final Pattern pattern = Pattern.compile("-(\\S*)\\.");


		@Override
		public String extractVersion(String requestPath) {
			Matcher matcher = pattern.matcher(requestPath);
			if (matcher.find()) {
				String match = matcher.group(1);
				return match.contains("-") ? match.substring(match.lastIndexOf("-") + 1) : match;
			}
			else {
				return null;
			}
		}

		@Override
		public String removeVersion(String requestPath, String version) {
			return StringUtils.delete(requestPath, "-" + version);
		}

		@Override
		public String addVersion(String requestPath, String version) {
			String baseFilename = StringUtils.stripFilenameExtension(requestPath);
			String extension = StringUtils.getFilenameExtension(requestPath);
			return baseFilename + "-" + version + "." + extension;
		}
	}

}
