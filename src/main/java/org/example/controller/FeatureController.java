package org.example.controller;

import org.example.model.FeatureResponse;
import org.example.service.FeatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeatureController {

    private static final Logger logger = LoggerFactory.getLogger(FeatureController.class);


    @Autowired
    FeatureService featureService;


    @GetMapping("/vertx/prelimcheck")
    FeatureResponse triggerPrelimCheck(){
        FeatureResponse featureResponse ;

        featureResponse  = featureService.prelimCheck();
        return featureResponse;

    }


}
