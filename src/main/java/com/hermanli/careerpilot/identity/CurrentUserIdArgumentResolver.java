package com.hermanli.careerpilot.identity;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && (parameter.getParameterType() == long.class
                || parameter.getParameterType() == Long.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object userId = webRequest.getAttribute(
                AuthenticationInterceptor.USER_ID_ATTRIBUTE,
                NativeWebRequest.SCOPE_REQUEST
        );
        if (userId instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        throw new AuthenticationRequiredException();
    }
}
