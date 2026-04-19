package in.stocktrace.kiteapi;

import com.zerodhatech.models.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kite/{userId}")
public class ProfileController {

    private final KiteApiHelper kite;

    public ProfileController(KiteApiHelper kite) {
        this.kite = kite;
    }

    @GetMapping("/profile")
    public Profile profile(@PathVariable String userId) {
        return kite.call(userId, k -> k.getProfile());
    }
}
