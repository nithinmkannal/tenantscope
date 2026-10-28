package com.triagelens.web;

import com.triagelens.domain.*;
import com.triagelens.service.IncidentService;
import com.triagelens.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentView create(@Valid @RequestBody CreateIncidentRequest request) {
        return service.create(request);
    }

    @GetMapping
    public Page<IncidentView> list(@RequestParam(required = false) IncidentState state,
                                   @RequestParam(required = false) Priority priority,
                                   @RequestParam(required = false) ServiceArea serviceArea,
                                   @RequestParam(required = false) String tenant,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.search(state, priority, serviceArea, tenant, pr);
    }

    @GetMapping("/{number}")
    public IncidentDetailView get(@PathVariable String number) {
        return service.get(number);
    }

    @PostMapping("/{number}/transition")
    public IncidentView transition(@PathVariable String number, @Valid @RequestBody TransitionRequest request) {
        return service.transition(number, request);
    }

    @PostMapping("/{number}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteView addNote(@PathVariable String number, @Valid @RequestBody NoteRequest request) {
        return service.addNote(number, request);
    }
}
