package com.unistudents.api.controllers;

import com.unistudents.api.components.LoginForm;
import com.unistudents.api.services.ELearningServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/elearning")
@EnableCaching
public class ELearningServiceController {
    @Autowired
    private ELearningServiceService elearning;

    @RequestMapping(value = {"/{university}"}, method = RequestMethod.POST)
    public ResponseEntity<Object> getStudent(
            @PathVariable("university") String university,
            @RequestBody LoginForm loginForm) {
        return elearning.get(university, loginForm);
    }

}
