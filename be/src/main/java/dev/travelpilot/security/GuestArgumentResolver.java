package dev.travelpilot.security;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injects the verified {@link Guest}; a request without a guest token gets 401 UNAUTHORIZED. */
public class GuestArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Guest.class;
    }

    @Override
    public Guest resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return GuestAuthInterceptor.current(request).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
