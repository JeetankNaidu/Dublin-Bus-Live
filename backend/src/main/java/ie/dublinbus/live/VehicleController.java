package ie.dublinbus.live;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.allowed-origins}")
public class VehicleController {

    private final VehiclePositionService service;

    public VehicleController(VehiclePositionService service) {
        this.service = service;
    }

    @GetMapping("/vehicles")
    public VehicleSnapshot vehicles() {
        return service.current();
    }
}
