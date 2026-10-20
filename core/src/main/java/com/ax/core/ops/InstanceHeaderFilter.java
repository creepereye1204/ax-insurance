package com.ax.core.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 로드밸런싱 확인용: 어떤 인스턴스가 응답했는지 헤더로 알려준다. */
@Component
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private final String instance;

    public InstanceHeaderFilter(@Value("${ax.instance}") String instance) {
        this.instance = instance;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Ax-Instance", instance);
        chain.doFilter(request, response);
    }
}
