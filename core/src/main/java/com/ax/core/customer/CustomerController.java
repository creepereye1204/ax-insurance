package com.ax.core.customer;

import com.ax.core.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;

    public record CreateRequest(@NotBlank String name, @NotNull @Past LocalDate birthDate, String phone) {
    }

    public record Response(Long id, String name, LocalDate birthDate, String phone) {
        static Response from(Customer c) {
            return new Response(c.getId(), c.getName(), c.getBirthDate(), c.getPhone());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest req) {
        return Response.from(customerRepository.save(new Customer(req.name(), req.birthDate(), req.phone())));
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return customerRepository.findById(id)
                .map(Response::from)
                .orElseThrow(() -> new NotFoundException("고객", id));
    }
}
