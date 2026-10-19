package com.ax.core.kcd;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kcd")
@RequiredArgsConstructor
public class KcdController {

    private final KcdRepository kcdRepository;

    public record Response(String code, String name) {
    }

    @GetMapping
    public List<Response> search(@RequestParam(defaultValue = "") String q) {
        List<Kcd> found = q.isBlank() ? kcdRepository.findAll() : kcdRepository.search(q.trim());
        return found.stream().limit(50).map(k -> new Response(k.getCode(), k.getName())).toList();
    }
}
