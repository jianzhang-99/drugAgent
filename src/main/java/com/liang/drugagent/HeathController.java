package com.liang.drugagent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Liang
 **/
@RestController
@RequestMapping("/health")
public class HeathController {

    @GetMapping
    public String healthCheck() {
        return "ok";
    }
}
