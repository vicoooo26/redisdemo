package tk.vico.redisdemo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public interface SimpleController {

    @RequestMapping(value = "/invoke", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    void invoke();
}
