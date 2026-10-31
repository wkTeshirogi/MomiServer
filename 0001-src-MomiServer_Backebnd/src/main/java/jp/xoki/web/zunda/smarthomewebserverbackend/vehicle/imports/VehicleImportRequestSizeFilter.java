package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleImportRequestSizeFilterConstants.MULTIPART_OVERHEAD_BYTES;

/** multipart解析より前に車検証取込要求の通信量を制限する。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VehicleImportRequestSizeFilter extends OncePerRequestFilter {

	private final VehicleImportProperties properties;

	public VehicleImportRequestSizeFilter(VehicleImportProperties properties) { this.properties = properties; }

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long contentLength = request.getContentLengthLong();
		if (contentLength < 0 && request.getHeader("Content-Length") != null) {
			try { contentLength = Long.parseLong(request.getHeader("Content-Length")); }
			catch (NumberFormatException _) { contentLength = -1; }
		}
		if (contentLength < 0) {
			response.sendError(HttpStatus.LENGTH_REQUIRED.value(), "Content-Length is required for vehicle imports.");
			return;
		}
		if (contentLength > properties.getMaxBytes() + MULTIPART_OVERHEAD_BYTES) {
			response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Vehicle import request is too large.");
			return;
		}
		filterChain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !HttpMethod.POST.matches(request.getMethod()) || !"/api/vehicle-imports".equals(request.getRequestURI());
	}
}
