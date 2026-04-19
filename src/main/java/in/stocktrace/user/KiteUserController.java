package in.stocktrace.user;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class KiteUserController {

    private final KiteUserService service;

    public KiteUserController(KiteUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<KiteUserDto.Response> list() {
        return service.listAll().stream().map(KiteUserDto.Response::from).toList();
    }

    @GetMapping("/active")
    public List<KiteUserDto.Response> listActive() {
        return service.listActive().stream().map(KiteUserDto.Response::from).toList();
    }

    @GetMapping("/{userId}")
    public KiteUserDto.Response get(@PathVariable String userId) {
        return KiteUserDto.Response.from(service.getRequired(userId));
    }

    @PostMapping
    public ResponseEntity<KiteUserDto.Response> create(@Valid @RequestBody KiteUserDto.CreateRequest req) {
        return ResponseEntity.status(201).body(KiteUserDto.Response.from(service.create(req)));
    }

    @PatchMapping("/{userId}")
    public KiteUserDto.Response update(@PathVariable String userId, @RequestBody KiteUserDto.UpdateRequest req) {
        return KiteUserDto.Response.from(service.update(userId, req));
    }

    @PostMapping("/{userId}/activate")
    public KiteUserDto.Response activate(@PathVariable String userId) {
        return KiteUserDto.Response.from(service.setActive(userId, true));
    }

    @PostMapping("/{userId}/deactivate")
    public KiteUserDto.Response deactivate(@PathVariable String userId) {
        return KiteUserDto.Response.from(service.setActive(userId, false));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable String userId) {
        service.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
