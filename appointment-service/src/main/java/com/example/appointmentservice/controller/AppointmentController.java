package com.example.appointmentservice.controller;

import com.example.appointmentservice.dto.AppointmentRequest;
import com.example.appointmentservice.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/appointments", "/api/v1/appointments"})
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    @PostMapping
    public Object createAppointment(@RequestBody(required = false) AppointmentRequest request) {
        return appointmentService.createAppointment(request);
    }
}
