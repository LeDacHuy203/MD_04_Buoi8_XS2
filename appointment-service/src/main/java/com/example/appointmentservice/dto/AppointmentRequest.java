package com.example.appointmentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentRequest {
    private String patientName;
    private Long doctorId;
    private String reason;
}
